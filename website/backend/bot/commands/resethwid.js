const { SlashCommandBuilder, EmbedBuilder } = require('discord.js');
const User = require('../../models/User');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('resethwid')
        .setDescription('Reset a user\'s HWID (Admin only)')
        .addSubcommand(subcommand =>
            subcommand
                .setName('username')
                .setDescription('Reset HWID by username')
                .addStringOption(option =>
                    option
                        .setName('username')
                        .setDescription('Username to reset HWID for')
                        .setRequired(true)))
        .addSubcommand(subcommand =>
            subcommand
                .setName('discord')
                .setDescription('Reset HWID by Discord ID')
                .addStringOption(option =>
                    option
                        .setName('discord_id')
                        .setDescription('Discord ID to reset HWID for')
                        .setRequired(true))),
    
    async execute(interaction) {
        await interaction.deferReply({ flags: 64 }); // EPHEMERAL

        try {
            // Check if user is admin in database
            const adminUser = await User.findOne({ discord_id: interaction.user.id });

            if (!adminUser || !adminUser.admin) {
                return await interaction.editReply({
                    content: '❌ You do not have permission to use this command. Admin access required.',
                    flags: 64
                });
            }

            const subcommand = interaction.options.getSubcommand();
            let userToReset;

            if (subcommand === 'username') {
                const username = interaction.options.getString('username');
                userToReset = await User.findOne({ username: username });
                
                if (!userToReset) {
                    return await interaction.editReply({
                        content: `❌ User with username **${username}** not found.`,
                        flags: 64
                    });
                }
            } else if (subcommand === 'discord') {
                const discordId = interaction.options.getString('discord_id');
                userToReset = await User.findOne({ discord_id: discordId });
                
                if (!userToReset) {
                    return await interaction.editReply({
                        content: `❌ User with Discord ID **${discordId}** not found.`,
                        flags: 64
                    });
                }
            }

            const oldHwid = userToReset.hwid || 'Not set';

            // Reset HWID
            await User.findByIdAndUpdate(userToReset._id, {
                hwid: null,
                last_hwid_reset: new Date()
            });

            const embed = new EmbedBuilder()
                .setColor(0x52A684)
                .setTitle('🔄 HWID Reset')
                .addFields(
                    {
                        name: 'Username',
                        value: `\`\`\`ini\n[${userToReset.username}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'User ID',
                        value: `\`\`\`ini\n[user_${String(userToReset._id).substring(0, 6)}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'Discord',
                        value: `\`\`\`ini\n[${userToReset.discord_username || userToReset.discord_id || 'Not linked'}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'Previous HWID',
                        value: `\`\`\`ini\n[${oldHwid}]\n\`\`\``,
                        inline: false
                    },
                    {
                        name: 'New HWID',
                        value: `\`\`\`ini\n[Reset - User can set new HWID]\n\`\`\``,
                        inline: false
                    }
                )
                .setFooter({ text: `Reset by ${adminUser.username}`, iconURL: interaction.client.user.displayAvatarURL() })
                .setTimestamp();

            await interaction.editReply({ embeds: [embed] });
        } catch (error) {
            console.error('[BOT] Error in /resethwid command:', error);
            await interaction.editReply({
                content: '❌ An error occurred while resetting the HWID.',
                flags: 64
            });
        }
    },
};

