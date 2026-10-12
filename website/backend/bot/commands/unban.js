const { SlashCommandBuilder, EmbedBuilder } = require('discord.js');
const User = require('../../models/User');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('unban')
        .setDescription('Unban a user from Echo Client (Admin only)')
        .addStringOption(option =>
            option
                .setName('username')
                .setDescription('Username to unban')
                .setRequired(true)),
    
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

            // Find user to unban
            const userToUnban = await User.findOne({ username: username });

            if (!userToUnban) {
                return await interaction.editReply({
                    content: `❌ User **${username}** not found.`,
                    flags: 64
                });
            }

            if (!userToUnban.banned) {
                return await interaction.editReply({
                    content: `❌ User **${username}** is not banned.`,
                    flags: 64
                });
            }

            // Unban the user
            await User.findByIdAndUpdate(userToUnban._id, {
                banned: false,
                ban_reason: null
            });

            const embed = new EmbedBuilder()
                .setColor(0x52A684)
                .setTitle('✅ User Unbanned')
                .addFields(
                    {
                        name: 'Username',
                        value: `\`\`\`ini\n[${userToUnban.username}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'User ID',
                        value: `\`\`\`ini\n[user_${String(userToUnban._id).substring(0, 6)}]\n\`\`\``,
                        inline: true
                    }
                )
                .setFooter({ text: `Unbanned by ${adminUser.username}`, iconURL: interaction.client.user.displayAvatarURL() })
                .setTimestamp();

            await interaction.editReply({ embeds: [embed] });
        } catch (error) {
            console.error('[BOT] Error in /unban command:', error);
            await interaction.editReply({
                content: '❌ An error occurred while unbanning the user.',
                flags: 64
            });
        }
    },
};

