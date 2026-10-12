const argon2 = require('argon2');
const axios = require('axios');
const database = require('../utils/database');
const rateLimiter = require('../middleware/rateLimit');
const { SECURITY, TURNSTILE } = require('../config/constants');
const sessionToken = require('../utils/sessionToken');
const User = require('../models/User');

// Register new user
exports.register = async (req, res) => {
    try {
        const { username, password, turnstileToken } = req.body;

        if (!username || !password) {
            return res.status(400).json({
                success: false,
                message: 'Username and password are required'
            });
        }

        // Verify Turnstile token
        if (!turnstileToken) {
            return res.status(400).json({
                success: false,
                message: 'Verification required. Please complete the security check.'
            });
        }

        const TURNSTILE_SECRET = TURNSTILE.SECRET;
        
        if (!TURNSTILE_SECRET) {
            console.warn('[AUTH] TURNSTILE_SECRET not set in config.json');
            return res.status(500).json({
                success: false,
                message: 'Verification service configuration error'
            });
        }
        
        try {
            const turnstileResponse = await axios.post(
                'https://challenges.cloudflare.com/turnstile/v0/siteverify',
                new URLSearchParams({
                    secret: TURNSTILE_SECRET,
                    response: turnstileToken,
                }),
                {
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                    },
                }
            );

            if (!turnstileResponse.data.success) {
                return res.status(400).json({
                    success: false,
                    message: 'Verification failed. Please try again.'
                });
            }
        } catch (turnstileError) {
            console.error('[AUTH] Turnstile verification error:', turnstileError);
            return res.status(500).json({
                success: false,
                message: 'Verification service error. Please try again later.'
            });
        }

        // Check if user already exists
        const existingUser = await database.findUserByUsername(username);
        if (existingUser) {
            return res.status(409).json({
                success: false,
                message: 'Username already exists'
            });
        }

        // Hash password
        const hashedPassword = await argon2.hash(password);

        // Create new user
        const user = new User({
            username,
            password: hashedPassword
        });

        await user.save();

        res.status(201).json({
            success: true,
            message: 'Account created successfully',
            user: {
                id: user._id.toString(),
                username: user.username
            }
        });
    } catch (error) {
        console.error('[AUTH] Registration error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error during registration'
        });
    }
};

// Login user
exports.login = async (req, res) => {
    try {
        const { username, password, hwid } = req.body;
        const clientIP = sessionToken.normalizeIP(req.ip || req.connection.remoteAddress);

        if (!username || !password) {
            return res.status(400).json({
                success: false,
                message: 'Username and password are required'
            });
        }

        // Check rate limiting
        const rateLimitCheck = rateLimiter.check(clientIP, hwid);
        if (rateLimitCheck.limited) {
            return res.status(429).json({
                success: false,
                message: rateLimitCheck.message,
                rateLimited: true,
                remainingTime: rateLimitCheck.remainingTime
            });
        }

        // Find user by username
        const user = await database.findUserByUsername(username);
        if (!user) {
            rateLimiter.recordFailedAttempt(clientIP, hwid);
            return res.status(401).json({
                success: false,
                message: 'Invalid username or password'
            });
        }

        // Verify password
        const match = await argon2.verify(user.password, password);
        if (!match) {
            rateLimiter.recordFailedAttempt(clientIP, hwid);
            await database.recordFailedLoginAttempt(user.id, clientIP);
            return res.status(401).json({
                success: false,
                message: 'Invalid username or password'
            });
        }

        // Check if user is banned
        if (user.banned) {
            rateLimiter.recordFailedAttempt(clientIP, hwid);
            await database.recordFailedLoginAttempt(user.id, clientIP);
            return res.status(403).json({
                success: false,
                message: `Account banned: ${user.ban_reason || 'No reason provided'}`
            });
        }

        // Check HWID binding if enabled
        if (SECURITY.ENABLE_HWID_BINDING && hwid) {
            if (!user.hwid) {
                await database.updateUserHWID(user.id, hwid);
                console.log(`[AUTH] HWID bound for user: ${username}`);
            } else if (user.hwid !== hwid) {
            rateLimiter.recordFailedAttempt(clientIP, hwid);
            await database.recordFailedLoginAttempt(user.id, clientIP);
                return res.status(403).json({
                    success: false,
                    message: 'Invalid HWID'
                });
            }
        }

        // Clear rate limit on success
        rateLimiter.clear(clientIP, hwid);
        await database.recordSuccessfulLogin(user.id, clientIP);

        // Generate session token
        const issuedToken = sessionToken.generateSessionToken(user.id, clientIP, hwid);

        res.json({
            success: true,
            message: 'Logged in successfully',
            user: {
                id: user.id,
                username: user.username
            },
            sessionToken: issuedToken,
            timestamp: Date.now()
        });
    } catch (error) {
        console.error('[AUTH] Login error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error during authentication'
        });
    }
};

// Logout user (mainly for clearing client-side tokens)
exports.logout = async (req, res) => {
    // Since we're using stateless tokens, logout is mainly client-side
    // But we can log it for analytics
    res.json({
        success: true,
        message: 'Logged out successfully'
    });
};

// Get current user info
exports.getCurrentUser = async (req, res) => {
    try {
        const token = req.headers.authorization?.replace('Bearer ', '');

        if (!token) {
            return res.status(401).json({
                success: false,
                message: 'No token provided'
            });
        }

        const clientIP = sessionToken.normalizeIP(req.ip || req.connection.remoteAddress);
        const requestHWID = req.headers['x-hwid'] || req.body?.hwid || req.query?.hwid || '';
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
            user: {
                id: user.id,
                username: user.username,
                hwid: user.hwid || null,
                userNumber: user.userNumber || null,
                plan: user.plan || 'N/A',
                expiration_date: user.expiration_date || null,
                admin: user.admin || false,
                banned: user.banned || false,
                ban_reason: user.ban_reason || null,
                discord_id: user.discord_id || null,
                discord_username: user.discord_username || null,
                last_hwid_reset: user.last_hwid_reset || null,
                last_login: user.last_login || null,
                createdAt: user.createdAt || null,
                stripe_customer_id: user.stripe_customer_id || null,
                stripe_subscription_id: user.stripe_subscription_id || null
            }
        });
    } catch (error) {
        console.error('[AUTH] Get current user error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error'
        });
    }
};

// Reset HWID (once per month)
exports.resetHWID = async (req, res) => {
    try {
        const token = req.headers.authorization?.replace('Bearer ', '');

        if (!token) {
            return res.status(401).json({
                success: false,
                message: 'No token provided'
            });
        }

        const clientIP = sessionToken.normalizeIP(req.ip || req.connection.remoteAddress);
        const requestHWID = req.headers['x-hwid'] || req.body?.hwid || req.query?.hwid || '';
        const decoded = sessionToken.verifySessionToken(token, clientIP, requestHWID);
        if (!decoded) {
            return res.status(401).json({
                success: false,
                message: 'Invalid or expired token'
            });
        }

        const result = await database.resetUserHWID(decoded.uid);
        
        if (result.success) {
            res.json({
                success: true,
                message: result.message
            });
        } else {
            res.status(400).json({
                success: false,
                message: result.message,
                daysRemaining: result.daysRemaining
            });
        }
    } catch (error) {
        console.error('[AUTH] Reset HWID error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error'
        });
    }
};

// Reset password
exports.resetPassword = async (req, res) => {
    try {
        const { username, newPassword, userId } = req.body;
        const token = req.headers.authorization?.replace('Bearer ', '');

        // Validate input
        if (!newPassword) {
            return res.status(400).json({
                success: false,
                message: 'New password is required'
            });
        }

        if (newPassword.length < 6) {
            return res.status(400).json({
                success: false,
                message: 'Password must be at least 6 characters long'
            });
        }

        let targetUserId = null;

        // If userId is provided, this is an admin action, requires authentication and admin privileges
        if (userId) {
            // Require authentication token for admin actions
            if (!token) {
                return res.status(401).json({
                    success: false,
                    message: 'Authentication required for admin actions'
                });
            }
            
            // Verify token and validate session
            const clientIP = sessionToken.normalizeIP(req.ip || req.connection.remoteAddress);
            const requestHWID = req.headers['x-hwid'] || req.body?.hwid || req.query?.hwid || '';
            const decoded = sessionToken.verifySessionToken(token, clientIP, requestHWID);
            
            if (!decoded) {
                return res.status(401).json({
                    success: false,
                    message: 'Invalid or expired token'
                });
            }
            
            // Verify account status
            const adminUser = await User.findById(decoded.uid);
            if (!adminUser) {
                return res.status(401).json({
                    success: false,
                    message: 'User account not found'
                });
            }
            
            // Check if user is banned
            if (adminUser.banned) {
                return res.status(403).json({
                    success: false,
                    message: `Account banned: ${adminUser.ban_reason || 'No reason provided'}`
                });
            }
            
            // Check if user has admin privileges
            if (!adminUser.admin) {
                return res.status(403).json({
                    success: false,
                    message: 'Admin privileges required'
                });
            }
            
            // Verify target user exists
            const targetUser = await User.findById(userId);
            if (!targetUser) {
                return res.status(404).json({
                    success: false,
                    message: 'User not found'
                });
            }
            
            targetUserId = userId;
        } else if (token) {
            // Authenticated user resetting their own password
            const clientIP = sessionToken.normalizeIP(req.ip || req.connection.remoteAddress);
            const requestHWID = req.headers['x-hwid'] || req.body?.hwid || req.query?.hwid || '';
            const decoded = sessionToken.verifySessionToken(token, clientIP, requestHWID);
            
            if (!decoded) {
                return res.status(401).json({
                    success: false,
                    message: 'Invalid or expired token'
                });
            }
            
            // Verify user account still exists and is valid
            const user = await User.findById(decoded.uid);
            if (!user) {
                return res.status(401).json({
                    success: false,
                    message: 'User account not found'
                });
            }
            
            // Check if user is banned
            if (user.banned) {
                return res.status(403).json({
                    success: false,
                    message: `Account banned: ${user.ban_reason || 'No reason provided'}`
                });
            }
            
            targetUserId = decoded.uid;
        } else {
            return res.status(400).json({
                success: false,
                message: 'Authentication token is required'
            });
        }

        // Hash new password
        const hashedPassword = await argon2.hash(newPassword);

        // Update password
        await User.findByIdAndUpdate(targetUserId, {
            password: hashedPassword
        });

        res.json({
            success: true,
            message: 'Password has been reset successfully'
        });
    } catch (error) {
        console.error('[AUTH] Reset password error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error'
        });
    }
};


