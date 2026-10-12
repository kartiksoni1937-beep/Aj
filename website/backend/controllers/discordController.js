const database = require('../utils/database');
const User = require('../models/User');
const { getAccessToken, getUserInfo, addUserToGuild, addRoleToMember, getClientId } = require('../utils/discord');
const { DISCORD, FRONTEND_URL } = require('../config/constants');
const sessionToken = require('../utils/sessionToken');
const { storeAuthCode, exchangeAuthCode } = require('../utils/authCodeStore');
const crypto = require('crypto');
const argon2 = require('argon2');

// Initialize Discord client ID on startup
let discordClientId = DISCORD.CLIENT_ID;

// Auto-fetch client ID if not set
async function initializeDiscordClientId() {
    if (!discordClientId && DISCORD.BOT_TOKEN) {
        try {
            const id = await getClientId();
            if (id) {
                discordClientId = id;
                console.log('[DISCORD] Auto-fetched Client ID:', id);
            }
        } catch (error) {
            console.error('[DISCORD] Failed to auto-fetch Client ID:', error.message);
        }
    }
}

// Initialize on module load
initializeDiscordClientId();

// Get Discord OAuth URL
exports.getDiscordAuthUrl = async (req, res) => {
    try {
        // Auto-fetch client ID if not set
        if (!discordClientId || discordClientId === '') {
            discordClientId = await getClientId();
            if (!discordClientId) {
                console.error('[DISCORD] Failed to fetch client ID from bot token');
                return res.status(500).json({
                    success: false,
                    message: 'Discord client ID not available. Please check bot token.'
                });
            }
            console.log('[DISCORD] Client ID fetched:', discordClientId);
        }

        const token = req.headers.authorization?.replace('Bearer ', '');
        
        if (!token) {
            return res.status(401).json({
                success: false,
                message: 'No token provided'
            });
        }

        const clientIP = sessionToken.normalizeIP(req.ip || req.connection.remoteAddress);
        const requestHWID = req.headers['x-hwid'] || req.body?.hwid || '';
        const decoded = sessionToken.verifySessionToken(token, clientIP, requestHWID);
        if (!decoded) {
            return res.status(401).json({
                success: false,
                message: 'Invalid or expired token'
            });
        }

        const state = Buffer.from(JSON.stringify({ userId: decoded.uid, timestamp: Date.now() })).toString('base64');
        const redirectUri = DISCORD.REDIRECT_URI;
        
        const scopes = ['identify', 'guilds.join'];
        const authUrl = `https://discord.com/api/oauth2/authorize?client_id=${discordClientId}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=${scopes.join('%20')}&state=${state}`;

        res.json({
            success: true,
            authUrl: authUrl
        });
    } catch (error) {
        console.error('[DISCORD] Get auth URL error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error'
        });
    }
};

// Handle Discord OAuth callback (handles both login and linking)
exports.discordCallback = async (req, res) => {
    try {
        const { code, state } = req.query;

        // Use frontend URL from config

        if (!code) {
            return res.redirect(`${FRONTEND_URL}/auth?error=no_code&message=Discord authorization code missing`);
        }

        if (!state) {
            return res.redirect(`${FRONTEND_URL}/auth?error=no_state&message=Invalid state parameter`);
        }

        // Decode state to determine if it's login or linking
        let stateData;
        try {
            stateData = JSON.parse(Buffer.from(state, 'base64').toString('utf8'));
            
            // Verify state hasn't expired (5 minutes)
            if (Date.now() - stateData.timestamp > 5 * 60 * 1000) {
                return res.redirect(`${FRONTEND_URL}/auth?error=expired_state&message=Authorization request expired`);
            }
        } catch (e) {
            return res.redirect(`${FRONTEND_URL}/auth?error=invalid_state&message=Invalid state parameter`);
        }

        // Ensure client ID is available
        if (!discordClientId) {
            discordClientId = await getClientId();
            if (!discordClientId) {
                return res.redirect(`${FRONTEND_URL}/auth?error=client_id_missing&message=Discord client ID not available`);
            }
        }

        // Get access token from Discord
        let tokenData;
        try {
            tokenData = await getAccessToken(code, DISCORD.REDIRECT_URI, discordClientId);
        } catch (error) {
            // Handle specific Discord OAuth errors
            if (error.response?.data?.error === 'invalid_grant') {
                console.error('[DISCORD] Invalid grant - code may have expired or been used already');
                return res.redirect(`${FRONTEND_URL}/auth?error=expired_code&message=Discord authorization code expired. Please try logging in again.`);
            }
            throw error; // Re-throw other errors
        }
        const accessToken = tokenData.access_token;

        // Get Discord user info
        const discordUser = await getUserInfo(accessToken);

        // Check if this is a login flow or linking flow
        if (stateData.type === 'login') {
            // Handle login flow
            return await handleDiscordLogin(req, res, discordUser, accessToken);
        } else {
            // Handle linking flow (existing account linking)
            return await handleDiscordLinking(req, res, discordUser, accessToken, stateData.userId);
        }
    } catch (error) {
        console.error('[DISCORD] Callback error:', error);
        const errorMessage = error.response?.data?.error_description || error.message || 'Unknown error';
        res.redirect(`${FRONTEND_URL}/auth?error=discord_callback_failed&message=${encodeURIComponent(errorMessage)}`);
    }
};

// Handle Discord login flow
async function handleDiscordLogin(req, res, discordUser, accessToken) {
    try {
        // Check if user already exists with this Discord ID
        let user = await User.findOne({ discord_id: discordUser.id });

        if (!user) {
            // Create new user account
            // Generate a random password (user won't need it for Discord login)
            const randomPassword = crypto.randomBytes(32).toString('hex');
            const hashedPassword = await argon2.hash(randomPassword);

            // Create username from Discord username (sanitize it)
            let username = discordUser.username.toLowerCase().replace(/[^a-z0-9_]/g, '_');
            // Ensure username is unique
            let baseUsername = username;
            let counter = 1;
            while (await User.findOne({ username })) {
                username = `${baseUsername}${counter}`;
                counter++;
            }

            user = new User({
                username: username,
                password: hashedPassword,
                discord_id: discordUser.id,
                discord_username: `${discordUser.username}${discordUser.discriminator !== '0' ? `#${discordUser.discriminator}` : ''}`
            });

            await user.save();
            console.log(`[DISCORD] Created new user account for Discord user ${discordUser.username}`);
        } else {
            // Update Discord username if it changed
            const newDiscordUsername = `${discordUser.username}${discordUser.discriminator !== '0' ? `#${discordUser.discriminator}` : ''}`;
            if (user.discord_username !== newDiscordUsername) {
                user.discord_username = newDiscordUsername;
                await user.save();
            }
        }

        // Check if user is banned
        if (user.banned) {
            return res.redirect(`${FRONTEND_URL}/auth?error=banned&message=Account banned: ${user.ban_reason || 'No reason provided'}`);
        }

        // Add user to Discord server (guild)
        try {
            await addUserToGuild(discordUser.id, accessToken);
            console.log(`[DISCORD] Added user ${discordUser.username} (${discordUser.id}) to guild ${DISCORD.GUILD_ID}`);
        } catch (error) {
            // Log but continue - user might already be in guild
            console.warn('[DISCORD] Failed to add user to guild (may already be member):', error.message);
        }

        const clientIP = sessionToken.normalizeIP(req.ip || req.connection.remoteAddress);
        const issuedToken = sessionToken.generateSessionToken(user._id.toString(), clientIP, '');

        // Record successful login
        await database.recordSuccessfulLogin(user.id, req.ip || req.connection.remoteAddress);

        console.log(`[DISCORD] Successfully logged in Discord user ${discordUser.username} as ${user.username}`);

        // Store token with authorization code instead of putting it in URL
        const authCode = storeAuthCode(issuedToken, 5 * 60 * 1000); // 5 minutes expiry
        
        // Redirect to frontend with authorization code
        res.redirect(`${FRONTEND_URL}/auth?discord_login=true&code=${encodeURIComponent(authCode)}`);
    } catch (error) {
        console.error('[DISCORD] Login callback error:', error);
        const errorMessage = error.response?.data?.error_description || error.message || 'Unknown error';
        res.redirect(`${FRONTEND_URL}/auth?error=discord_login_failed&message=${encodeURIComponent(errorMessage)}`);
    }
}

// Handle Discord linking flow
async function handleDiscordLinking(req, res, discordUser, accessToken, userId) {
    try {
        // Add user to Discord server (guild)
        try {
            await addUserToGuild(discordUser.id, accessToken);
            console.log(`[DISCORD] Added user ${discordUser.username} (${discordUser.id}) to guild ${DISCORD.GUILD_ID}`);
        } catch (error) {
            // Log but continue - user might already be in guild
            console.warn('[DISCORD] Failed to add user to guild (may already be member):', error.message);
        }

        // Update user in database
        const updatedUser = await User.findByIdAndUpdate(
            userId,
            {
                discord_id: discordUser.id,
                discord_username: `${discordUser.username}${discordUser.discriminator !== '0' ? `#${discordUser.discriminator}` : ''}`
            },
            { new: true }
        );

        if (!updatedUser) {
            return res.redirect(`${FRONTEND_URL}/?error=user_not_found&message=User account not found`);
        }

        // Grant customer role to the user only if they have an active plan
        if (DISCORD.CUSTOMER_ROLE_ID) {
            const planLower = updatedUser.plan ? String(updatedUser.plan).trim().toLowerCase() : '';
            const isLifetime = planLower === 'lifetime';
            const hasActivePlan = updatedUser.plan && 
                                  updatedUser.plan !== 'N/A' && 
                                  updatedUser.plan !== 'free' &&
                                  (isLifetime || !updatedUser.expiration_date || new Date(updatedUser.expiration_date) > new Date());
            
            if (hasActivePlan) {
                try {
                    await addRoleToMember(discordUser.id, DISCORD.CUSTOMER_ROLE_ID);
                    console.log(`[DISCORD] Granted customer role to user ${discordUser.username} (${discordUser.id})`);
                } catch (error) {
                    // Log but continue, role assignment failure shouldn't block the linking process
                    console.warn('[DISCORD] Failed to grant customer role (non-fatal):', error.message);
                }
            } else {
                console.log(`[DISCORD] User ${discordUser.username} (${discordUser.id}) does not have an active plan, skipping customer role assignment`);
            }
        }

        console.log(`[DISCORD] Successfully linked Discord account ${discordUser.username} to user ${updatedUser.username}`);

        // Redirect to frontend dashboard with success
        res.redirect(`${FRONTEND_URL}/dashboard?discord_linked=true&username=${encodeURIComponent(discordUser.username)}`);
    } catch (error) {
        console.error('[DISCORD] Linking callback error:', error);
        const errorMessage = error.response?.data?.error_description || error.message || 'Unknown error';
        res.redirect(`${FRONTEND_URL}/?error=discord_linking_failed&message=${encodeURIComponent(errorMessage)}`);
    }
}

// Get Discord link status
exports.getDiscordStatus = async (req, res) => {
    try {
        const token = req.headers.authorization?.replace('Bearer ', '');

        if (!token) {
            return res.status(401).json({
                success: false,
                message: 'No token provided'
            });
        }

        const clientIP = sessionToken.normalizeIP(req.ip || req.connection.remoteAddress);
        const requestHWID = req.headers['x-hwid'] || req.body?.hwid || '';
        const decoded = sessionToken.verifySessionToken(token, clientIP, requestHWID);
        if (!decoded) {
            return res.status(401).json({
                success: false,
                message: 'Invalid or expired token'
            });
        }

        const user = await database.findUserById(decoded.uid);
        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        res.json({
            success: true,
            linked: !!user.discord_id,
            discord_id: user.discord_id || null,
            discord_username: user.discord_username || null
        });
    } catch (error) {
        console.error('[DISCORD] Get status error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error'
        });
    }
};

// Unlink Discord account
exports.unlinkDiscord = async (req, res) => {
    try {
        const token = req.headers.authorization?.replace('Bearer ', '');

        if (!token) {
            return res.status(401).json({
                success: false,
                message: 'No token provided'
            });
        }

        const clientIP = sessionToken.normalizeIP(req.ip || req.connection.remoteAddress);
        const requestHWID = req.headers['x-hwid'] || req.body?.hwid || '';
        const decoded = sessionToken.verifySessionToken(token, clientIP, requestHWID);
        if (!decoded) {
            return res.status(401).json({
                success: false,
                message: 'Invalid or expired token'
            });
        }

        const user = await database.findUserById(decoded.uid);
        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        if (!user.discord_id) {
            return res.status(400).json({
                success: false,
                message: 'Discord account is not linked'
            });
        }

        // Unlink Discord account
        await User.findByIdAndUpdate(decoded.uid, {
            discord_id: null,
            discord_username: null
        });

        console.log(`[DISCORD] Unlinked Discord account from user ${user.username}`);

        res.json({
            success: true,
            message: 'Discord account unlinked successfully'
        });
    } catch (error) {
        console.error('[DISCORD] Unlink error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error'
        });
    }
};

// Get Discord login URL (no auth required)
exports.getDiscordLoginUrl = async (req, res) => {
    try {
        // Auto-fetch client ID if not set
        if (!discordClientId || discordClientId === '') {
            discordClientId = await getClientId();
            if (!discordClientId) {
                console.error('[DISCORD] Failed to fetch client ID from bot token');
                return res.status(500).json({
                    success: false,
                    message: 'Discord client ID not available. Please check bot token.'
                });
            }
            console.log('[DISCORD] Client ID fetched:', discordClientId);
        }

        // Create state for login flow (different from linking flow)
        const state = Buffer.from(JSON.stringify({ 
            type: 'login', 
            timestamp: Date.now() 
        })).toString('base64');
        
        // Use the same redirect URI as linking
        const redirectUri = DISCORD.REDIRECT_URI;
        const scopes = ['identify', 'guilds.join'];
        const authUrl = `https://discord.com/api/oauth2/authorize?client_id=${discordClientId}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=${scopes.join('%20')}&state=${state}`;

        res.json({
            success: true,
            authUrl: authUrl
        });
    } catch (error) {
        console.error('[DISCORD] Get login URL error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error'
        });
    }
};

// Exchange authorization code for session token
exports.exchangeAuthCode = async (req, res) => {
    try {
        const { code } = req.body;
        
        if (!code) {
            return res.status(400).json({
                success: false,
                message: 'Authorization code is required'
            });
        }
        
        const token = exchangeAuthCode(code);
        
        if (!token) {
            return res.status(401).json({
                success: false,
                message: 'Invalid or expired authorization code'
            });
        }
        
        res.json({
            success: true,
            sessionToken: token
        });
    } catch (error) {
        console.error('[DISCORD] Exchange auth code error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error'
        });
    }
};

// Handle Discord login callback (deprecated - now handled by discordCallback)
// Keeping for backwards compatibility but redirects to main callback
exports.discordLoginCallback = exports.discordCallback;

