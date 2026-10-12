const { SlashCommandBuilder, EmbedBuilder } = require('discord.js');
const database = require('../../utils/database');
const User = require('../../models/User');
const { FRONTEND_URL } = require('../../config/constants');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('me')
        .setDescription('Shows your account information'),
    
    async execute(interaction) {
        await interaction.deferReply({ flags: 64 }); // 64 = EPHEMERAL

        try {
            // Find user by Discord ID
            const user = await User.findOne({ discord_id: interaction.user.id });

            if (!user) {
                return await interaction.editReply({
                    content: `❌ Your Discord account is not linked to an Echo Client account.\n\nLink your account at: ${FRONTEND_URL}/dashboard`,
                    flags: 64 // EPHEMERAL
                });
            }

            // Check if user is banned
            if (user.banned) {
                return await interaction.editReply({
                    content: `❌ Your account has been banned.\n\n**Reason:** ${user.ban_reason || 'No reason provided'}`,
                    flags: 64
                });
            }

            // Format user data
            const userId = String(user._id || user.id).substring(0, 6);
            const uid = String(user.userNumber || 1).padStart(4, '0');
            const plan = (user.plan || 'N/A').toUpperCase();
            const isLicenseActive = user.expiration_date && new Date(user.expiration_date) > new Date();
            const licenseExpiryTimestamp = isLicenseActive ? Math.floor(new Date(user.expiration_date).getTime() / 1000) : null;
            const registeredTimestamp = Math.floor(new Date(user.createdAt).getTime() / 1000);
            const registeredDate = `<t:${registeredTimestamp}:D>`;
            const licenseExpiryDate = licenseExpiryTimestamp ? `<t:${licenseExpiryTimestamp}:D>` : null;

            const embed = new EmbedBuilder()
                .setColor(0x52A684) // Echo primary color
                .setTitle('Account Information')
                .setThumbnail(interaction.user.displayAvatarURL())
                .setDescription(`**${user.username}**'s Echo Client account details`)
                .addFields(
                    {
                        name: 'Username',
                        value: `\`\`\`ini\n[${user.username}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'User ID',
                        value: `\`\`\`ini\n[user_${userId}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'UID',
                        value: `\`\`\`ini\n[#${uid}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'Plan',
                        value: `\`\`\`ini\n[${plan}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'License Status',
                        value: isLicenseActive 
                            ? `\`\`\`ini\n[Active until ${licenseExpiryDate}]\n\`\`\``
                            : `\`\`\`ini\n[Expired]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'HWID',
                        value: `\`\`\`ini\n[${user.hwid || 'Not Set'}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'Registered',
                        value: registeredDate,
                        inline: true
                    },
                    {
                        name: 'Discord',
                        value: `\`\`\`ini\n[${user.discord_username || 'Not Linked'}]\n\`\`\``,
                        inline: true
                    }
                )
                .setFooter({ text: 'Echo Client', iconURL: interaction.client.user.displayAvatarURL() })
                .setTimestamp();

            await interaction.editReply({ embeds: [embed] });
        } catch (error) {
            console.error('[BOT] Error in /me command:', error);
            await interaction.editReply({
                content: '❌ An error occurred while fetching your account information.',
                flags: 64 // EPHEMERAL
            });
        }
    },
};

