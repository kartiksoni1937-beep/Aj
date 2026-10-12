const { RATE_LIMIT } = require('../config/constants');

class RateLimiter {
    constructor() {
        this.ipStore = new Map();
        this.hwidStore = new Map();
        this.config = RATE_LIMIT;
        this.startCleanupInterval();
    }

    startCleanupInterval() {
        this.cleanupInterval = setInterval(() => {
            this.cleanup();
        }, this.config.CLEANUP_INTERVAL_MS);
    }

    cleanup() {
        const now = Date.now();

        this.cleanStore(this.ipStore, now);
        this.cleanStore(this.hwidStore, now);

        console.log(`[RATE_LIMIT] Cleanup: ${this.ipStore.size} IPs, ${this.hwidStore.size} HWIDs tracked`);
    }

    cleanStore(store, now) {
        for (const [key, data] of store.entries()) {
            if (data.lockedUntil && now > data.lockedUntil) {
                store.delete(key);
            } else if (now - data.firstAttempt > this.config.WINDOW_MS) {
                store.delete(key);
            }
        }
    }

    check(ip, hwid) {
        const now = Date.now();

        const ipCheck = this.checkStore(this.ipStore, ip, now, 'ip');
        if (ipCheck.limited) return ipCheck;

        if (hwid) {
            const hwidCheck = this.checkStore(this.hwidStore, hwid, now, 'hwid');
            if (hwidCheck.limited) return hwidCheck;
        }
        return { limited: false };
    }

    checkStore(store, key, now, type) {
        const data = store.get(key);
        if (!data) return { limited: false };

        if (data.lockedUntil && now < data.lockedUntil) {
            const remainingTime = Math.ceil((data.lockedUntil - now) / 1000 / 60);
            const message = type === 'ip'
                ? `Too many failed login attempts. Please try again in ${remainingTime} minutes.`
                : `This device is temporarily locked due to too many failed login attempts. Please try again in ${remainingTime} minutes.`;

            return {
                limited: true,
                reason: type,
                message,
                remainingTime,
                attempts: data.attempts
            };
        }

        if (now - data.firstAttempt > this.config.WINDOW_MS) {
            store.delete(key);
        }

        return { limited: false };
    }

    recordFailedAttempt(ip, hwid) {
        const now = Date.now();

        this.recordAttempt(this.ipStore, ip, now, 'IP');

        if (hwid) {
            this.recordAttempt(this.hwidStore, hwid, now, 'HWID');
        }
    }

    recordAttempt(store, key, now, type) {
        const data = store.get(key) || { attempts: 0, firstAttempt: now };

        data.attempts++;

        if (data.attempts === 1){
            data.firstAttempt = now;
        }

        if (data.attempts >= this.config.MAX_ATTEMPTS) {
            data.lockedUntil = now + this.config.LOCKOUT_MS;
            const displayKey = type === 'HWID' ? `${key.substring(0, 8)}...` : key;
            console.warn(`[SECURITY] ${type} ${displayKey} locked out until ${new Date(data.lockedUntil).toISOString()} (${data.attempts} attempts)`);
        } else {
            const displayKey = type === 'HWID' ? `${key.substring(0, 8)}...` : key;
            console.log(`[SECURITY] Failed login attempt ${data.attempts}/${this.config.MAX_ATTEMPTS} for ${type} ${displayKey}`);
        }

        store.set(key, data);
    }

    clear(ip, hwid) {
        const cleared = [];

        if (this.ipStore.delete(ip)) {
            cleared.push('IP');
        }

        if (hwid && this.hwidStore.delete(hwid)) {
            cleared.push('HWID');
        }

        if (cleared.length > 0) {
            console.log(`[SECURITY] Cleared ${cleared.join(', ')} rate limits`);
        }
    }

    // Admin 
    
    getStats() {
        const ipLocked = Array.from(this.ipStore.values()).filter(d => d.lockedUntil && Date.now() < d.lockedUntil).length;
        const hwidLocked = Array.from(this.hwidStore.values()).filter(d => d.lockedUntil && Date.now() < d.lockedUntil).length;
        
        return {
            ipEntries: this.ipStore.size,
            hwidEntries: this.hwidStore.size,
            ipLocked,
            hwidLocked,
            config: this.config
        };
    }

    clearAll() {
        const ipCount = this.ipStore.size;
        const hwidCount = this.hwidStore.size;

        this.ipStore.clear();
        this.hwidStore.clear();

        console.log(`[SECURITY] Cleared all rate limits: ${ipCount} IPs, ${hwidCount} HWIDs`);
    }

    clearIP(ip) {
        const deleted = this.ipStore.delete(ip);
        if (deleted) {
            console.log(`[SECURITY] Cleared IP rate limit: ${ip}`);
        }
        return deleted;
    }
    
    clearHWID(hwid) {
        const deleted = this.hwidStore.delete(hwid);
        if (deleted) {
            console.log(`[SECURITY] Cleared HWID rate limit: ${hwid}`);
        }
        return deleted;
    }

    stop() {
        if (this.cleanupInterval) {
            clearInterval(this.cleanupInterval);
            this.cleanupInterval = null;
        }
    }
}

module.exports = new RateLimiter();