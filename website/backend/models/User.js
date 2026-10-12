const mongoose = require('mongoose');

const userSchema = new mongoose.Schema({
    username: {
        type: String,
        required: true,
        unique: true,
        trim: true
    },
    userNumber: {
        type: Number,
        unique: true,
        sparse: true
    },
    password: {
        type: String,
        required: true
    },
    loaded_config: {
        type: String,
        default: null
    },
    hwid: {
        type: String,
        default: null
    },
    plan: {
        type: String,
        default: 'N/A'
    },
    expiration_date: {
        type: Date,
        default: null
    },
    admin: {
        type: Boolean,
        default: false,
        index: true
    },
    banned: {
        type: Boolean,
        default: false,
        index: true
    },
    ban_reason: {
        type: String,
        default: null
    },
    discord_id: {
        type: String,
        default: null,
        index: true
    },
    discord_username: {
        type: String,
        default: null
    },
    last_hwid_reset: {
        type: Date,
        default: null
    },
    last_login: {
        type: Date,
        default: null
    },
    last_login_ip: {
        type: String,
        default: null
    },
    last_attempt: {
        type: Date,
        default: null
    },
    last_attempt_ip: {
        type: String,
        default: null
    },
    last_failed_attempt: {
        type: Date,
        default: null
    },
    stripe_customer_id: {
        type: String,
        default: null,
        index: true
    },
    stripe_subscription_id: {
        type: String,
        default: null,
        index: true
    }
}, {
    timestamps: true // Adds createdAt and updatedAt automatically
});

// Index for faster lookups (username already has unique index, adding hwid index)
userSchema.index({ hwid: 1 });
userSchema.index({ expiration_date: 1 }); // For plan expiration queries
userSchema.index({ plan: 1, expiration_date: 1 }); // Compound index for expiration checks

// Auto-increment userNumber before saving
userSchema.pre('save', async function(next) {
    if (this.isNew && !this.userNumber) {
        try {
            const UserModel = this.constructor;
            const lastUser = await UserModel.findOne({}, {}, { sort: { userNumber: -1 } });
            this.userNumber = lastUser && lastUser.userNumber ? lastUser.userNumber + 1 : 1;
        } catch (error) {
            return next(error);
        }
    }
    next();
});

const User = mongoose.model('User', userSchema);

module.exports = User;


