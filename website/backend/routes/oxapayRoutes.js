const express = require('express');
const router = express.Router();
const oxapayController = require('../controllers/oxapayController');

// POST /api/payments/oxapay/lifetime - Create lifetime invoice
router.post('/lifetime', oxapayController.createLifetimeInvoice);

// POST /api/payments/oxapay/monthly - Create monthly invoice
router.post('/monthly', oxapayController.createMonthlyInvoice);

// POST /api/payments/oxapay/webhook - OxaPay webhook handler (raw body handled in server.js)
router.post('/webhook', oxapayController.handleWebhook);

// GET /api/payments/oxapay/status - Check payment status
router.get('/status', oxapayController.checkPaymentStatus);

module.exports = router;

