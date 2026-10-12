const fs = require('fs');
const path = require('path');

// Load config.json
const configPath = path.join(__dirname, '..', 'config.json');
let config = {};

try {
    const configFile = fs.readFileSync(configPath, 'utf8');
    config = JSON.parse(configFile);
} catch (error) {
    console.warn('[CONFIG] Failed to load config.json, using defaults:', error.message);
    // Fallback to defaults if config.json doesn't exist
    config = {
        httpPort: 3001,
        wsPort: 6767,
        mongodbUri: 'mongodb://localhost:27017/echo-client',
        jwtSecret: 'default-secret-change-this',
        rateLimit: {
            maxAttempts: 6,
            windowMs: 300000,
            lockoutMs: 900000,
            cleanupIntervalMs: 60000
        },
        session: {
            heartbeatInterval: 30000,
            sessionTimeout: 180000
        },
        security: {
            enableHwidBinding: true,
            logFailedAttempts: true
        }
    };
}

module.exports = {
    WS_PORT: config.wsPort || 6767,
    HTTP_PORT: config.httpPort || 3001,

    FRONTEND_URL: config.frontendUrl || 'http://localhost:3000',
    BACKEND_URL: config.backendUrl || 'http://localhost:3001',

    MONGODB_URI: config.mongodbUri || 'mongodb://localhost:27017/echo-client',

    RATE_LIMIT: {
        MAX_ATTEMPTS: config.rateLimit?.maxAttempts || 6,
        WINDOW_MS: config.rateLimit?.windowMs || 300000,
        LOCKOUT_MS: config.rateLimit?.lockoutMs || 900000,
        CLEANUP_INTERVAL_MS: config.rateLimit?.cleanupIntervalMs || 60000,
    },

    SESSION: {
        HEARTBEAT_INTERVAL: config.session?.heartbeatInterval || 30000,
        SESSION_TIMEOUT: config.session?.sessionTimeout || 180000,
        TOKEN_EXPIRY: config.session?.tokenExpiry || 1800000, // 30 minutes in milliseconds
    },

    SECURITY: {
        ENABLE_HWID_BINDING: config.security?.enableHwidBinding !== false,
        LOG_FAILED_ATTEMPTS: config.security?.logFailedAttempts !== false,
    },

    SECRET: {
        JWT_SECRET: config.jwtSecret || 'default-secret-change-this'
    },

    DISCORD: {
        CLIENT_ID: config.discord?.clientId || '',
        CLIENT_SECRET: config.discord?.clientSecret || '',
        BOT_TOKEN: config.discord?.botToken || '',
        GUILD_ID: config.discord?.guildId || '',
        LOG_CHANNEL_ID: config.discord?.logChannelId || '1448579594218373283',
        REDIRECT_URI: config.discord?.redirectUri || (config.backendUrl || 'http://localhost:3001') + '/api/auth/discord/callback',
        RELEASE_CHANNEL_ID: config.discord?.releaseChannelId || '1426552303455965297',
        CUSTOMER_ROLE_ID: config.discord?.customerRoleId || '1448738963245236375'
    },

    STRIPE: {
        SECRET_KEY: config.stripe?.secretKey || '',
        PUBLISHABLE_KEY: config.stripe?.publishableKey || '',
        WEBHOOK_SECRET: config.stripe?.webhookSecret || '',
        PRICES: {
            LIFETIME: {
                AMOUNT: config.stripe?.prices?.lifetime?.amount || 3500,
                CURRENCY: config.stripe?.prices?.lifetime?.currency || 'eur',
                PRICE_ID: config.stripe?.prices?.lifetime?.priceId || ''
            },
            MONTHLY: {
                AMOUNT: config.stripe?.prices?.monthly?.amount || 670,
                CURRENCY: config.stripe?.prices?.monthly?.currency || 'eur',
                PRICE_ID: config.stripe?.prices?.monthly?.priceId || ''
            }
        }
    },

    OXAPAY: {
        MERCHANT_API_KEY: config.oxapay?.merchantApiKey || '',
        PRICES: {
            LIFETIME: {
                AMOUNT: config.oxapay?.prices?.lifetime?.amount || 35.00,
                CURRENCY: config.oxapay?.prices?.lifetime?.currency || 'EUR'
            },
            MONTHLY: {
                AMOUNT: config.oxapay?.prices?.monthly?.amount || 6.70,
                CURRENCY: config.oxapay?.prices?.monthly?.currency || 'EUR'
            }
        },
        SANDBOX: config.oxapay?.sandbox || false
    },

    TURNSTILE: {
        SECRET: config.turnstile?.secret || ''
    }
};
