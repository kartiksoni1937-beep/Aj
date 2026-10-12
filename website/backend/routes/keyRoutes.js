const express = require('express');
const router = express.Router();
const keyController = require('../controllers/keyController');

// POST /api/keys/redeem
router.post('/redeem', keyController.redeemKey);

module.exports = router;

