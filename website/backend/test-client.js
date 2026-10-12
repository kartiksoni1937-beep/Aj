const WebSocket = require('ws');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { WS_PORT } = require('./config/constants');

// Load Public Key
const PUBLIC_KEY_PATH = path.join(__dirname, 'keys/public.pem');
let PUBLIC_KEY;
try {
    PUBLIC_KEY = fs.readFileSync(PUBLIC_KEY_PATH, 'utf8');
} catch (error) {
    console.error(`[ERROR] Failed to load public key from ${PUBLIC_KEY_PATH}`);
    console.error('Make sure the RSA public key file exists in the keys/ directory');
    process.exit(1);
}

let ws;
let heartbeatInterval;
let sessionKey; // AES Key

function generateAESKey() {
    return crypto.randomBytes(32);
}

function encryptRSA(data) {
    return crypto.publicEncrypt(
        {
            key: PUBLIC_KEY,
            padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
            oaepHash: 'sha1',
        },
        data
    ).toString('base64');
}

function encryptAES(data, key) {
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
    
    const jsonStr = JSON.stringify(data);
    let encrypted = cipher.update(jsonStr, 'utf8');
    const final = cipher.final();
    const authTag = cipher.getAuthTag();
    
    const combined = Buffer.concat([encrypted, final, authTag]);
    
    return {
        iv: iv.toString('base64'),
        d: combined.toString('base64')
    };
}

function decryptAES(encryptedDataBase64, key, ivBase64) {
    const iv = Buffer.from(ivBase64, 'base64');
    const encryptedBuffer = Buffer.from(encryptedDataBase64, 'base64');
    
    const authTagLength = 16;
    const authTag = encryptedBuffer.subarray(encryptedBuffer.length - authTagLength);
    const ciphertext = encryptedBuffer.subarray(0, encryptedBuffer.length - authTagLength);

    const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(authTag);
    
    let decrypted = decipher.update(ciphertext, null, 'utf8');
    decrypted += decipher.final('utf8');
    
    return JSON.parse(decrypted);
}

function connect() {
    ws = new WebSocket(`ws://localhost:${WS_PORT}`);
    console.log(`[CLIENT] Connecting to ws://localhost:${WS_PORT}...`);
    
    // Connection timeout
    const connectionTimeout = setTimeout(() => {
        if (ws.readyState === WebSocket.CONNECTING) {
            console.error('[CLIENT] Connection timeout - server may not be running');
            ws.terminate();
            process.exit(1);
        }
    }, 5000);
    
    ws.on('open', () => {
        clearTimeout(connectionTimeout);
    });

    ws.on('open', () => {
        console.log('Connected to server');

        // 1. Generate AES Key
        sessionKey = generateAESKey();
        console.log('Generated AES Key:', sessionKey.toString('hex'));

        // 2. Prepare Login Payload
        const loginPayload = {
            type: 'login',
            username: 'dev',
            password: 'dev',
            hwid: '2d4b90f4a8db822005656f1c1064fe1cf52720fdf258e061986a6e94d3dea31e'
        };

        // 3. Encrypt Payload with AES
        const encryptedPayload = encryptAES(loginPayload, sessionKey);

        // 4. Encrypt AES Key with RSA
        const encryptedKey = encryptRSA(sessionKey);

        // 5. Send Handshake Packet
        const handshakePacket = {
            k: encryptedKey,
            iv: encryptedPayload.iv,
            d: encryptedPayload.d
        };

        console.log('Sending Handshake...');
        ws.send(JSON.stringify(handshakePacket));
    });

    ws.on('message', (data) => {
        let rawMessage;
        try {
            rawMessage = JSON.parse(data.toString());
        } catch (e) {
            console.error('Failed to parse message:', e.message);
            return;
        }
        
        // Decrypt if encrypted
        let message = rawMessage;
        if (rawMessage.iv && rawMessage.d) {
            try {
                message = decryptAES(rawMessage.d, sessionKey, rawMessage.iv);
                console.log('Received Encrypted Message:', message);
            } catch (e) {
                console.error('Failed to decrypt message:', e.message);
                return;
            }
        } else {
            console.log('Received Plaintext Message:', rawMessage);
        }

        if (message.type === 'login_response') {
            if (message.success) {
                console.log('Login successful!');
                
                // Start heartbeat (encrypted)
                heartbeatInterval = setInterval(() => {
                    if (ws.readyState === WebSocket.OPEN) {
                        const heartbeat = { type: 'heartbeat' }; // Server doesn't handle this yet but good for testing encryption
                        const encrypted = encryptAES(heartbeat, sessionKey);
                        // ws.send(JSON.stringify(encrypted)); 
                        // Server doesn't have a heartbeat handler in switch, so let's send something valid or just skip
                    }
                }, 30000);
            } else {
                console.log('Login failed:', message.message);
                ws.close();
            }
        }
    });

    ws.on('close', () => {
        console.log('Disconnected');
        if (heartbeatInterval) clearInterval(heartbeatInterval);
    });

    ws.on('error', (error) => {
        console.error('WebSocket Error:', error.message || error);
        if (error.code) {
            console.error('Error Code:', error.code);
        }
    });
}

connect();
