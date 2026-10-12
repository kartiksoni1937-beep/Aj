const mongoose = require('mongoose');

const releaseSchema = new mongoose.Schema({
    version: {
        type: String,
        required: true,
        unique: true,
        trim: true
    },
    file_url: {
        type: String,
        required: true
    },
    file_name: {
        type: String,
        required: true
    },
    file_size: {
        type: Number, // Size in bytes
        default: 0
    },
    added: {
        type: [String],
        default: []
    },
    changed: {
        type: [String],
        default: []
    },
    fixed: {
        type: [String],
        default: []
    },
    created_by: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        default: null
    },
    discord_message_id: {
        type: String,
        default: null
    },
    discord_channel_id: {
        type: String,
        default: null
    },
    is_active: {
        type: Boolean,
        default: true
    },
    is_silent: {
        type: Boolean,
        default: false
    }
}, {
    timestamps: true
});

// Index for faster lookups (version already has unique index from unique: true)
releaseSchema.index({ is_active: 1 });
releaseSchema.index({ createdAt: -1 });

const Release = mongoose.model('Release', releaseSchema);

module.exports = Release;

