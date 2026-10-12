const { SlashCommandBuilder, EmbedBuilder } = require('discord.js');
const User = require('../../models/User');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('ban')
        .setDescription('Ban a user from Echo Client (Admin only)')
        .addStringOption(option =>
            option
                .setName('username')
                .setDescription('Username to ban')
                .setRequired(true))
        .addStringOption(option =>
            option
                .setName('reason')
                .setDescription('Reason for the ban')
                .setRequired(false)),
    
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

            const username = interaction.options.getString('username');
            const reason = interaction.options.getString('reason') || 'No reason provided';

            // Find user to ban
            const userToBan = await User.findOne({ username: username });

            if (!userToBan) {
                return await interaction.editReply({
                    content: `❌ User **${username}** not found.`,
                    flags: 64
                });
            }

            if (userToBan.banned) {
                return await interaction.editReply({
                    content: `❌ User **${username}** is already banned.`,
                    flags: 64
                });
            }

            if (userToBan.admin) {
                return await interaction.editReply({
                    content: `❌ Cannot ban admin users.`,
                    flags: 64
                });
            }

            // Ban the user
            await User.findByIdAndUpdate(userToBan._id, {
                banned: true,
                ban_reason: reason
            });

            const embed = new EmbedBuilder()
                .setColor(0xFF0000)
                .setTitle('🔨 User Banned')
                .addFields(
                    {
                        name: 'Username',
                        value: `\`\`\`ini\n[${userToBan.username}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'User ID',
                        value: `\`\`\`ini\n[user_${String(userToBan._id).substring(0, 6)}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'Reason',
                        value: `\`\`\`ini\n[${reason}]\n\`\`\``,
                        inline: false
                    }
                )
                .setFooter({ text: `Banned by ${adminUser.username}`, iconURL: interaction.client.user.displayAvatarURL() })
                .setTimestamp();

            await interaction.editReply({ embeds: [embed] });
        } catch (error) {
            console.error('[BOT] Error in /ban command:', error);
            await interaction.editReply({
                content: '❌ An error occurred while banning the user.',
                flags: 64
            });
        }
    },
};

