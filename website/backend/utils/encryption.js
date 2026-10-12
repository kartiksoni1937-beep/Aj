const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

// Load keys
const PRIVATE_KEY_PATH = path.join(__dirname, '../keys/private.pem');
let PRIVATE_KEY;
try {
    PRIVATE_KEY = fs.readFileSync(PRIVATE_KEY_PATH, 'utf8');
} catch (error) {
    console.error(`[CRYPTO] Failed to load private key from ${PRIVATE_KEY_PATH}`);
    console.error('[CRYPTO] RSA keys are required for encryption. Please generate them using:');
    console.error('  openssl genrsa -out keys/private.pem 2048');
    console.error('  openssl rsa -in keys/private.pem -pubout -out keys/public.pem');
    throw new Error('RSA private key not found');
}

class EncryptionUtil {
    /**
     * Decrypts an RSA-encrypted AES key
     * @param {string} encryptedKeyBase64 - The encrypted AES key in base64
     * @returns {Buffer} The decrypted AES key
     */
    static decryptRSA(encryptedKeyBase64) {
        try {
            const buffer = Buffer.from(encryptedKeyBase64, 'base64');
            const decrypted = crypto.privateDecrypt(
                {
                    key: PRIVATE_KEY,
                    padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
                    oaepHash: 'sha1',
                },
                buffer
            );
            return decrypted;
        } catch (error) {
            console.error('[CRYPTO] RSA Decryption failed:', error.message);
            throw new Error('Failed to decrypt session key');
        }
    }

    /**
     * Decrypts AES-GCM encrypted data
     * @param {string} encryptedDataBase64 - The encrypted data in base64
     * @param {Buffer} key - The 32-byte AES key
     * @param {string} ivBase64 - The 12-byte IV in base64
     * @returns {Object} The decrypted JSON object
     */
    static decryptAES(encryptedDataBase64, key, ivBase64) {
        try {
            const iv = Buffer.from(ivBase64, 'base64');
            const encryptedBuffer = Buffer.from(encryptedDataBase64, 'base64');
            
            // In GCM mode, the auth tag is usually appended to the ciphertext or sent separately.
            // Java's GCM implementation typically appends the tag to the end of the ciphertext.
            // Node's crypto.createDecipheriv expects the auth tag to be set separately using setAuthTag.
            // We need to extract the tag from the end of the buffer (last 16 bytes).
            
            const authTagLength = 16;
            const authTag = encryptedBuffer.subarray(encryptedBuffer.length - authTagLength);
            const ciphertext = encryptedBuffer.subarray(0, encryptedBuffer.length - authTagLength);

            const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
            decipher.setAuthTag(authTag);
            
            let decrypted = decipher.update(ciphertext, null, 'utf8');
            decrypted += decipher.final('utf8');
            
            return JSON.parse(decrypted);
        } catch (error) {
            console.error('[CRYPTO] AES Decryption failed:', error.message);
            throw new Error('Failed to decrypt payload');
        }
    }

    /**
     * Encrypts data using AES-GCM
     * @param {Object} data - The JSON object to encrypt
     * @param {Buffer} key - The 32-byte AES key
     * @returns {Object} { iv: string, d: string } - Base64 encoded IV and data (with appended auth tag)
     */
    static encryptAES(data, key) {
        try {
            const iv = crypto.randomBytes(12);
            const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
            
            const jsonStr = JSON.stringify(data);
            let encrypted = cipher.update(jsonStr, 'utf8');
            const final = cipher.final();
            const authTag = cipher.getAuthTag();
            
            // Append auth tag to ciphertext to match Java's behavior
            const combined = Buffer.concat([encrypted, final, authTag]);
            
            return {
                iv: iv.toString('base64'),
                d: combined.toString('base64')
            };
        } catch (error) {
            console.error('[CRYPTO] AES Encryption failed:', error.message);
            throw new Error('Failed to encrypt payload');
        }
    }
}

module.exports = EncryptionUtil;
