const express = require('express');
const router = express.Router();
const paymentController = require('../controllers/paymentController');

// POST /api/payments/lifetime - Create lifetime checkout session
router.post('/lifetime', paymentController.createLifetimeCheckout);

// POST /api/payments/monthly - Create monthly subscription checkout
router.post('/monthly', paymentController.createMonthlySubscription);

// GET /api/payments/portal - Get customer portal URL
router.get('/portal', paymentController.getCustomerPortal);

// GET /api/payments/status - Check payment status
router.get('/status', paymentController.checkPaymentStatus);

// GET /api/payments/prices - Get pricing information
router.get('/prices', paymentController.getPrices);

// POST /api/payments/webhook - Stripe webhook handler (raw body handled in server.js)
router.post('/webhook', paymentController.handleWebhook);

module.exports = router;

