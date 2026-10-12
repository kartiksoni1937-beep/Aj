const axios = require('axios');
const { DISCORD } = require('../config/constants');

/**
 * Get Discord client ID from bot token
 */
async function getClientId() {
    try {
        const response = await axios.get('https://discord.com/api/v10/users/@me', {
            headers: {
                'Authorization': `Bot ${DISCORD.BOT_TOKEN}`
            }
        });
        return response.data.id;
    } catch (error) {
        console.error('[DISCORD] Failed to get client ID:', error.message);
        return null;
    }
}

/**
 * Get OAuth2 access token from authorization code
 */
async function getAccessToken(code, redirectUri, clientId) {
    try {
        if (!clientId) {
            // Try to get client ID if not provided
            clientId = await getClientId();
            if (!clientId) {
                throw new Error('Discord client ID is required');
            }
        }

        const params = new URLSearchParams();
        params.append('client_id', clientId);
        params.append('client_secret', DISCORD.CLIENT_SECRET);
        params.append('grant_type', 'authorization_code');
        params.append('code', code);
        params.append('redirect_uri', redirectUri);

        const response = await axios.post('https://discord.com/api/v10/oauth2/token', params, {
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            }
        });

        return response.data;
    } catch (error) {
        console.error('[DISCORD] Failed to get access token:', error.response?.data || error.message);
        throw error;
    }
}

/**
 * Get Discord user info from access token
 */
async function getUserInfo(accessToken) {
    try {
        const response = await axios.get('https://discord.com/api/v10/users/@me', {
            headers: {
                'Authorization': `Bearer ${accessToken}`
            }
        });

        return response.data;
    } catch (error) {
        console.error('[DISCORD] Failed to get user info:', error.response?.data || error.message);
        throw error;
    }
}

/**
 * Add user to Discord server using bot token
 */
async function addUserToGuild(userId, accessToken) {
    try {
        const response = await axios.put(
            `https://discord.com/api/v10/guilds/${DISCORD.GUILD_ID}/members/${userId}`,
            {
                access_token: accessToken
            },
            {
                headers: {
                    'Authorization': `Bot ${DISCORD.BOT_TOKEN}`,
                    'Content-Type': 'application/json'
                }
            }
        );

        return response.data;
    } catch (error) {
        // If user is already in guild, that's fine
        if (error.response?.status === 204) {
            return { success: true, message: 'User already in guild' };
        }
        console.error('[DISCORD] Failed to add user to guild:', error.response?.data || error.message);
        throw error;
    }
}

/**
 * Add role to a Discord guild member
 */
async function addRoleToMember(userId, roleId) {
    try {
        const response = await axios.put(
            `https://discord.com/api/v10/guilds/${DISCORD.GUILD_ID}/members/${userId}/roles/${roleId}`,
            {},
            {
                headers: {
                    'Authorization': `Bot ${DISCORD.BOT_TOKEN}`,
                    'Content-Type': 'application/json'
                }
            }
        );

        return response.data;
    } catch (error) {
        // If role is already assigned or user doesn't exist, log but don't throw
        if (error.response?.status === 204) {
            return { success: true, message: 'Role already assigned or user not in guild' };
        }
        console.error('[DISCORD] Failed to add role to member:', error.response?.data || error.message);
        throw error;
    }
}

/**
 * Remove role from a Discord guild member
 */
async function removeRoleFromMember(userId, roleId) {
    try {
        const response = await axios.delete(
            `https://discord.com/api/v10/guilds/${DISCORD.GUILD_ID}/members/${userId}/roles/${roleId}`,
            {
                headers: {
                    'Authorization': `Bot ${DISCORD.BOT_TOKEN}`
                }
            }
        );

        return response.data;
    } catch (error) {
        // If role is not assigned or user doesn't exist, log but don't throw
        if (error.response?.status === 204) {
            return { success: true, message: 'Role not assigned or user not in guild' };
        }
        console.error('[DISCORD] Failed to remove role from member:', error.response?.data || error.message);
        throw error;
    }
}

module.exports = {
    getClientId,
    getAccessToken,
    getUserInfo,
    addUserToGuild,
    addRoleToMember,
    removeRoleFromMember
};

