const crypto = require('crypto');
const { SECRET, SESSION } = require('../config/constants');

function normalizeIP(ip) {
    if (!ip) return '';
    if (ip.startsWith('::ffff:')) {
        const parts = ip.split(':');
        return parts[parts.length - 1];
    }
    if (ip === '::1') {
        return '127.0.0.1';
    }
    return ip;
}

function fingerprint(value) {
    return crypto.createHash('sha256').update(value || '').digest('hex');
}

function generateSessionToken(uid, clientIP, hwid) {
    const timestamp = Date.now();
    const random = crypto.randomBytes(16).toString('hex');
    const hwidHash = fingerprint(hwid || '');
    const ipHash = fingerprint(clientIP || '');

    const data = `${uid}.${timestamp}.${random}.${hwidHash}.${ipHash}`;
    const signature = crypto.createHmac('sha256', SECRET.JWT_SECRET).update(data).digest('hex');
    return Buffer.from(`${data}.${signature}`).toString('base64');
}

function verifySessionToken(token, clientIP, hwid, options = {}) {
    try {
        const decoded = Buffer.from(token, 'base64').toString('utf8');
        const parts = decoded.split('.');
        
        if (parts.length !== 6) {
            return null;
        }
        
        const [uid, timestamp, random, hwidHash, ipHash, signature] = parts;
        const data = `${uid}.${timestamp}.${random}.${hwidHash}.${ipHash}`;
        const expectedSignature = crypto.createHmac('sha256', SECRET.JWT_SECRET).update(data).digest('hex');
        
        if (signature !== expectedSignature) return null;
        
        // Verify IP binding
        const expectedIPHash = fingerprint(clientIP || '');
        const skipIP = options.skipIPCheck || false;
        if (!skipIP && ipHash !== expectedIPHash) {
            return null; // IP mismatch
        }

        const tokenAge = Date.now() - parseInt(timestamp, 10);
        if (tokenAge > SESSION.TOKEN_EXPIRY) return null;

        const skipHwid = options.skipHwidCheck || (hwidHash === fingerprint('') && (!hwid || hwid === ''));
        if (!skipHwid && hwidHash !== fingerprint(hwid || '')) return null;

        return { uid, userId: uid, timestamp: parseInt(timestamp, 10) };
    } catch (error) {
        return null;
    }
}

module.exports = {
    generateSessionToken,
    verifySessionToken,
    normalizeIP,
    fingerprint,
};

