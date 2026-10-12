const fs = require('fs');
const path = require('path');

exports.downloadPayload = (req, res) => {
    try {
        const payloadFileName = 'payload.zip';
        const payloadPath = path.join(__dirname, '../resources', payloadFileName);

        if (!fs.existsSync(payloadPath)) {
            return res.status(404).json({
                success: false,
                message: 'Payload file not found'
            });
        }

        const fileStats = fs.statSync(payloadPath);

        res.setHeader('Content-Type', 'application/zip');
        res.setHeader('Content-Disposition', `attachment; filename="${payloadFileName}"`);
        res.setHeader('Content-Length', fileStats.size);
        res.setHeader('Cache-Control', 'no-cache');

        const fileStream = fs.createReadStream(payloadPath);
        fileStream.pipe(res);

        fileStream.on('error', (error) => {
            console.error('[PAYLOAD] Error streaming payload.zip:', error);
            if (!res.headersSent) {
                res.status(500).json({
                    success: false,
                    message: 'Failed to download payload file'
                });
            } else {
                res.end();
            }
        });

        res.on('close', () => {
            if (!fileStream.destroyed) {
                fileStream.destroy();
            }
        });
    } catch (error) {
        console.error('[PAYLOAD] Download payload error:', error);
        if (!res.headersSent) {
            res.status(500).json({
                success: false,
                message: 'Internal server error'
            });
        }
    }
};
