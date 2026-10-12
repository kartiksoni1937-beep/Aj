const express = require('express');
const router = express.Router();
const releaseController = require('../controllers/releaseController');

// Public routes (no auth required)
router.get('/public', releaseController.getAllReleases);
router.get('/public/latest', releaseController.getLatestRelease);

// Protected routes (require auth)
router.get('/', releaseController.getReleases);
router.get('/download/:fileName', releaseController.downloadRelease);

module.exports = router;


