console.clear();
const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const { WS_PORT, HTTP_PORT, DISCORD } = require('./config/constants');
const database = require('./utils/database');
const AuthHandler = require('./handlers/authHandler');
const rateLimiter = require('./middleware/rateLimit');
const discordLogger = require('./utils/discordLogger');
const EncryptionUtil = require('./utils/encryption');
const routes = require('./routes');
const DiscordBot = require('./bot/index');
const featureInfoController = require('./controllers/featureInfoController');
const { startPlanExpirationScheduler, stopPlanExpirationScheduler } = require('./utils/planExpiration');

const connectedClients = new Map();
let discordBot = null;
let planExpirationInterval = null;

const authHandler = new AuthHandler(connectedClients);

// Extract real client IP when behind Cloudflare/proxies
function getClientIP(req) {
    const cfIp = req.headers['cf-connecting-ip'];
    const xff = req.headers['x-forwarded-for'];

    if (cfIp) return cfIp.split(',')[0].trim();
    if (xff) return xff.split(',')[0].trim();
    return req.socket.remoteAddress;
}

// Create Express app and HTTP server
const app = express();
const httpServer = http.createServer(app);

// Trust proxy for accurate IP addresses
app.set('trust proxy', true);

// CORS middleware (adjust as needed for production)
app.use((req, res, next) => {
    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept, Authorization');
    if (req.method === 'OPTIONS') {
        return res.sendStatus(200);
    }
    next();
});

// Stripe webhook needs raw body - handle it before JSON parsing
app.use('/api/payments/webhook', express.raw({ type: 'application/json' }));

// OxaPay webhook needs raw body for proper HMAC validation
app.use('/api/payments/oxapay/webhook', express.raw({ type: 'application/json' }));

// Middleware for other routes
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Mount routes
app.use('/api', routes);

//init server
async function initializeServer() {
    try {
        await database.initialize();

        // Start HTTP server
        httpServer.listen(HTTP_PORT, async () => {
            console.log(`[SERVER] HTTP server running on port ${HTTP_PORT}`);
            
            // Start Discord bot
            if (DISCORD.BOT_TOKEN) {
                try {
                    discordBot = new DiscordBot();
                    await discordBot.start();
                    discordLogger.setClient(discordBot.client);
                } catch (error) {
                    console.error('[BOT] Failed to start Discord bot:', error);
                }
            } else {
                console.warn('[BOT] Discord bot token not configured, skipping bot startup');
            }

            // Start plan expiration scheduler (runs every hour)
            planExpirationInterval = startPlanExpirationScheduler(60 * 60 * 1000);
        });

        // Start WebSocket server on separate port
        //const wss = new WebSocket.Server({ port: WS_PORT });
        console.log(`[SERVER] WebSocket server running on port ${WS_PORT}`);

        //setupWebSocketServer(wss);
        setupGracefulShutdown(httpServer/*, wss*/);
    } catch (error) {
        console.error(`[SERVER] Error initializing server:`, error);
        process.exit(1);
    }
}

/*function setupWebSocketServer(wss) {
    wss.on('connection', (ws, req) => {
        const clientIP = getClientIP(req);
        console.log(`[SERVER] New connection from ${clientIP}`);

        // Override send to auto-encrypt if session key exists
        const originalSend = ws.send.bind(ws);
        ws.send = (data) => {
            if (ws.aesKey && typeof data === 'string') {
                try {
                    // If data is already JSON string, parse it first
                    // If it's just a string message, wrap it
                    let jsonData;
                    try {
                        jsonData = JSON.parse(data);
                    } catch (e) {
                        jsonData = { message: data };
                    }
                    
                    const encrypted = EncryptionUtil.encryptAES(jsonData, ws.aesKey);
                    console.log(`[CRYPTO] Encrypted outgoing message to ${ws.username || clientIP}`);
                    originalSend(JSON.stringify(encrypted));
                } catch (error) {
                    console.error('[SERVER] Failed to encrypt outgoing message:', error);
                    // Fallback to plaintext or close? For now, log error.
                }
            } else {
                originalSend(data);
            }
        };

        ws.on('message', async (data) => {
            try {
                const rawMessage = JSON.parse(data.toString());
                let decryptedMessage = rawMessage;

                // Check for Secure Envelope
                if (rawMessage.iv && rawMessage.d) {
                    // 1. Handle Handshake (First packet)
                    if (rawMessage.k) {
                        try {
                            const aesKey = EncryptionUtil.decryptRSA(rawMessage.k);
                            ws.aesKey = aesKey;
                            console.log(`[SERVER] Handshake successful for ${clientIP}`);
                            console.log(`[CRYPTO] Decrypted RSA handshake from ${clientIP}`);
                        } catch (error) {
                            console.error(`[SERVER] Handshake failed for ${clientIP}:`, error.message);
                            authHandler.logAuthEvent('failure', {
                                username: ws.username,
                                uid: ws.uid,
                                ip: ws.clientIP || clientIP,
                                hwid: ws.hwid,
                                method: 'handshake',
                                source: 'websocket',
                                reason: `Handshake failed: ${error.message}`
                            });
                            ws.close();
                            return;
                        }
                    }

                    // 2. Decrypt Payload
                    if (ws.aesKey) {
                        try {
                            decryptedMessage = EncryptionUtil.decryptAES(rawMessage.d, ws.aesKey, rawMessage.iv);
                            console.log(`[CRYPTO] Decrypted AES payload from ${ws.username || clientIP}`);
                        } catch (error) {
                            console.error(`[SERVER] Decryption failed for ${clientIP}:`, error.message);
                            authHandler.logAuthEvent('failure', {
                                username: ws.username,
                                uid: ws.uid,
                                ip: ws.clientIP || clientIP,
                                hwid: ws.hwid,
                                method: 'decrypt',
                                source: 'websocket',
                                reason: `AES decrypt failed: ${error.message}`
                            });
                            ws.close();
                            return;
                        }
                    } else {
                        // Encrypted payload but no key established?
                        console.warn(`[SERVER] Received encrypted payload without established session from ${clientIP}`);
                        authHandler.logAuthEvent('failure', {
                            username: ws.username,
                            uid: ws.uid,
                            ip: ws.clientIP || clientIP,
                            hwid: ws.hwid,
                            method: 'decrypt',
                            source: 'websocket',
                            reason: 'Encrypted payload without session key'
                        });
                        ws.close();
                        return;
                    }
                }

                // Process the decrypted message
                const message = decryptedMessage;

                switch (message.type) {
                    case 'login':
                        await authHandler.handleLogin(ws, message, clientIP);
                        break;
                    
                    case 'rotate_token':
                        await authHandler.handleTokenRotation(ws, message);
                        break;

                    case 'get_features': {
                        // forgot lmao for feature info
                        const decoded = ws.sessionToken
                            ? authHandler.verifySessionToken(
                                ws.sessionToken,
                                ws.clientIP || clientIP,
                                ws.hwid
                            )
                            : null;

                        if (!ws.uid || !decoded || decoded.uid !== ws.uid) {
                            ws.send(JSON.stringify({
                                type: 'error',
                                message: 'Not authenticated'
                            }));
                            authHandler.logAuthEvent('failure', {
                                username: ws.username,
                                uid: ws.uid,
                                ip: ws.clientIP || clientIP,
                                hwid: ws.hwid,
                                method: 'token_validation',
                                source: 'websocket',
                                reason: 'Invalid session during get_features'
                            });
                            ws.close();
                            return;
                        }

                        ws.send(JSON.stringify({
                            type: 'features_response',
                            ...featureInfoController.getFragmentedFeatureInfo()
                        }));
                        break;
                    }
                        
                    default:
                        ws.send(JSON.stringify({
                            type: 'error',
                            message: 'Invalid message type',
                        }));
                        break;
                }
            } catch (error) {
                console.error(`[SERVER] Error processing message:`, error);
                ws.send(JSON.stringify({
                    type: 'error',
                    message: 'Invalid message format',
                }));
            }
        });

        ws.on('close', () => {
            if (ws.uid) {
                connectedClients.delete(ws.uid);
                console.log(`[SERVER] User ${ws.username} (UID: ${ws.uid}) disconnected`);
                authHandler.logAuthEvent('success', {
                    username: ws.username,
                    uid: ws.uid,
                    ip: ws.clientIP || clientIP,
                    hwid: ws.hwid,
                    method: 'logout',
                    source: 'websocket',
                    reason: 'User disconnected'
                });
            } else {
                console.log(`[SERVER] Unauthenticated user disconnected`);
            }
        });
        ws.on('error', (error) => {
            console.error(`[SERVER] WebSocket error:`, error);
            authHandler.logAuthEvent('failure', {
                username: ws.username,
                uid: ws.uid,
                ip: ws.clientIP || clientIP,
                hwid: ws.hwid,
                method: 'ws_error',
                source: 'websocket',
                reason: `WebSocket error: ${error.message}`
            });
        });
    });
}*/

function setupGracefulShutdown(httpServer/*, wss*/) {
    process.on('SIGINT', async () => {
        console.log('\n[SERVER] Shutting down server...');

        authHandler.disconnectAll('Server shutting down');

        // Close WebSocket server
        //wss.close(async () => {
        //    console.log('[SERVER] WebSocket server closed');
        //});

        // Close HTTP server
        httpServer.close(async () => {
            console.log('[SERVER] HTTP server closed');
            
            // Stop Discord bot
            if (discordBot) {
                await discordBot.stop();
            }

            // Stop plan expiration scheduler
            if (planExpirationInterval) {
                stopPlanExpirationScheduler(planExpirationInterval);
            }
            
            rateLimiter.stop();
            
            await database.close();
            
            console.log('[SERVER] Shutdown complete');
            process.exit(0);
        });
    });
}

// Export API for external use (e.g., admin panel, testing)
module.exports = {
    // Auth functions
    kickUser: (uid, reason) => authHandler.kickUser(uid, reason),
    broadcastMessage: (message) => authHandler.broadcastMessage(message),
    getConnectedUsers: () => authHandler.getConnectedUsers(),
    
    // Rate limiter functions
    rateLimiter: {
        getStats: () => rateLimiter.getStats(),
        clearAll: () => rateLimiter.clearAll(),
        clearIP: (ip) => rateLimiter.clearIP(ip),
        clearHWID: (hwid) => rateLimiter.clearHWID(hwid)
    },
    
    // Database functions
    database: {
        findUserByUsername: (username) => database.findUserByUsername(username),
        findUserById: (id) => database.findUserById(id)
    }
};

// Start the server
if (require.main === module) {
    initializeServer();
}
