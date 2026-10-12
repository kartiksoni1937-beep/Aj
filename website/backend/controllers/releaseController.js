const Release = require('../models/Release');
const User = require('../models/User');
const sessionToken = require('../utils/sessionToken');
const discordLogger = require('../utils/discordLogger');
const path = require('path');
const fs = require('fs');

// Get all releases (public, no auth required)
async function getAllReleases(req, res) {
    try {
        const releases = await Release.find({ is_active: true })
            .sort({ createdAt: -1 })
            .limit(50)
            .select('version added changed fixed createdAt')
            .lean();

        res.json({
            success: true,
            releases: releases.map(release => ({
                version: release.version,
                added: release.added || [],
                changed: release.changed || [],
                fixed: release.fixed || [],
                date: release.createdAt
            }))
        });
    } catch (error) {
        console.error('[API] Error fetching releases:', error);
        res.status(500).json({ success: false, error: 'Failed to fetch releases' });
    }
}

// Get latest release
async function getLatestRelease(req, res) {
    try {
        const release = await Release.findOne({ is_active: true })
            .sort({ createdAt: -1 })
            .select('version added changed fixed createdAt')
            .lean();

        if (!release) {
            return res.json({
                success: true,
                release: null
            });
        }

        res.json({
            success: true,
            release: {
                version: release.version,
                added: release.added || [],
                changed: release.changed || [],
                fixed: release.fixed || [],
                date: release.createdAt
            }
        });
    } catch (error) {
        console.error('[API] Error fetching latest release:', error);
        res.status(500).json({ success: false, error: 'Failed to fetch latest release' });
    }
}

// Get releases for dashboard (requires auth)
async function getReleases(req, res) {
    try {
        const token = req.headers.authorization?.replace('Bearer ', '');
        if (!token) {
            return res.status(401).json({ success: false, error: 'No token provided' });
        }

        const clientIP = sessionToken.normalizeIP(req.ip || req.connection.remoteAddress);
        const requestHWID = req.headers['x-hwid'] || req.body?.hwid || req.query?.hwid || '';
        const payload = sessionToken.verifySessionToken(token, clientIP, requestHWID, { skipHwidCheck: !requestHWID });
        if (!payload) {
            return res.status(401).json({ success: false, error: 'Invalid or expired token' });
        }

        const user = await User.findById(payload.userId || payload.uid);
        if (!user) {
            return res.status(401).json({ success: false, error: 'User not found' });
        }

        // Check if user is banned
        if (user.banned) {
            return res.status(403).json({ 
                success: false, 
                error: `Account banned: ${user.ban_reason || 'No reason provided'}` 
            });
        }

        const releases = await Release.find({ is_active: true })
            .sort({ createdAt: -1 })
            .limit(20)
            .select('version file_name file_size file_url added changed fixed createdAt')
            .lean();

        // Check if user has an active plan
        const planLower = user.plan ? String(user.plan).trim().toLowerCase() : '';
        const isLifetime = planLower === 'lifetime';
        const userHasActivePlan = !!user.plan &&
            user.plan !== 'N/A' &&
            (isLifetime || !user.expiration_date || new Date(user.expiration_date) > new Date());

        res.json({
            success: true,
            releases: releases.map(release => {
                const releaseData = {
                    version: release.version,
                    file_name: release.file_name,
                    file_size: release.file_size,
                    added: release.added || [],
                    changed: release.changed || [],
                    fixed: release.fixed || [],
                    date: release.createdAt,
                    is_silent: release.is_silent || false
                };
                
                // Only include download_url for users with active plans
                if (userHasActivePlan) {
                    releaseData.download_url = release.file_url;
                }
                
                return releaseData;
            })
        });
    } catch (error) {
        console.error('[API] Error fetching releases:', error);
        res.status(500).json({ success: false, error: 'Failed to fetch releases' });
    }
}

// Download release file (requires auth and plan check)
async function downloadRelease(req, res) {
    try {
        // Only accept token in Authorization header
        const token = req.headers.authorization?.replace('Bearer ', '');
        if (!token) {
            return res.status(401).json({ success: false, error: 'No token provided' });
        }

        // Extract real client IP (check proxy headers first, similar to server.js getClientIP)
        let rawIP = req.headers['cf-connecting-ip'] || req.headers['x-forwarded-for'] || req.ip || req.connection?.remoteAddress || req.socket?.remoteAddress;
        if (rawIP && typeof rawIP === 'string' && rawIP.includes(',')) {
            // X-Forwarded-For can contain multiple IPs, take the first one (client IP)
            rawIP = rawIP.split(',')[0].trim();
        }
        const clientIP = sessionToken.normalizeIP(rawIP);
        const requestHWID = req.headers['x-hwid'] || req.body?.hwid || req.query?.hwid || '';
        const payload = sessionToken.verifySessionToken(token, clientIP, requestHWID, { skipHwidCheck: !requestHWID });
        if (!payload) {
            return res.status(401).json({ success: false, error: 'Invalid or expired token' });
        }

        const user = await User.findById(payload.userId || payload.uid);
        if (!user) {
            return res.status(401).json({ success: false, error: 'User not found' });
        }

        // Check if user is banned
        if (user.banned) {
            return res.status(403).json({ 
                success: false, 
                error: `Account banned: ${user.ban_reason || 'No reason provided'}` 
            });
        }

        // Check if user has a plan (not "N/A")
        if (!user.plan || user.plan === 'N/A') {
            return res.status(403).json({ 
                success: false, 
                error: 'You need an active license to download releases' 
            });
        }

        const planLower = String(user.plan).trim().toLowerCase();
        if (planLower !== 'lifetime' && user.expiration_date && new Date(user.expiration_date) < new Date()) {
            return res.status(403).json({
                success: false,
                error: 'Your license has expired. Please renew to download releases'
            });
        }
        const fileName = decodeURIComponent(req.params.fileName);
        const releasesDir = path.join(__dirname, '../releases');
        const filePath = path.join(releasesDir, fileName);

        // Security check: ensure file is in releases directory
        const normalizedPath = path.normalize(filePath);
        const normalizedDir = path.normalize(releasesDir);
        if (!normalizedPath.startsWith(normalizedDir)) {
            return res.status(403).json({ success: false, error: 'Invalid file path' });
        }

        if (!fs.existsSync(filePath)) {
            return res.status(404).json({ success: false, error: 'File not found' });
        }

        const escapeRegex = (str) => {
            return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        };

        // Get release info for filename - try multiple patterns
        // Only allow active releases
        let release = await Release.findOne({ 
            file_url: `/api/releases/download/${fileName}`,
            is_active: true
        });
        if (!release) {
            // Try finding by filename in case the file_url format is different
            const escapedFileName = escapeRegex(fileName);
            release = await Release.findOne({ 
                $or: [
                    { file_url: { $regex: escapedFileName } },
                    { file_name: fileName }
                ],
                is_active: true
            }).sort({ createdAt: -1 });
        }
        
        // Verify release exists and is active
        if (!release) {
            return res.status(404).json({ 
                success: false, 
                error: 'Release not found or no longer available' 
            });
        }
        
        // Double check is_active
        if (!release.is_active) {
            return res.status(403).json({ 
                success: false, 
                error: 'This release is no longer available' 
            });
        }
        
        const downloadFileName = release.file_name;

        // Get file stats
        const fileStats = fs.statSync(filePath);
        const fileSize = fileStats.size;

        try {
            const logPayload = {
                username: user.username,
                uid: user._id.toString(),
                plan: user.plan || 'N/A',
                expiration_date: user.expiration_date || null,
                file_name: release.file_name,
                version: release.version,
                file_size: release.file_size || fileSize,
                ip: clientIP,
                hwid: requestHWID || null,
                discordId: user.discord_id || null,
                discordUsername: user.discord_username || null
            };
            const logResult = await discordLogger.logDownloadEvent(logPayload);
            if (!logResult) {
                return res.status(500).json({ 
                    success: false, 
                    error: 'Failed to log download event. Download blocked.' 
                });
            }
        } catch (logError) {
            console.error('[API] Error logging download event:', logError);
            return res.status(500).json({ 
                success: false, 
                error: 'Failed to log download event. Download blocked.' 
            });
        }

        // Set proper headers for file download
        res.setHeader('Content-Type', 'application/java-archive');
        res.setHeader('Content-Disposition', `attachment; filename="${downloadFileName}"`);
        res.setHeader('Content-Length', fileSize);
        res.setHeader('Cache-Control', 'no-cache');

        // Stream the file
        const fileStream = fs.createReadStream(filePath);
        fileStream.pipe(res);

        fileStream.on('error', (err) => {
            console.error('[API] Error streaming file:', err);
            if (!res.headersSent) {
                res.status(500).json({ success: false, error: 'Failed to download file' });
            } else {
                res.end();
            }
        });

        res.on('close', () => {
            if (!fileStream.destroyed) {
                fileStream.destroy();
            }
        });
    } catch (error) {
        console.error('[API] Error in download release:', error);
        if (!res.headersSent) {
            res.status(500).json({ success: false, error: 'Failed to download release' });
        }
    }
}

module.exports = {
    getAllReleases,
    getLatestRelease,
    getReleases,
    downloadRelease
};
