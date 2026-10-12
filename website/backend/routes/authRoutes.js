const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const discordController = require('../controllers/discordController');

// POST /api/auth/register
router.post('/register', authController.register);

// POST /api/auth/login
router.post('/login', authController.login);

// POST /api/auth/logout
router.post('/logout', authController.logout);

// GET /api/auth/me (get current user)
router.get('/me', authController.getCurrentUser);

// POST /api/auth/reset-hwid (reset HWID - once per month)
router.post('/reset-hwid', authController.resetHWID);

// POST /api/auth/reset-password (reset password)
router.post('/reset-password', authController.resetPassword);

// Discord OAuth routes
router.get('/discord/auth-url', discordController.getDiscordAuthUrl);
router.get('/discord/callback', discordController.discordCallback);
router.get('/discord/status', discordController.getDiscordStatus);
router.post('/discord/unlink', discordController.unlinkDiscord);

// Discord login routes (no auth required)
router.get('/discord/login-url', discordController.getDiscordLoginUrl);
// Login callback now uses the same endpoint as linking callback
router.get('/discord/login-callback', discordController.discordCallback);

// Exchange authorization code for session token (for Discord OAuth)
router.post('/discord/exchange-code', discordController.exchangeAuthCode);

module.exports = router;


