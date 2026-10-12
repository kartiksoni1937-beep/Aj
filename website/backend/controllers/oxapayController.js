const axios = require('axios');
const crypto = require('crypto');
const User = require('../models/User');
const Payment = require('../models/Payment');
const database = require('../utils/database');
const { OXAPAY, FRONTEND_URL, BACKEND_URL, DISCORD } = require('../config/constants');
const sessionToken = require('../utils/sessionToken');
const { addRoleToMember } = require('../utils/discord');

// Generate unique order ID
function generateOrderId(userId, plan) {
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(2, 8).toUpperCase();
    return `ECHO-${plan.toUpperCase()}-${timestamp}-${random}`;
}

// Create lifetime payment invoice
exports.createLifetimeInvoice = async (req, res) => {
    try {
        if (!OXAPAY.MERCHANT_API_KEY) {
            return res.status(500).json({
                success: false,
                message: 'OxaPay is not configured'
            });
        }

        const token = req.headers.authorization?.replace('Bearer ', '');

        if (!token) {
            return res.status(401).json({
                success: false,
                message: 'No token provided'
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

        const user = await database.findUserById(decoded.uid);
        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        const orderId = generateOrderId(decoded.uid, 'lifetime');

        // Create payment record
        const payment = new Payment({
            user_id: decoded.uid,
            order_id: orderId,
            plan: 'lifetime',
            amount: OXAPAY.PRICES.LIFETIME.AMOUNT,
            currency: OXAPAY.PRICES.LIFETIME.CURRENCY,
            status: 'pending'
        });

        // Prepare OxaPay invoice request
        const invoiceData = {
            amount: OXAPAY.PRICES.LIFETIME.AMOUNT,
            currency: OXAPAY.PRICES.LIFETIME.CURRENCY,
            lifetime: 60, // 60 minutes expiration
            fee_paid_by_payer: 0, // Merchant pays fee
            callback_url: `${BACKEND_URL}/api/payments/oxapay/webhook`,
            return_url: `${FRONTEND_URL}/dashboard?payment=success&order_id=${orderId}`,
            order_id: orderId,
            thanks_message: 'Thank you for your purchase! Your lifetime license is being activated.',
            description: `Echo Client - Lifetime License for ${user.username}`,
            sandbox: OXAPAY.SANDBOX
        };

        // Make request to OxaPay API
        const response = await axios.post('https://api.oxapay.com/v1/payment/invoice', invoiceData, {
            headers: {
                'merchant_api_key': OXAPAY.MERCHANT_API_KEY,
                'Content-Type': 'application/json'
            }
        });

        if (response.data && response.data.status === 200 && response.data.data) {
            const { track_id, payment_url, expired_at } = response.data.data;

            // Update payment record with track_id and payment_url
            payment.track_id = track_id;
            payment.payment_url = payment_url;
            payment.expired_at = new Date(expired_at * 1000);
            await payment.save();

            res.json({
                success: true,
                trackId: track_id,
                paymentUrl: payment_url,
                orderId: orderId
            });
        } else {
            await payment.remove(); // Remove failed payment record
            res.status(500).json({
                success: false,
                message: response.data?.message || 'Failed to create invoice'
            });
        }
    } catch (error) {
        console.error('[OXAPAY] Create lifetime invoice error:', error.response?.data || error.message);
        res.status(500).json({
            success: false,
            message: 'Failed to create payment invoice'
        });
    }
};

// Create monthly subscription invoice (Note: OxaPay doesn't support subscriptions, so this is a one-time payment for 1 month)
exports.createMonthlyInvoice = async (req, res) => {
    try {
        if (!OXAPAY.MERCHANT_API_KEY) {
            return res.status(500).json({
                success: false,
                message: 'OxaPay is not configured'
            });
        }

        const token = req.headers.authorization?.replace('Bearer ', '');

        if (!token) {
            return res.status(401).json({
                success: false,
                message: 'No token provided'
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

        const user = await database.findUserById(decoded.uid);
        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        const orderId = generateOrderId(decoded.uid, 'premium');

        // Create payment record
        const payment = new Payment({
            user_id: decoded.uid,
            order_id: orderId,
            plan: 'premium',
            amount: OXAPAY.PRICES.MONTHLY.AMOUNT,
            currency: OXAPAY.PRICES.MONTHLY.CURRENCY,
            status: 'pending'
        });

        // Prepare OxaPay invoice request
        const invoiceData = {
            amount: OXAPAY.PRICES.MONTHLY.AMOUNT,
            currency: OXAPAY.PRICES.MONTHLY.CURRENCY,
            lifetime: 60, // 60 minutes expiration
            fee_paid_by_payer: 0, // Merchant pays fee
            callback_url: `${BACKEND_URL}/api/payments/oxapay/webhook`,
            return_url: `${FRONTEND_URL}/dashboard?payment=success&order_id=${orderId}`,
            order_id: orderId,
            thanks_message: 'Thank you for your purchase! Your premium subscription is being activated.',
            description: `Echo Client - Monthly Premium for ${user.username}`,
            sandbox: OXAPAY.SANDBOX
        };

        // Make request to OxaPay API
        const response = await axios.post('https://api.oxapay.com/v1/payment/invoice', invoiceData, {
            headers: {
                'merchant_api_key': OXAPAY.MERCHANT_API_KEY,
                'Content-Type': 'application/json'
            }
        });

        if (response.data && response.data.status === 200 && response.data.data) {
            const { track_id, payment_url, expired_at } = response.data.data;

            // Update payment record with track_id and payment_url
            payment.track_id = track_id;
            payment.payment_url = payment_url;
            payment.expired_at = new Date(expired_at * 1000);
            await payment.save();

            res.json({
                success: true,
                trackId: track_id,
                paymentUrl: payment_url,
                orderId: orderId
            });
        } else {
            await payment.remove(); // Remove failed payment record
            res.status(500).json({
                success: false,
                message: response.data?.message || 'Failed to create invoice'
            });
        }
    } catch (error) {
        console.error('[OXAPAY] Create monthly invoice error:', error.response?.data || error.message);
        res.status(500).json({
            success: false,
            message: 'Failed to create payment invoice'
        });
    }
};

// Handle OxaPay webhook
exports.handleWebhook = async (req, res) => {
    try {
        if (!OXAPAY.MERCHANT_API_KEY) {
            console.error('[OXAPAY] Merchant API key not configured');
            return res.status(500).send('OxaPay not configured');
        }

        // Get raw body for HMAC validation (req.body is a Buffer when using express.raw())
        const rawBody = req.body.toString('utf8');
        const hmacHeader = req.headers['hmac'] || req.headers['HMAC'];

        if (!hmacHeader) {
            console.error('[OXAPAY] Missing HMAC header');
            return res.status(400).send('Missing HMAC signature');
        }

        // Validate HMAC signature using the raw body string
        const calculatedHmac = crypto
            .createHmac('sha512', OXAPAY.MERCHANT_API_KEY)
            .update(rawBody)
            .digest('hex');

        if (calculatedHmac.toLowerCase() !== hmacHeader.toLowerCase()) {
            console.error('[OXAPAY] Invalid HMAC signature');
            console.error(`[OXAPAY] Expected: ${calculatedHmac}, Received: ${hmacHeader}`);
            return res.status(400).send('Invalid HMAC signature');
        }

        // Parse the JSON body
        const data = JSON.parse(rawBody);
        const { track_id, status, order_id, amount, currency } = data;

        if (!track_id || !status) {
            console.error('[OXAPAY] Missing required fields in webhook');
            return res.status(400).send('Missing required fields');
        }

        // Find payment by track_id
        const payment = await Payment.findOne({ track_id });
        if (!payment) {
            console.error(`[OXAPAY] Payment not found for track_id: ${track_id}`);
            return res.status(404).send('Payment not found');
        }

        // Update payment status
        if (status === 'Paying') {
            payment.status = 'paying';
            await payment.save();
            console.log(`[OXAPAY] Payment ${track_id} is paying (awaiting confirmation)`);
        } else if (status === 'Paid') {
            // Only process if not already processed
            if (payment.status !== 'paid') {
                payment.status = 'paid';
                payment.paid_at = new Date();
                await payment.save();

                // Update user plan
                const user = await User.findById(payment.user_id);
                if (!user) {
                    console.error(`[OXAPAY] User not found for payment ${track_id}`);
                    return res.status(404).send('User not found');
                }

                const now = new Date();
                let expirationDate = null;

                if (payment.plan === 'lifetime') {
                    // Lifetime = 100 years from now
                    expirationDate = new Date(now.getFullYear() + 100, now.getMonth(), now.getDate());
                } else if (payment.plan === 'premium') {
                    // Monthly subscription = 30 days from now
                    expirationDate = new Date(now);
                    expirationDate.setDate(expirationDate.getDate() + 30);
                }

                await User.findByIdAndUpdate(payment.user_id, {
                    plan: payment.plan === 'lifetime' ? 'lifetime' : 'premium',
                    expiration_date: expirationDate
                });

                // Grant customer role if Discord is linked
                if (user.discord_id && DISCORD.CUSTOMER_ROLE_ID) {
                    try {
                        await addRoleToMember(user.discord_id, DISCORD.CUSTOMER_ROLE_ID);
                        console.log(`[OXAPAY] Granted customer role to Discord user ${user.discord_id} (${user.username})`);
                    } catch (error) {
                        console.warn(`[OXAPAY] Failed to grant customer role to Discord user ${user.discord_id} (${user.username}):`, error.message);
                    }
                }

                console.log(`[OXAPAY] Payment ${track_id} confirmed. Plan ${payment.plan} activated for user ${user.username}`);
            }
        } else if (status === 'Failed' || status === 'Expired') {
            payment.status = status.toLowerCase();
            await payment.save();
            console.log(`[OXAPAY] Payment ${track_id} ${status.toLowerCase()}`);
        }

        // Return OK as required by OxaPay
        res.status(200).send('OK');
    } catch (error) {
        console.error('[OXAPAY] Webhook error:', error);
        res.status(500).send('Webhook handler failed');
    }
};

// Check payment status
exports.checkPaymentStatus = async (req, res) => {
    try {
        const { orderId } = req.query;

        if (!orderId) {
            return res.status(400).json({
                success: false,
                message: 'Order ID is required'
            });
        }

        const payment = await Payment.findOne({ order_id: orderId });

        if (!payment) {
            return res.status(404).json({
                success: false,
                message: 'Payment not found'
            });
        }

        res.json({
            success: true,
            status: payment.status,
            trackId: payment.track_id,
            paymentUrl: payment.payment_url
        });
    } catch (error) {
        console.error('[OXAPAY] Check payment status error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to check payment status'
        });
    }
};

