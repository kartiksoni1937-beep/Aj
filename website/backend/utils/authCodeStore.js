const authCodes = new Map();

function generateAuthCode() {
    return require('crypto').randomBytes(32).toString('hex');
}


function storeAuthCode(token, expiresInMs = 5 * 60 * 1000) {
    const code = generateAuthCode();
    const expiresAt = Date.now() + expiresInMs;
    
    authCodes.set(code, {
        token,
        expiresAt
    });
    
    setTimeout(() => {
        if (authCodes.has(code)) {
            authCodes.delete(code);
        }
    }, expiresInMs);
    
    return code;
}

function exchangeAuthCode(code) {
    const entry = authCodes.get(code);
    
    if (!entry) {
        return null; // Code not found
    }
    
    if (Date.now() > entry.expiresAt) {
        authCodes.delete(code);
        return null; // Code expired
    }
    
    // One-time use: delete the code after use
    authCodes.delete(code);
    
    return entry.token;
}

function cleanupExpiredCodes() {
    const now = Date.now();
    for (const [code, entry] of authCodes.entries()) {
        if (now > entry.expiresAt) {
            authCodes.delete(code);
        }
    }
}

setInterval(cleanupExpiredCodes, 5 * 60 * 1000);

module.exports = {
    storeAuthCode,
    exchangeAuthCode
};
