const express = require('express');
const router = express.Router();
const authRoutes = require('./authRoutes');
const keyRoutes = require('./keyRoutes');
const releaseRoutes = require('./releaseRoutes');
const paymentRoutes = require('./paymentRoutes');
const oxapayRoutes = require('./oxapayRoutes');
const payloadController = require('../controllers/payloadController');

// Mount route modules
router.use('/auth', authRoutes);
router.use('/keys', keyRoutes);
router.use('/releases', releaseRoutes);
router.use('/payments', paymentRoutes);
router.use('/payments/oxapay', oxapayRoutes);

// Public payload download (no auth)
router.get('/payload', payloadController.downloadPayload);

// Health check endpoint
router.get('/health', (req, res) => {
    res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

module.exports = router;


