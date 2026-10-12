const { Events } = require('discord.js');
const DiscordBot = require('../index');

module.exports = {
    name: Events.ClientReady,
    once: true,
    async execute(client) {
        console.log(`[BOT] Ready! Logged in as ${client.user.tag}`);
        console.log(`[BOT] Serving ${client.guilds.cache.size} guild(s)`);
        
        const botInstance = client.botInstance;
        if (botInstance && typeof botInstance.registerSlashCommands === 'function') {
            try {
                await botInstance.registerSlashCommands();
            } catch (error) {
                console.error('[BOT] Failed to register commands in ready event:', error);
            }
        }
    },
};

