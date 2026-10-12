const mongoose = require('mongoose');
const { MONGODB_URI } = require('../config/constants');
const User = require('../models/User');

class Database {
    constructor() {
        this.connection = null;
    }

    async initialize() {
        try {
            if (this.connection && mongoose.connection.readyState === 1) {
                console.log('Database already connected');
                return true;
            }

            await mongoose.connect(MONGODB_URI);

            this.connection = mongoose.connection;
            console.log('Database connected');
            return true;
        } catch (error) {
            console.error('Database connection failed:', error);
            throw error;
        }
    }

    async close() {
        if (this.connection && mongoose.connection.readyState === 1) {
            await mongoose.connection.close();
            console.log('Database connection closed');
        }
    }

    async findUserByUsername(username) {
        try {
            const user = await User.findOne({ username }).lean();
            if (user && user._id) {
                user.id = user._id.toString();
                // Auto-migrate: Add missing fields if they don't exist
                await this.migrateUserFields(user._id.toString(), user);
            }
            return user;
        } catch (error) {
            console.error('Error finding user by username:', error);
            throw error;
        }
    }


    async findUserById(id) {
        try {
            const user = await User.findById(id).lean();
            if (user && user._id) {
                user.id = user._id.toString();
                // Auto-migrate: Add missing fields if they don't exist
                await this.migrateUserFields(id, user);
            }
            return user;
        } catch (error) {
            console.error('Error finding user by id:', error);
            throw error;
        }
    }

    async migrateUserFields(userId, userData) {
        try {
            const updates = {};
            
            // Add plan if missing
            if (userData.plan === undefined || userData.plan === null) {
                updates.plan = 'N/A';
            }
            
            // Add expiration_date if missing (keep null for N/A users)
            if (userData.expiration_date === undefined) {
                updates.expiration_date = null;
            }
            
            // Add userNumber if missing (will be set by pre-save hook on next save)
            if (userData.userNumber === undefined || userData.userNumber === null) {
                const lastUser = await User.findOne({}, {}, { sort: { userNumber: -1 } });
                updates.userNumber = lastUser && lastUser.userNumber ? lastUser.userNumber + 1 : 1;
            }
            
            // Add last_hwid_reset if missing (renamed from last_hwid_change)
            if (userData.last_hwid_reset === undefined && userData.last_hwid_change !== undefined) {
                updates.last_hwid_reset = userData.last_hwid_change;
            } else if (userData.last_hwid_reset === undefined) {
                updates.last_hwid_reset = null;
            }
            
            // Add admin field if missing
            if (userData.admin === undefined) {
                updates.admin = false;
            }
            
            // Add Discord fields if missing
            if (userData.discord_id === undefined) {
                updates.discord_id = null;
            }
            if (userData.discord_username === undefined) {
                updates.discord_username = null;
            }
            
            // Add ban fields if missing
            if (userData.banned === undefined) {
                updates.banned = false;
            }
            if (userData.ban_reason === undefined) {
                updates.ban_reason = null;
            }
            
            // Only update if there are fields to add
            if (Object.keys(updates).length > 0) {
                await User.findByIdAndUpdate(userId, { $set: updates }, { new: false });
                console.log(`[MIGRATION] Updated user ${userId} with missing fields:`, Object.keys(updates));
            }
        } catch (error) {
            console.error('Error migrating user fields:', error);
            // Don't throw - migration failures shouldn't break the app
        }
    }

    async updateUserHWID(userID, hwid) {
        try {
            // Check if this is the first time setting HWID
            const user = await User.findById(userID).lean();
            const isFirstTime = !user || !user.hwid;
            
            // Only set last_hwid_reset if this is NOT the first time (i.e., user already had an HWID)
            // This allows first HWID registration to be reset immediately without 30-day limit
            const updateData = { hwid };
            if (!isFirstTime) {
                updateData.last_hwid_reset = new Date();
            }
            
            const result = await User.findByIdAndUpdate(
                userID,
                updateData,
                { new: true }
            );
            return result !== null;
        } catch (error) {
            console.error('Error updating user HWID:', error);
            throw error;
        }
    }

    async resetUserHWID(userID) {
        try {
            const user = await User.findById(userID).lean();
            if (!user) {
                return { success: false, message: 'User not found' };
            }

            // Check if last reset was less than 30 days ago
            if (user.last_hwid_reset) {
                const daysSinceReset = (Date.now() - new Date(user.last_hwid_reset).getTime()) / (1000 * 60 * 60 * 24);
                if (daysSinceReset < 30) {
                    const daysRemaining = Math.ceil(30 - daysSinceReset);
                    return { 
                        success: false, 
                        message: `HWID can only be reset once per month. Please wait ${daysRemaining} more day(s).`,
                        daysRemaining 
                    };
                }
            }

            // Reset HWID
            const result = await User.findByIdAndUpdate(
                userID,
                { hwid: null, last_hwid_reset: new Date() },
                { new: true }
            );

            if (result) {
                return { success: true, message: 'HWID reset successfully' };
            }
            return { success: false, message: 'Failed to reset HWID' };
        } catch (error) {
            console.error('Error resetting user HWID:', error);
            throw error;
        }
    }

    async recordSuccessfulLogin(userID, ipAddress) {
        try {
            const now = new Date();
            const result = await User.findByIdAndUpdate(
                userID,
                {
                    last_login: now,
                    last_login_ip: ipAddress,
                    last_attempt: now,
                    last_attempt_ip: ipAddress
                },
                { new: true }
            );
            return result !== null;
        } catch (error) {
            console.error('Error recording successful login:', error);
            throw error;
        }
    }

    async recordFailedLoginAttempt(userID, ipAddress) {
        try {
            const now = new Date();
            const result = await User.findByIdAndUpdate(
                userID,
                {
                    last_attempt: now,
                    last_attempt_ip: ipAddress,
                    last_failed_attempt: now
                },
                { new: true }
            );
            return result !== null;
        } catch (error) {
            console.error('Error recording failed login attempt:', error);
            throw error;
        }
    }
}

module.exports = new Database();
