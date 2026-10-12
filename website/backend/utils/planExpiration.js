const User = require('../models/User');
const { removeRoleFromMember } = require('./discord');
const { DISCORD } = require('../config/constants');

/**
 * Check and expire plans that have passed their expiration date
 * This should be run periodically (e.g., every hour) via cron job or setInterval
 */
async function expirePlans() {
    try {
        const now = new Date();
        
        // Find all users with plans that have expired
        // Exclude 'N/A' and 'free' plans, and only check users with expiration dates
        const expiredUsers = await User.find({
            plan: { $nin: ['N/A', 'free'] },
            expiration_date: { $lte: now, $ne: null }
        });

        if (expiredUsers.length === 0) {
            console.log('[PLAN_EXPIRATION] No expired plans found');
            return { expired: 0, users: [] };
        }

        // Update expired plans to 'N/A'
        const userIds = expiredUsers.map(user => user._id);
        const result = await User.updateMany(
            { _id: { $in: userIds } },
            { 
                $set: { 
                    plan: 'N/A',
                    expiration_date: null 
                } 
            }
        );

        console.log(`[PLAN_EXPIRATION] Expired ${result.modifiedCount} plan(s)`);
        
        // Log which users were affected
        const usernames = expiredUsers.map(user => user.username).join(', ');
        if (usernames) {
            console.log(`[PLAN_EXPIRATION] Expired plans for users: ${usernames}`);
        }

        // Remove customer role from users with linked Discord accounts
        if (DISCORD.CUSTOMER_ROLE_ID) {
            for (const user of expiredUsers) {
                if (user.discord_id) {
                    try {
                        await removeRoleFromMember(user.discord_id, DISCORD.CUSTOMER_ROLE_ID);
                        console.log(`[PLAN_EXPIRATION] Removed customer role from Discord user ${user.discord_id} (${user.username})`);
                    } catch (error) {
                        // Log but continue, role removal failure shouldn't block expiration
                        console.warn(`[PLAN_EXPIRATION] Failed to remove customer role from Discord user ${user.discord_id} (${user.username}):`, error.message);
                    }
                }
            }
        }

        return { 
            expired: result.modifiedCount, 
            users: expiredUsers.map(u => ({ id: u._id.toString(), username: u.username }))
        };
    } catch (error) {
        console.error('[PLAN_EXPIRATION] Error expiring plans:', error);
        throw error;
    }
}

/**
 * Start the plan expiration scheduler
 * @param {number} intervalMs - Interval in milliseconds (default: 1 hour)
 */
function startPlanExpirationScheduler(intervalMs = 60 * 60 * 1000) {
    console.log(`[PLAN_EXPIRATION] Starting scheduler (runs every ${intervalMs / 1000 / 60} minutes)`);
    
    // Run immediately on start
    expirePlans().catch(err => {
        console.error('[PLAN_EXPIRATION] Initial expiration check failed:', err);
    });

    // Then run on interval
    const interval = setInterval(() => {
        expirePlans().catch(err => {
            console.error('[PLAN_EXPIRATION] Scheduled expiration check failed:', err);
        });
    }, intervalMs);

    return interval;
}

/**
 * Stop the plan expiration scheduler
 */
function stopPlanExpirationScheduler(interval) {
    if (interval) {
        clearInterval(interval);
        console.log('[PLAN_EXPIRATION] Scheduler stopped');
    }
}

module.exports = {
    expirePlans,
    startPlanExpirationScheduler,
    stopPlanExpirationScheduler
};

