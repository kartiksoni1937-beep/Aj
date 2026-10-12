const mongoose = require('mongoose');

const keySchema = new mongoose.Schema({
    key: {
        type: String,
        required: true,
        unique: true,
        trim: true,
        uppercase: true
    },
    plan: {
        type: String,
        required: true,
        enum: ['premium', 'lifetime'],
        default: 'premium'
    },
    duration_days: {
        type: Number,
        default: 30 // Default 30 days for premium
    },
    discord_id: {
        type: String,
        default: null
    },
    redeemed_by: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        default: null
    },
    redeemed_at: {
        type: Date,
        default: null
    },
    created_by: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        default: null
    },
    is_active: {
        type: Boolean,
        default: true
    }
}, {
    timestamps: true
});

// Index for faster lookups (key already has unique index from unique: true)
keySchema.index({ redeemed_by: 1 });
keySchema.index({ is_active: 1 });

const Key = mongoose.model('Key', keySchema);

module.exports = Key;

