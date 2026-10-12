const { SlashCommandBuilder, EmbedBuilder } = require('discord.js');
const User = require('../../models/User');
const Key = require('../../models/Key');
const crypto = require('crypto');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('genkey')
        .setDescription('Generate license keys (Admin only)')
        .addStringOption(option =>
            option
                .setName('plan')
                .setDescription('License plan type')
                .setRequired(true)
                .addChoices(
                    { name: 'Premium', value: 'premium' },
                    { name: 'Lifetime', value: 'lifetime' }
                ))
        .addIntegerOption(option =>
            option
                .setName('count')
                .setDescription('Number of keys to generate')
                .setRequired(false)
                .setMinValue(1)
                .setMaxValue(10))
        .addIntegerOption(option =>
            option
                .setName('duration')
                .setDescription('Duration in days (ignored for lifetime)')
                .setRequired(false)
                .setMinValue(1)
                .setMaxValue(365)),
    
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

            const plan = interaction.options.getString('plan');
            const count = interaction.options.getInteger('count') || 1;
            const duration = interaction.options.getInteger('duration') || (plan === 'lifetime' ? null : 30);

            const keys = [];

            for (let i = 0; i < count; i++) {
                // Generate a random key: ECHO-XXXX-XXXX-XXXX-XXXX
                const segments = [];
                for (let j = 0; j < 4; j++) {
                    segments.push(crypto.randomBytes(2).toString('hex').toUpperCase());
                }
                const keyString = `ECHO-${segments.join('-')}`;

                const key = new Key({
                    key: keyString,
                    plan: plan,
                    duration_days: plan === 'lifetime' ? null : duration,
                    created_by: adminUser._id
                });

                await key.save();
                keys.push(keyString);
            }

            const embed = new EmbedBuilder()
                .setColor(0x52A684)
                .setTitle('✅ License Keys Generated')
                .setDescription(`Successfully generated **${count}** license key(s)`)
                .addFields(
                    {
                        name: 'Plan',
                        value: `\`\`\`ini\n[${plan.toUpperCase()}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'Duration',
                        value: `\`\`\`ini\n[${plan === 'lifetime' ? 'Lifetime' : `${duration} days`}]\n\`\`\``,
                        inline: true
                    },
                    {
                        name: 'Keys',
                        value: `\`\`\`\n${keys.join('\n')}\n\`\`\``,
                        inline: false
                    }
                )
                .setFooter({ text: 'Echo Client Admin', iconURL: interaction.client.user.displayAvatarURL() })
                .setTimestamp();

            await interaction.editReply({ embeds: [embed] });
        } catch (error) {
            console.error('[BOT] Error in /genkey command:', error);
            await interaction.editReply({
                content: '❌ An error occurred while generating license keys.',
                flags: 64
            });
        }
    },
};

