const { Events } = require('discord.js');
const { handleReleaseModal } = require('../commands/release');
const { handleEditChangelogModal } = require('../commands/editchangelog');
const { handleResetPasswordModal } = require('../commands/resetpassword');
const discordLogger = require('../../utils/discordLogger');
const User = require('../../models/User');

module.exports = {
    name: Events.InteractionCreate,
    async execute(interaction) {
        // Handle autocomplete interactions
        if (interaction.isAutocomplete()) {
            const command = interaction.client.commands.get(interaction.commandName);
            if (command && command.autocomplete) {
                try {
                    await command.autocomplete(interaction);
                } catch (error) {
                    console.error(`[BOT] Error in autocomplete for ${interaction.commandName}:`, error);
                }
            }
            return;
        }

        // Handle modal submissions
        if (interaction.isModalSubmit()) {
            if (interaction.customId.startsWith('release_modal_')) {
                return await handleReleaseModal(interaction);
            }
            if (interaction.customId.startsWith('editchangelog_modal_')) {
                return await handleEditChangelogModal(interaction);
            }
            if (interaction.customId.startsWith('resetpassword_modal_')) {
                return await handleResetPasswordModal(interaction);
            }
            return;
        }

        // Handle button interactions
        if (interaction.isButton()) {
            if (interaction.customId.startsWith('showlog_')) {
                return await handleShowLogButton(interaction);
            }
            return;
        }

        if (!interaction.isChatInputCommand()) return;

        const command = interaction.client.commands.get(interaction.commandName);

        if (!command) {
            console.error(`[BOT] No command matching ${interaction.commandName} was found.`);
            return;
        }

        try {
            await command.execute(interaction);
        } catch (error) {
            console.error(`[BOT] Error executing ${interaction.commandName}:`, error);
            
            const errorMessage = { content: 'There was an error while executing this command!', flags: 64 }; // 64 = EPHEMERAL
            
            if (interaction.replied || interaction.deferred) {
                await interaction.followUp(errorMessage);
            } else {
                await interaction.reply(errorMessage);
            }
        }
    },
};

async function handleShowLogButton(interaction) {
    try {
        const adminUser = await User.findOne({ discord_id: interaction.user.id, admin: true }).lean();
        if (!adminUser) {
            return await interaction.reply({
                content: '❌ You do not have permission to use this command. Admin only.',
                ephemeral: true
            });
        }

        const logId = interaction.customId.replace('showlog_', '');
        const entry = discordLogger.getLog(logId);

        if (!entry) {
            return await interaction.reply({
                content: `❌ No log found for ID \`${logId}\`.`,
                ephemeral: true
            });
        }

        let embed;
        if (entry.logType === 'download') {
            embed = discordLogger.buildDetailedDownloadEmbed(entry);
        } else {
            embed = discordLogger.buildDetailedAuthEmbed(entry);
        }

        return await interaction.reply({ embeds: [embed], ephemeral: true });
    } catch (error) {
        console.error('[BOT] Error handling showlog button:', error);
        const errorMessage = { 
            content: '❌ An error occurred while retrieving the log.', 
            flags: 64 // EPHEMERAL
        };
        
        if (interaction.replied || interaction.deferred) {
            await interaction.followUp(errorMessage);
        } else {
            await interaction.reply(errorMessage);
        }
    }
}

