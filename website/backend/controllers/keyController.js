const database = require('../utils/database');
const Key = require('../models/Key');
const User = require('../models/User');
const sessionToken = require('../utils/sessionToken');
const { DISCORD } = require('../config/constants');
const { addRoleToMember } = require('../utils/discord');

// Redeem a license key
exports.redeemKey = async (req, res) => {
    try {
        const token = req.headers.authorization?.replace('Bearer ', '');
        const { key } = req.body;

        if (!token) {
            return res.status(401).json({
                success: false,
                message: 'No token provided'
            });
        }

        if (!key) {
            return res.status(400).json({
                success: false,
                message: 'License key is required'
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

        // Find the key
        const licenseKey = await Key.findOne({ 
            key: key.toUpperCase().trim(),
            is_active: true
        });

        if (!licenseKey) {
            return res.status(404).json({
                success: false,
                message: 'Invalid or inactive license key'
            });
        }

        // Check if key is already redeemed
        if (licenseKey.redeemed_by) {
            return res.status(400).json({
                success: false,
                message: 'This license key has already been redeemed'
            });
        }

        // Get user
        const user = await database.findUserById(decoded.uid);
        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        // Calculate expiration date
        const now = new Date();
        let expirationDate = null;

        if (licenseKey.plan === 'lifetime') {
            // Lifetime = 100 years from now
            expirationDate = new Date(now.getFullYear() + 100, now.getMonth(), now.getDate());
        } else if (licenseKey.duration_days) {
            expirationDate = new Date(now);
            expirationDate.setDate(expirationDate.getDate() + licenseKey.duration_days);
        }

        // Update user plan
        await User.findByIdAndUpdate(decoded.uid, {
            plan: licenseKey.plan,
            expiration_date: expirationDate
        });

        // Grant customer role if Discord is linked
        if (user.discord_id && DISCORD.CUSTOMER_ROLE_ID) {
            try {
                await addRoleToMember(user.discord_id, DISCORD.CUSTOMER_ROLE_ID);
                console.log(`[KEY] Granted customer role to Discord user ${user.discord_id} (${user.username})`);
            } catch (error) {
                console.warn(`[KEY] Failed to grant customer role to Discord user ${user.discord_id} (${user.username}):`, error.message);
            }
        }

        // Mark key as redeemed
        await Key.findByIdAndUpdate(licenseKey._id, {
            redeemed_by: decoded.uid,
            redeemed_at: now
        });

        res.json({
            success: true,
            message: `License key redeemed successfully! Plan: ${licenseKey.plan.toUpperCase()}`,
            plan: licenseKey.plan,
            expiration_date: expirationDate
        });
    } catch (error) {
        console.error('[KEY] Redeem error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error during key redemption'
        });
    }
};

