const argon2 = require('argon2');
const crypto = require('crypto');
const rateLimiter = require('../middleware/rateLimit');
const database = require('../utils/database');
const { SECURITY } = require('../config/constants');
const sessionToken = require('../utils/sessionToken');
const discordLogger = require('../utils/discordLogger');

class AuthHandler {
    constructor(connectedClients) {
        this.connectedClients = connectedClients;
    }

    generateSessionToken(uid, clientIP, hwid) {
        const normalizedIP = sessionToken.normalizeIP(clientIP);
        return sessionToken.generateSessionToken(uid, normalizedIP, hwid || '');
    }

    verifySessionToken(token, clientIP, hwid) {
        const normalizedIP = sessionToken.normalizeIP(clientIP);
        return sessionToken.verifySessionToken(token, normalizedIP, hwid || '');
    }

    async handleLogin(ws, message, clientIP) {
        const { username, password, hwid, token } = message;
        const normalizedIP = sessionToken.normalizeIP(clientIP);
        const source = message.source || 'websocket';
        const method = token ? 'session_token' : 'credentials';

        // Token-based login (Reconnection)
        if (token) {
            const decoded = this.verifySessionToken(token, normalizedIP, hwid);
            if (decoded) {
                try {
                    const user = await database.findUserById(decoded.uid);
                    if (user) {
                        // Check if user is banned
                        if (user.banned) {
                            this.logAuthEvent('failure', {
                                username: user.username,
                                uid: user.id,
                                ip: normalizedIP,
                                hwid,
                                method,
                                source,
                                reason: `Account banned: ${user.ban_reason || 'No reason provided'}`,
                                discordId: user.discord_id,
                                discordUsername: user.discord_username
                            });
                            this.sendLoginResponse(ws, false, `Account banned: ${user.ban_reason || 'No reason provided'}`);
                            return;
                        }
                        
                        const licenseCheck = this.validateLicense(user);
                        if (!licenseCheck.valid) {
                            this.logAuthEvent('failure', {
                                username: user.username,
                                uid: user.id,
                                ip: normalizedIP,
                                hwid,
                                method,
                                source,
                                reason: `License invalid: ${licenseCheck.message}`,
                                discordId: user.discord_id,
                                discordUsername: user.discord_username
                            });
                            this.sendLoginResponse(ws, false, `License invalid: ${licenseCheck.message}`);
                            return;
                        }
                        // Restore session
                        this.setupSession(ws, user, normalizedIP, hwid);
                        this.logAuthEvent('success', {
                            username: user.username,
                            uid: user.id,
                            ip: normalizedIP,
                            hwid,
                            method,
                            source,
                            discordId: user.discord_id,
                            discordUsername: user.discord_username
                        });
                        return;
                    }
                } catch (error) {
                    console.error(`[AUTH] Error restoring session for user ${decoded.uid}:`, error);
                }
            }
            // If token invalid or user not found, fall through to normal login or fail
            if (!username || !password) {
                this.logAuthEvent('failure', {
                    username,
                    ip: normalizedIP,
                    hwid,
                    method,
                    source,
                    reason: 'Invalid or expired session token'
                });
                this.sendLoginResponse(ws, false, 'Invalid or expired session token');
                return;
            }
        }

        if (!username || !password) {
            this.logAuthEvent('failure', {
                username,
                ip: normalizedIP,
                hwid,
                method,
                source,
                reason: 'Missing credentials'
            });
            this.sendLoginResponse(ws, false, 'Missing credentials');
            return;
        }

        const rateLimitCheck = rateLimiter.check(normalizedIP, hwid);
        if (rateLimitCheck.limited) {
            ws.send(JSON.stringify({
                type: 'login_response',
                success: false,
                message: rateLimitCheck.message,
                rateLimited: true,
                remainingTime: rateLimitCheck.remainingTime,
            }));
            console.warn(`[AUTH] Rate limited login attempt from IP: ${normalizedIP}, reason: ${rateLimitCheck.reason}`)
            this.logAuthEvent('failure', {
                username,
                ip: normalizedIP,
                hwid,
                method,
                source,
                reason: `Rate limited: ${rateLimitCheck.reason || 'Too many attempts'}`
            });
            return;
        }

        try {
            const user = await database.findUserByUsername(username);
            if (!user) {
                rateLimiter.recordFailedAttempt(normalizedIP, hwid);
                this.sendLoginResponse(ws, false, 'Invalid username or password');
                this.logAuthEvent('failure', {
                    username,
                    ip: normalizedIP,
                    hwid,
                    method,
                    source,
                    reason: 'Invalid username or password'
                });
                return;
            }

            const match = await argon2.verify(user.password, password);
            if (!match) {
                rateLimiter.recordFailedAttempt(normalizedIP, hwid);
                await database.recordFailedLoginAttempt(user.id, normalizedIP);
                this.sendLoginResponse(ws, false, 'Invalid username or password');
                this.logAuthEvent('failure', {
                    username: user.username,
                    uid: user.id,
                    ip: normalizedIP,
                    hwid,
                    method,
                    source,
                    reason: 'Invalid password',
                    discordId: user.discord_id,
                    discordUsername: user.discord_username
                });
                return;
            }

            // Check if user is banned
            if (user.banned) {
                rateLimiter.recordFailedAttempt(normalizedIP, hwid);
                await database.recordFailedLoginAttempt(user.id, normalizedIP);
                this.sendLoginResponse(ws, false, `Account banned: ${user.ban_reason || 'No reason provided'}`);
                this.logAuthEvent('failure', {
                    username: user.username,
                    uid: user.id,
                    ip: normalizedIP,
                    hwid,
                    method,
                    source,
                    reason: `Account banned: ${user.ban_reason || 'No reason provided'}`,
                    discordId: user.discord_id,
                    discordUsername: user.discord_username
                });
                return;
            }

            // Check license validity
            const licenseCheck = this.validateLicense(user);
            if (!licenseCheck.valid) {
                rateLimiter.recordFailedAttempt(clientIP, hwid);
                await database.recordFailedLoginAttempt(user.id, clientIP);
                this.sendLoginResponse(ws, false, `License invalid: ${licenseCheck.message}`);
                console.warn(`[AUTH] License invalid for user: ${username} from IP: ${clientIP} - ${licenseCheck.message}`);
                this.logAuthEvent('failure', {
                    username: user.username,
                    uid: user.id,
                    ip: normalizedIP,
                    hwid,
                    method,
                    source,
                    reason: `License invalid: ${licenseCheck.message}`,
                    discordId: user.discord_id,
                    discordUsername: user.discord_username
                });
                return;
            }

            if (SECURITY.ENABLE_HWID_BINDING) {
                if (!user.hwid && hwid) {
                    await database.updateUserHWID(user.id, hwid);
                    console.log(`[AUTH] HWID bound for user: ${username}`);
                } else if (user.hwid && hwid && user.hwid !== hwid) {
                    rateLimiter.recordFailedAttempt(normalizedIP, hwid);
                    await database.recordFailedLoginAttempt(user.id, normalizedIP);
                    this.sendLoginResponse(ws, false, 'Invalid HWID');
                    console.warn(`[SECURITY] HWID mismatch for user: ${username} from IP: ${normalizedIP}`);
                    this.logAuthEvent('failure', {
                        username: user.username,
                        uid: user.id,
                        ip: normalizedIP,
                        hwid,
                        method,
                        source,
                        reason: 'Invalid HWID',
                        discordId: user.discord_id,
                        discordUsername: user.discord_username
                    });
                    return;
                }
            }

            rateLimiter.clear(normalizedIP, hwid);
            await database.recordSuccessfulLogin(user.id, normalizedIP);

            this.setupSession(ws, user, normalizedIP, hwid);
            this.logAuthEvent('success', {
                username: user.username,
                uid: user.id,
                ip: normalizedIP,
                hwid,
                method,
                source,
                discordId: user.discord_id,
                discordUsername: user.discord_username
            });

        } catch (error) {
            console.error(`[AUTH] Error handling login for user ${username}:`, error);
            this.sendLoginResponse(ws, false, 'Internal server error during authentication');
        }
    }

    // License checking logic
    validateLicense(user) {
        if (!user.plan || user.plan === 'N/A') {
            return { valid: false, message: 'No valid plan found. Please purchase a valid license to continue.' };
        }

        if (user.license_expiry && Date.now() > new Date(user.license_expiry).getTime()) {
            return { valid: false, message: 'License has expired. Please renew your license to continue.' };
        }
        return { valid: true, message: 'License valid' };
    }

    setupSession(ws, user, clientIP, hwid) {
        ws.uid = user.id;
        ws.username = user.username;
        ws.clientIP = sessionToken.normalizeIP(clientIP);
        ws.hwid = hwid || '';
        ws.sessionToken = this.generateSessionToken(user.id, ws.clientIP, ws.hwid);
        ws.lastHeartbeat = Date.now(); // Initialize keepalive
        const serverInteger = crypto.randomInt(0, 0x7fffffff);
        const secretLocalKey = crypto.randomInt(0, 0x7fffffff);
        const runtimeConstant = (serverInteger ^ secretLocalKey) - (-21332);
        
        this.connectedClients.set(ws.uid, ws);

        ws.send(JSON.stringify({
            type: 'login_response',
            success: true,
            message: 'Logged in successfully',
            uid: user.id,
            username: user.username,
            sessionToken: ws.sessionToken,
            timestamp: Date.now(),
            serverInteger,
            secretLocalKey,
            runtimeConstant,
        }));

        console.log(`[AUTH] User ${user.username} (${user.id}) authenticated from IP: ${ws.clientIP}`);
    }

    handleTokenRotation(ws, message) {
        if (!ws.uid || !ws.sessionToken) {
            ws.send(JSON.stringify({ type: 'error', message: 'Not authenticated' }));
            return;
        }

        if (message.token !== ws.sessionToken) {
            console.warn(`[AUTH] Token mismatch for user ${ws.username}. Possible replay or sync issue.`);
            this.logAuthEvent('failure', {
                username: ws.username,
                uid: ws.uid,
                ip: ws.clientIP,
                hwid: ws.hwid,
                method: 'token_rotation',
                source: 'websocket',
                reason: 'Session token mismatch on rotation'
            });
            // We could disconnect here, but let's just fail the rotation for now
            ws.send(JSON.stringify({ type: 'error', message: 'Invalid token' }));
            return;
        }

        // Generate new token
        const newToken = this.generateSessionToken(ws.uid, ws.clientIP, ws.hwid);
        ws.sessionToken = newToken;
        ws.lastHeartbeat = Date.now(); // Treat rotation as heartbeat

        ws.send(JSON.stringify({
            type: 'rotate_token_response',
            success: true,
            token: newToken,
            timestamp: Date.now()
        }));

        this.logAuthEvent('success', {
            username: ws.username,
            uid: ws.uid,
            ip: ws.clientIP,
            hwid: ws.hwid,
            method: 'token_rotation',
            source: 'websocket',
            reason: 'Token rotated'
        });
    }

    sendLoginResponse(ws, success, message) {
        ws.send(JSON.stringify({
            type: 'login_response',
            success: success,
            message: message,
        }));
    }

    kickUser(uid, reason = 'Kicked by administrator') {
        const ws = this.connectedClients.get(uid);
        const WebSocket = require('ws');

        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({
                type: 'kick',
                message: reason,
            }));
            ws.close();
            this.connectedClients.delete(uid);
            console.log(`[AUTH] User ${uid} (${ws.username}) kicked: ${reason}`);

            this.logAuthEvent('failure', {
                username: ws.username,
                uid,
                ip: ws.clientIP,
                hwid: ws.hwid,
                method: 'kick',
                source: 'websocket',
                reason
            });
        } else {
            console.warn(`[AUTH] Failed to kick user ${uid}: not connected or already closed`);
        }
    }

    broadcastMessage(message) {
        const WebSocket = require('ws');
        let sentCount = 0;

        this.connectedClients.forEach((ws, uid) => {
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({
                    type: 'broadcast',
                    message: message,
                }));
                sentCount++;
            }
        });

        console.log(`[AUTH] Broadcasted message to ${sentCount} clients`);
        return sentCount;
    }

    getConnectedUsers() {
        return Array.from(this.connectedClients.values()).map(ws => ({
            uid: ws.uid,
            username: ws.username,
        }));
    }

    disconnectAll(reason = 'Server shutdown') {
        const WebSocket = require('ws');

        this.connectedClients.forEach((ws, uid) => {
            if (ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({
                    type: 'server_shutdown',
                    message: reason,
                }));
                ws.close();
                this.logAuthEvent('failure', {
                    username: ws.username,
                    uid,
                    ip: ws.clientIP,
                    hwid: ws.hwid,
                    method: 'disconnect',
                    source: 'websocket',
                    reason
                });
            }
        });

        console.log(`[SERVER] Disconnected ${this.connectedClients.size} users`);
        this.connectedClients.clear();
    }

    async logAuthEvent(status, payload) {
        try {
            const logId = await discordLogger.logAuthEvent({ status, ...payload });
            return logId || null;
        } catch {
            return null;
        }
    }
}

module.exports = AuthHandler;