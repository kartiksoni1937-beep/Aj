const stripe = require('stripe');
const User = require('../models/User');
const database = require('../utils/database');
const { STRIPE, FRONTEND_URL, DISCORD } = require('../config/constants');
const sessionToken = require('../utils/sessionToken');
const { addRoleToMember, removeRoleFromMember } = require('../utils/discord');

// Initialize Stripe
const stripeClient = STRIPE.SECRET_KEY ? stripe(STRIPE.SECRET_KEY) : null;

// Cache for price IDs (to avoid recreating them)
let priceIdCache = {
    lifetime: null,
    monthly: null
};

// Initialize prices - create them in Stripe if they don't exist
async function ensurePricesExist() {
    if (!stripeClient) return;

    try {
        // Check if we have price IDs in config
        const lifetimePriceId = STRIPE.PRICES.LIFETIME.PRICE_ID;
        const monthlyPriceId = STRIPE.PRICES.MONTHLY.PRICE_ID;

        // Create lifetime price if not exists
        if (!lifetimePriceId) {
            console.log('[PAYMENT] Creating lifetime price in Stripe...');
            const lifetimePrice = await stripeClient.prices.create({
                currency: STRIPE.PRICES.LIFETIME.CURRENCY,
                unit_amount: STRIPE.PRICES.LIFETIME.AMOUNT,
                product_data: {
                    name: 'Echo Client - Lifetime License'
                }
            });
            priceIdCache.lifetime = lifetimePrice.id;
            console.log(`[PAYMENT] Created lifetime price: ${lifetimePrice.id}`);
            console.log(`[PAYMENT] Add this to config.json: "priceId": "${lifetimePrice.id}"`);
        } else {
            priceIdCache.lifetime = lifetimePriceId;
        }

        // Create monthly subscription price if not exists
        if (!monthlyPriceId) {
            console.log('[PAYMENT] Creating monthly price in Stripe...');
            const monthlyPrice = await stripeClient.prices.create({
                currency: STRIPE.PRICES.MONTHLY.CURRENCY,
                unit_amount: STRIPE.PRICES.MONTHLY.AMOUNT,
                recurring: {
                    interval: 'month'
                },
                product_data: {
                    name: 'Echo Client - Monthly Premium'
                }
            });
            priceIdCache.monthly = monthlyPrice.id;
            console.log(`[PAYMENT] Created monthly price: ${monthlyPrice.id}`);
            console.log(`[PAYMENT] Add this to config.json: "priceId": "${monthlyPrice.id}"`);
        } else {
            priceIdCache.monthly = monthlyPriceId;
        }
    } catch (error) {
        console.error('[PAYMENT] Error ensuring prices exist:', error);
    }
}

// Initialize prices on module load
if (stripeClient) {
    ensurePricesExist().catch(err => {
        console.error('[PAYMENT] Failed to initialize prices:', err);
    });
}

// Create checkout session for lifetime payment
exports.createLifetimeCheckout = async (req, res) => {
    try {
        if (!stripeClient) {
            return res.status(500).json({
                success: false,
                message: 'Stripe is not configured'
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

        // Ensure price exists
        await ensurePricesExist();
        
        // Use price ID if available, otherwise use price_data
        const priceId = priceIdCache.lifetime || STRIPE.PRICES.LIFETIME.PRICE_ID;
        
        const lineItem = priceId ? {
            price: priceId,
            quantity: 1
        } : {
            price_data: {
                currency: STRIPE.PRICES.LIFETIME.CURRENCY,
                product_data: {
                    name: 'Echo Client - Lifetime License'
                },
                unit_amount: STRIPE.PRICES.LIFETIME.AMOUNT, // €35.00 = 3500 cents
            },
            quantity: 1,
        };

        // Create Stripe Checkout Session for one-time payment
        const session = await stripeClient.checkout.sessions.create({
            payment_method_types: ['card'],
            line_items: [lineItem],
            mode: 'payment',
            success_url: `${FRONTEND_URL}/dashboard?payment=success&session_id={CHECKOUT_SESSION_ID}`,
            cancel_url: `${FRONTEND_URL}/dashboard?payment=cancelled`,
            client_reference_id: decoded.uid,
            metadata: {
                userId: decoded.uid,
                plan: 'lifetime',
                type: 'one-time'
            }
        });

        res.json({
            success: true,
            sessionId: session.id,
            url: session.url
        });
    } catch (error) {
        console.error('[PAYMENT] Create lifetime checkout error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to create checkout session'
        });
    }
};

// Create checkout session for monthly subscription
exports.createMonthlySubscription = async (req, res) => {
    try {
        if (!stripeClient) {
            return res.status(500).json({
                success: false,
                message: 'Stripe is not configured'
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

        // Check if user already has an active subscription
        if (user.stripe_customer_id) {
            try {
                const customer = await stripeClient.customers.retrieve(user.stripe_customer_id);
                if (customer.subscriptions && customer.subscriptions.data.length > 0) {
                    const activeSubscription = customer.subscriptions.data.find(
                        sub => sub.status === 'active' || sub.status === 'trialing'
                    );
                    if (activeSubscription) {
                        return res.status(400).json({
                            success: false,
                            message: 'You already have an active subscription'
                        });
                    }
                }
            } catch (error) {
                // Customer might not exist, continue with creating new subscription
            }
        }

        // Create or retrieve Stripe customer
        let customerId = user.stripe_customer_id;
        if (!customerId) {
            const customer = await stripeClient.customers.create({
                metadata: {
                    userId: decoded.uid,
                    username: user.username
                }
            });
            customerId = customer.id;
            
            // Save customer ID to user
            await User.findByIdAndUpdate(decoded.uid, {
                stripe_customer_id: customerId
            });
        }

        // Ensure price exists
        await ensurePricesExist();
        
        // Use price ID if available, otherwise use price_data
        const priceId = priceIdCache.monthly || STRIPE.PRICES.MONTHLY.PRICE_ID;
        
        const lineItem = priceId ? {
            price: priceId,
            quantity: 1
        } : {
            price_data: {
                currency: STRIPE.PRICES.MONTHLY.CURRENCY,
                product_data: {
                    name: 'Echo Client - Monthly Premium'
                },
                recurring: {
                    interval: 'month'
                },
                unit_amount: STRIPE.PRICES.MONTHLY.AMOUNT, // €6.70 = 670 cents
            },
            quantity: 1,
        };

        // Create Stripe Checkout Session for subscription
        const session = await stripeClient.checkout.sessions.create({
            customer: customerId,
            payment_method_types: ['card'],
            line_items: [lineItem],
            mode: 'subscription',
            success_url: `${FRONTEND_URL}/dashboard?payment=success&session_id={CHECKOUT_SESSION_ID}`,
            cancel_url: `${FRONTEND_URL}/dashboard?payment=cancelled`,
            client_reference_id: decoded.uid,
            metadata: {
                userId: decoded.uid,
                plan: 'premium',
                type: 'subscription'
            }
        });

        res.json({
            success: true,
            sessionId: session.id,
            url: session.url
        });
    } catch (error) {
        console.error('[PAYMENT] Create subscription error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to create subscription'
        });
    }
};

// Handle Stripe webhook events
exports.handleWebhook = async (req, res) => {
    try {
        if (!stripeClient) {
            return res.status(500).json({ error: 'Stripe not configured' });
        }

        const sig = req.headers['stripe-signature'];
        const webhookSecret = STRIPE.WEBHOOK_SECRET;

        if (!webhookSecret) {
            console.warn('[PAYMENT] Webhook secret not configured, skipping verification');
            return res.status(400).json({ error: 'Webhook secret not configured' });
        }

        let event;
        try {
            event = stripeClient.webhooks.constructEvent(req.body, sig, webhookSecret);
        } catch (err) {
            console.error('[PAYMENT] Webhook signature verification failed:', err.message);
            return res.status(400).json({ error: `Webhook Error: ${err.message}` });
        }

        // Handle the event
        switch (event.type) {
            case 'checkout.session.completed':
                await handleCheckoutCompleted(event.data.object);
                break;

            case 'customer.subscription.created':
            case 'customer.subscription.updated':
                await handleSubscriptionUpdate(event.data.object);
                break;

            case 'customer.subscription.deleted':
                await handleSubscriptionCancelled(event.data.object);
                break;

            case 'invoice.payment_succeeded':
                await handleInvoicePaymentSucceeded(event.data.object);
                break;

            case 'invoice.payment_failed':
                await handleInvoicePaymentFailed(event.data.object);
                break;

            default:
                console.log(`[PAYMENT] Unhandled event type: ${event.type}`);
        }

        res.json({ received: true });
    } catch (error) {
        console.error('[PAYMENT] Webhook error:', error);
        res.status(500).json({ error: 'Webhook handler failed' });
    }
};

// Handle checkout session completed
async function handleCheckoutCompleted(session) {
    try {
        const userId = session.metadata?.userId || session.client_reference_id;
        if (!userId) {
            console.error('[PAYMENT] No user ID in checkout session');
            return;
        }

        const user = await User.findById(userId);
        if (!user) {
            console.error('[PAYMENT] User not found:', userId);
            return;
        }

        if (session.mode === 'payment') {
            // One-time payment (lifetime)
            const expirationDate = new Date();
            expirationDate.setFullYear(expirationDate.getFullYear() + 100); // 100 years = lifetime

            await User.findByIdAndUpdate(userId, {
                plan: 'lifetime',
                expiration_date: expirationDate,
                stripe_customer_id: session.customer || user.stripe_customer_id
            });

            // Grant customer role if Discord is linked
            if (user.discord_id && DISCORD.CUSTOMER_ROLE_ID) {
                try {
                    await addRoleToMember(user.discord_id, DISCORD.CUSTOMER_ROLE_ID);
                    console.log(`[PAYMENT] Granted customer role to Discord user ${user.discord_id} (${user.username})`);
                } catch (error) {
                    console.warn(`[PAYMENT] Failed to grant customer role to Discord user ${user.discord_id} (${user.username}):`, error.message);
                }
            }

            console.log(`[PAYMENT] Lifetime plan activated for user ${user.username}`);
        } else if (session.mode === 'subscription') {
            // Subscription payment
            const subscription = await stripeClient.subscriptions.retrieve(session.subscription);
            
            const expirationDate = new Date(subscription.current_period_end * 1000);

            await User.findByIdAndUpdate(userId, {
                plan: 'premium',
                expiration_date: expirationDate,
                stripe_subscription_id: subscription.id,
                stripe_customer_id: session.customer || user.stripe_customer_id
            });

            // Grant customer role if Discord is linked
            if (user.discord_id && DISCORD.CUSTOMER_ROLE_ID) {
                try {
                    await addRoleToMember(user.discord_id, DISCORD.CUSTOMER_ROLE_ID);
                    console.log(`[PAYMENT] Granted customer role to Discord user ${user.discord_id} (${user.username})`);
                } catch (error) {
                    console.warn(`[PAYMENT] Failed to grant customer role to Discord user ${user.discord_id} (${user.username}):`, error.message);
                }
            }

            console.log(`[PAYMENT] Subscription activated for user ${user.username}`);
        }
    } catch (error) {
        console.error('[PAYMENT] Error handling checkout completed:', error);
    }
}

// Handle subscription update
async function handleSubscriptionUpdate(subscription) {
    try {
        const customerId = subscription.customer;
        const user = await User.findOne({ stripe_customer_id: customerId });
        
        if (!user) {
            console.error('[PAYMENT] User not found for customer:', customerId);
            return;
        }

        if (subscription.status === 'active' || subscription.status === 'trialing') {
            const expirationDate = new Date(subscription.current_period_end * 1000);
            
            await User.findByIdAndUpdate(user._id, {
                plan: 'premium',
                expiration_date: expirationDate,
                stripe_subscription_id: subscription.id
            });

            // Grant customer role if Discord is linked (in case it wasn't granted before)
            if (user.discord_id && DISCORD.CUSTOMER_ROLE_ID) {
                try {
                    await addRoleToMember(user.discord_id, DISCORD.CUSTOMER_ROLE_ID);
                    console.log(`[PAYMENT] Granted customer role to Discord user ${user.discord_id} (${user.username})`);
                } catch (error) {
                    console.warn(`[PAYMENT] Failed to grant customer role to Discord user ${user.discord_id} (${user.username}):`, error.message);
                }
            }

            console.log(`[PAYMENT] Subscription updated for user ${user.username}`);
        } else if (subscription.status === 'canceled' || subscription.status === 'unpaid') {
            // Subscription cancelled or unpaid - expire immediately
            await User.findByIdAndUpdate(user._id, {
                plan: 'N/A',
                expiration_date: new Date()
            });

            // Remove customer role from Discord if linked
            if (user.discord_id && DISCORD.CUSTOMER_ROLE_ID) {
                try {
                    await removeRoleFromMember(user.discord_id, DISCORD.CUSTOMER_ROLE_ID);
                    console.log(`[PAYMENT] Removed customer role from Discord user ${user.discord_id} (${user.username})`);
                } catch (error) {
                    console.warn(`[PAYMENT] Failed to remove customer role from Discord user ${user.discord_id} (${user.username}):`, error.message);
                }
            }

            console.log(`[PAYMENT] Subscription cancelled for user ${user.username}`);
        }
    } catch (error) {
        console.error('[PAYMENT] Error handling subscription update:', error);
    }
}

// Handle subscription cancelled
async function handleSubscriptionCancelled(subscription) {
    try {
        const customerId = subscription.customer;
        const user = await User.findOne({ stripe_customer_id: customerId });
        
        if (!user) {
            console.error('[PAYMENT] User not found for customer:', customerId);
            return;
        }

        // Expire the plan immediately
        await User.findByIdAndUpdate(user._id, {
            plan: 'N/A',
            expiration_date: new Date(),
            stripe_subscription_id: null
        });

        // Remove customer role from Discord if linked
        if (user.discord_id && DISCORD.CUSTOMER_ROLE_ID) {
            try {
                await removeRoleFromMember(user.discord_id, DISCORD.CUSTOMER_ROLE_ID);
                console.log(`[PAYMENT] Removed customer role from Discord user ${user.discord_id} (${user.username})`);
            } catch (error) {
                console.warn(`[PAYMENT] Failed to remove customer role from Discord user ${user.discord_id} (${user.username}):`, error.message);
            }
        }

        console.log(`[PAYMENT] Subscription cancelled for user ${user.username}`);
    } catch (error) {
        console.error('[PAYMENT] Error handling subscription cancelled:', error);
    }
}

// Handle invoice payment succeeded
async function handleInvoicePaymentSucceeded(invoice) {
    try {
        const customerId = invoice.customer;
        const user = await User.findOne({ stripe_customer_id: customerId });
        
        if (!user) {
            console.error('[PAYMENT] User not found for customer:', customerId);
            return;
        }

        if (invoice.subscription) {
            const subscription = await stripeClient.subscriptions.retrieve(invoice.subscription);
            const expirationDate = new Date(subscription.current_period_end * 1000);
            
            await User.findByIdAndUpdate(user._id, {
                plan: 'premium',
                expiration_date: expirationDate
            });

            console.log(`[PAYMENT] Invoice payment succeeded for user ${user.username}`);
        }
    } catch (error) {
        console.error('[PAYMENT] Error handling invoice payment succeeded:', error);
    }
}

// Handle invoice payment failed
async function handleInvoicePaymentFailed(invoice) {
    try {
        const customerId = invoice.customer;
        const user = await User.findOne({ stripe_customer_id: customerId });
        
        if (!user) {
            console.error('[PAYMENT] User not found for customer:', customerId);
            return;
        }

        // If payment fails, we might want to give grace period or expire immediately
        // For now, we'll let Stripe handle retries and only expire if subscription is cancelled
        console.log(`[PAYMENT] Invoice payment failed for user ${user.username}`);
    } catch (error) {
        console.error('[PAYMENT] Error handling invoice payment failed:', error);
    }
}

// Get customer portal URL for managing subscription
exports.getCustomerPortal = async (req, res) => {
    try {
        if (!stripeClient) {
            return res.status(500).json({
                success: false,
                message: 'Stripe is not configured'
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

        if (!user.stripe_customer_id) {
            return res.status(400).json({
                success: false,
                message: 'No active subscription found'
            });
        }

        const session = await stripeClient.billingPortal.sessions.create({
            customer: user.stripe_customer_id,
            return_url: `${FRONTEND_URL}/dashboard`,
        });

        res.json({
            success: true,
            url: session.url
        });
    } catch (error) {
        console.error('[PAYMENT] Get customer portal error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to create customer portal session'
        });
    }
};

// Check payment status
exports.checkPaymentStatus = async (req, res) => {
    try {
        if (!stripeClient) {
            return res.status(500).json({
                success: false,
                message: 'Stripe is not configured'
            });
        }

        const { sessionId } = req.query;

        if (!sessionId) {
            return res.status(400).json({
                success: false,
                message: 'Session ID is required'
            });
        }

        const session = await stripeClient.checkout.sessions.retrieve(sessionId);

        res.json({
            success: true,
            paymentStatus: session.payment_status,
            status: session.status
        });
    } catch (error) {
        console.error('[PAYMENT] Check payment status error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to check payment status'
        });
    }
};

// Get pricing information (public endpoint)
exports.getPrices = async (req, res) => {
    try {
        // Get prices from config
        const stripePrices = {
            lifetime: {
                amount: STRIPE.PRICES.LIFETIME.AMOUNT,
                currency: STRIPE.PRICES.LIFETIME.CURRENCY,
                formatted: (STRIPE.PRICES.LIFETIME.AMOUNT / 100).toFixed(2) // Convert cents to euros
            },
            monthly: {
                amount: STRIPE.PRICES.MONTHLY.AMOUNT,
                currency: STRIPE.PRICES.MONTHLY.CURRENCY,
                formatted: (STRIPE.PRICES.MONTHLY.AMOUNT / 100).toFixed(2) // Convert cents to euros
            }
        };

        // Get OxaPay prices from constants
        const { OXAPAY } = require('../config/constants');
        const oxapayPrices = {
            lifetime: {
                amount: OXAPAY.PRICES.LIFETIME.AMOUNT,
                currency: OXAPAY.PRICES.LIFETIME.CURRENCY,
                formatted: OXAPAY.PRICES.LIFETIME.AMOUNT.toFixed(2)
            },
            monthly: {
                amount: OXAPAY.PRICES.MONTHLY.AMOUNT,
                currency: OXAPAY.PRICES.MONTHLY.CURRENCY,
                formatted: OXAPAY.PRICES.MONTHLY.AMOUNT.toFixed(2)
            }
        };

        res.json({
            success: true,
            prices: {
                stripe: stripePrices,
                oxapay: oxapayPrices
            }
        });
    } catch (error) {
        console.error('[PAYMENT] Get prices error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to fetch prices'
        });
    }
};

