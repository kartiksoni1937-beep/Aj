const mongoose = require('mongoose');
const argon2 = require('argon2');
const { MONGODB_URI } = require('./config/constants');
const User = require('./models/User');

const args = process.argv.slice(2);
let username = '';
let password = '';

for (let i = 0; i < args.length; i++) {
    if (args[i] === '-u') username = args[i + 1];
    if (args[i] === '-p') password = args[i + 1];
}

if (!username || !password) {
    console.log('Usage: node newacc.js -u <username> -p <password>');
    process.exit(1);
}

(async () => {
    try {
        await mongoose.connect(MONGODB_URI);

        // Hash the password using argon2
        // Argon2 already handles salting apparently
        const hashedPassword = await argon2.hash(password);

        const user = new User({
            username,
            password: hashedPassword
        });

        await user.save();

        console.log(`[SUCCESS] Account created for user: ${username}`);
        console.log(`Password has been securely hashed with Argon2.`);

    } catch (err) {
        if (err.code === 11000 || err.name === 'MongoServerError') {
            console.error('[ERROR] Username already exists.');
        } else {
            console.error('[ERROR] Database error:', err.message);
        }
    } finally {
        await mongoose.connection.close();
    }
})();
