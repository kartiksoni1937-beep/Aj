const { SlashCommandBuilder } = require('discord.js');
const discordLogger = require('../../utils/discordLogger');
const User = require('../../models/User');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('showlog')
        .setDescription('View detailed log by ID (shows IP and HWID) - supports auth and download logs')
        .addStringOption(option =>
            option
                .setName('id')
                .setDescription('Log ID to view')
                .setRequired(true)
        ),

    async execute(interaction) {
        const adminUser = await User.findOne({ discord_id: interaction.user.id, admin: true }).lean();
        if (!adminUser) {
            return interaction.reply({
                content: '❌ You do not have permission to use this command. Admin only.',
                ephemeral: true
            });
        }

        const logId = interaction.options.getString('id');
        const entry = discordLogger.getLog(logId);

        if (!entry) {
            return interaction.reply({
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

        return interaction.reply({ embeds: [embed], ephemeral: true });
    }
};

