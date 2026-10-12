const { SlashCommandBuilder, EmbedBuilder, ModalBuilder, TextInputBuilder, TextInputStyle, ActionRowBuilder } = require('discord.js');
const User = require('../../models/User');
const argon2 = require('argon2');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('resetpassword')
        .setDescription('Reset a user\'s password (Admin only)')
        .addSubcommand(subcommand =>
            subcommand
                .setName('username')
                .setDescription('Reset password by username')
                .addStringOption(option =>
                    option
                        .setName('username')
                        .setDescription('Username to reset password for')
                        .setRequired(true)))
        .addSubcommand(subcommand =>
            subcommand
                .setName('discord')
                .setDescription('Reset password by Discord ID')
                .addStringOption(option =>
                    option
                        .setName('discord_id')
                        .setDescription('Discord ID to reset password for')
                        .setRequired(true))),
    
    async execute(interaction) {
        try {
            // Check if user is admin in database
            const adminUser = await User.findOne({ discord_id: interaction.user.id });

            if (!adminUser || !adminUser.admin) {
                return await interaction.reply({
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
                    return await interaction.reply({
                        content: `❌ User with username **${username}** not found.`,
                        flags: 64
                    });
                }
            } else if (subcommand === 'discord') {
                const discordId = interaction.options.getString('discord_id');
                userToReset = await User.findOne({ discord_id: discordId });
                
                if (!userToReset) {
                    return await interaction.reply({
                        content: `❌ User with Discord ID **${discordId}** not found.`,
                        flags: 64
                    });
                }
            }

            // Create modal for new password
            const modal = new ModalBuilder()
                .setCustomId(`resetpassword_modal_${Date.now()}`)
                .setTitle(`Reset Password: ${userToReset.username}`);

            const passwordInput = new TextInputBuilder()
                .setCustomId('new_password')
                .setLabel('New Password')
                .setStyle(TextInputStyle.Short)
                .setPlaceholder('Enter new password (min 6 characters)')
                .setRequired(true)
                .setMinLength(6);

            const confirmPasswordInput = new TextInputBuilder()
                .setCustomId('confirm_password')
                .setLabel('Confirm Password')
                .setStyle(TextInputStyle.Short)
                .setPlaceholder('Confirm new password')
                .setRequired(true)
                .setMinLength(6);

            const firstRow = new ActionRowBuilder().addComponents(passwordInput);
            const secondRow = new ActionRowBuilder().addComponents(confirmPasswordInput);

            modal.addComponents(firstRow, secondRow);

            // Store user data in cache before showing modal
            if (!interaction.client.resetPasswordCache) {
                interaction.client.resetPasswordCache = new Map();
            }
            
            const cacheKey = `resetpassword_${interaction.user.id}_${Date.now()}`;
            interaction.client.resetPasswordCache.set(cacheKey, {
                userId: userToReset._id.toString(),
                username: userToReset.username,
                expiresAt: Date.now() + 300000 // 5 minutes
            });

            // Update modal customId to include cache key
            modal.setCustomId(`resetpassword_modal_${cacheKey}`);

            await interaction.showModal(modal);
        } catch (error) {
            console.error('[BOT] Error in /resetpassword command:', error);
            if (interaction.replied || interaction.deferred) {
                await interaction.editReply({
                    content: '❌ An error occurred while resetting the password.',
                    flags: 64
                });
            } else {
                await interaction.reply({
                    content: '❌ An error occurred while resetting the password.',
                    flags: 64
                });
            }
        }
    },
};

// Handle modal submission separately
async function handleResetPasswordModal(interaction) {
    try {
        const modalCacheKey = interaction.customId.replace('resetpassword_modal_', '');
        const cached = interaction.client.resetPasswordCache?.get(modalCacheKey);

        if (!cached || Date.now() > cached.expiresAt) {
            await interaction.reply({
                content: '❌ Password reset data expired. Please run /resetpassword again.',
                flags: 64
            });
            if (interaction.client.resetPasswordCache) {
                interaction.client.resetPasswordCache.delete(modalCacheKey);
            }
            return;
        }

        const { userId, username } = cached;

        const newPassword = interaction.fields.getTextInputValue('new_password');
        const confirmPassword = interaction.fields.getTextInputValue('confirm_password');

        if (newPassword !== confirmPassword) {
            return await interaction.reply({
                content: '❌ Passwords do not match. Please try again.',
                flags: 64
            });
        }

        if (newPassword.length < 6) {
            return await interaction.reply({
                content: '❌ Password must be at least 6 characters long.',
                flags: 64
            });
        }

        await interaction.deferReply({ flags: 64 });

        // Find and update the user
        const user = await User.findById(userId);

        if (!user) {
            return await interaction.editReply({
                content: '❌ User not found. It may have been deleted.',
                flags: 64
            });
        }

        // Hash new password
        const hashedPassword = await argon2.hash(newPassword);

        // Update password
        await User.findByIdAndUpdate(userId, {
            password: hashedPassword
        });

        const embed = new EmbedBuilder()
            .setColor(0x52A684)
            .setTitle('🔑 Password Reset')
            .addFields(
                {
                    name: 'Username',
                    value: `\`\`\`ini\n[${username}]\n\`\`\``,
                    inline: true
                },
                {
                    name: 'User ID',
                    value: `\`\`\`ini\n[user_${String(userId).substring(0, 6)}]\n\`\`\``,
                    inline: true
                },
                {
                    name: 'Discord',
                    value: `\`\`\`ini\n[${user.discord_username || user.discord_id || 'Not linked'}]\n\`\`\``,
                    inline: true
                },
                {
                    name: 'Status',
                    value: `\`\`\`ini\n[Password reset successfully]\n\`\`\``,
                    inline: false
                }
            )
            .setFooter({ text: `Reset by ${interaction.user.username}`, iconURL: interaction.user.displayAvatarURL() })
            .setTimestamp();

        await interaction.editReply({ embeds: [embed] });

        // Clean up cache
        if (interaction.client.resetPasswordCache) {
            interaction.client.resetPasswordCache.delete(modalCacheKey);
        }
    } catch (error) {
        console.error('[BOT] Error handling reset password modal:', error);
        if (interaction.deferred || interaction.replied) {
            await interaction.editReply({
                content: '❌ An error occurred while processing the password reset.',
                flags: 64
            });
        } else {
            await interaction.reply({
                content: '❌ An error occurred while processing the password reset.',
                flags: 64
            });
        }
    }
}

module.exports.handleResetPasswordModal = handleResetPasswordModal;

