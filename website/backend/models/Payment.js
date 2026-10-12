const mongoose = require('mongoose');

const paymentSchema = new mongoose.Schema({
    user_id: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true,
        index: true
    },
    track_id: {
        type: String,
        required: true,
        unique: true,
        index: true
    },
    order_id: {
        type: String,
        required: true,
        unique: true,
        index: true
    },
    plan: {
        type: String,
        required: true,
        enum: ['lifetime', 'premium'],
        index: true
    },
    amount: {
        type: Number,
        required: true
    },
    currency: {
        type: String,
        required: true
    },
    status: {
        type: String,
        enum: ['pending', 'paying', 'paid', 'failed', 'expired'],
        default: 'pending',
        index: true
    },
    payment_url: {
        type: String,
        default: null
    },
    expired_at: {
        type: Date,
        default: null
    },
    paid_at: {
        type: Date,
        default: null
    }
}, {
    timestamps: true
});

// Compound index for faster lookups
paymentSchema.index({ user_id: 1, status: 1 });
paymentSchema.index({ track_id: 1, status: 1 });

const Payment = mongoose.model('Payment', paymentSchema);

module.exports = Payment;

