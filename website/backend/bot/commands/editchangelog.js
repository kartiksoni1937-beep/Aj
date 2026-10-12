const { SlashCommandBuilder, EmbedBuilder, ModalBuilder, TextInputBuilder, TextInputStyle, ActionRowBuilder } = require('discord.js');
const User = require('../../models/User');
const Release = require('../../models/Release');
const { DISCORD, FRONTEND_URL } = require('../../config/constants');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('editchangelog')
        .setDescription('Edit changelog for an existing release (Admin only)')
        .addStringOption(option =>
            option
                .setName('version')
                .setDescription('Version number to edit (e.g., v2.4.2)')
                .setRequired(true)
                .setAutocomplete(true)),
    
    async autocomplete(interaction) {
        const focusedOption = interaction.options.getFocused(true);
        
        if (focusedOption.name === 'version') {
            try {
                const input = focusedOption.value.toLowerCase().trim();

                // Get recent releases from database
                const recentReleases = await Release.find({ is_active: true })
                    .sort({ createdAt: -1 })
                    .limit(25)
                    .select('version')
                    .lean();

                // Filter releases that match the input
                const matchingReleases = recentReleases
                    .filter(release => {
                        const versionLower = release.version.toLowerCase();
                        return !input || versionLower.includes(input) || input.includes(versionLower.replace('v', ''));
                    })
                    .slice(0, 25)
                    .map(release => ({
                        name: release.version,
                        value: release.version
                    }));

                // If no input and no releases, return empty array
                await interaction.respond(matchingReleases.length > 0 ? matchingReleases : []);
            } catch (error) {
                console.error('[BOT] Error in editchangelog autocomplete:', error);
                // Return empty array on error
                try {
                    await interaction.respond([]);
                } catch (e) {
                    // Ignore if already responded
                }
            }
        }
    },

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

            const version = interaction.options.getString('version');

            // Find the release
            const release = await Release.findOne({ version: version });

            if (!release) {
                return await interaction.reply({
                    content: `❌ Release with version "${version}" not found.`,
                    flags: 64
                });
            }

            // Create modal for editing changelog
            const modal = new ModalBuilder()
                .setCustomId(`editchangelog_modal_${Date.now()}`)
                .setTitle(`Edit Changelog: ${version}`);

            const addedInput = new TextInputBuilder()
                .setCustomId('added')
                .setLabel('Added (one per line)')
                .setStyle(TextInputStyle.Paragraph)
                .setPlaceholder('New feature 1\nNew feature 2')
                .setValue(release.added ? release.added.join('\n') : '')
                .setRequired(false);

            const changedInput = new TextInputBuilder()
                .setCustomId('changed')
                .setLabel('Changed (one per line)')
                .setStyle(TextInputStyle.Paragraph)
                .setPlaceholder('Improved feature 1\nUpdated feature 2')
                .setValue(release.changed ? release.changed.join('\n') : '')
                .setRequired(false);

            const fixedInput = new TextInputBuilder()
                .setCustomId('fixed')
                .setLabel('Fixed (one per line)')
                .setStyle(TextInputStyle.Paragraph)
                .setPlaceholder('Fixed bug 1\nFixed bug 2')
                .setValue(release.fixed ? release.fixed.join('\n') : '')
                .setRequired(false);

            const firstRow = new ActionRowBuilder().addComponents(addedInput);
            const secondRow = new ActionRowBuilder().addComponents(changedInput);
            const thirdRow = new ActionRowBuilder().addComponents(fixedInput);

            modal.addComponents(firstRow, secondRow, thirdRow);

            // Store release data in cache before showing modal
            if (!interaction.client.editChangelogCache) {
                interaction.client.editChangelogCache = new Map();
            }
            
            const cacheKey = `editchangelog_${interaction.user.id}_${Date.now()}`;
            interaction.client.editChangelogCache.set(cacheKey, {
                releaseId: release._id,
                version: version,
                expiresAt: Date.now() + 300000 // 5 minutes
            });

            // Update modal customId to include cache key
            modal.setCustomId(`editchangelog_modal_${cacheKey}`);

            await interaction.showModal(modal);
        } catch (error) {
            console.error('[BOT] Error in /editchangelog command:', error);
            if (interaction.replied || interaction.deferred) {
                await interaction.editReply({
                    content: '❌ An error occurred while editing the changelog.',
                    flags: 64
                });
            } else {
                await interaction.reply({
                    content: '❌ An error occurred while editing the changelog.',
                    flags: 64
                });
            }
        }
    },
};

// Handle modal submission separately
async function handleEditChangelogModal(interaction) {
    try {
        const modalCacheKey = interaction.customId.replace('editchangelog_modal_', '');
        const cached = interaction.client.editChangelogCache?.get(modalCacheKey);

        if (!cached || Date.now() > cached.expiresAt) {
            await interaction.reply({
                content: '❌ Changelog edit data expired. Please run /editchangelog again.',
                flags: 64
            });
            if (interaction.client.editChangelogCache) {
                interaction.client.editChangelogCache.delete(modalCacheKey);
            }
            return;
        }

        const { releaseId, version } = cached;

        const added = interaction.fields.getTextInputValue('added') || '';
        const changed = interaction.fields.getTextInputValue('changed') || '';
        const fixed = interaction.fields.getTextInputValue('fixed') || '';

        // Parse the text inputs into arrays
        const addedList = added.split('\n').filter(line => line.trim()).map(line => line.trim());
        const changedList = changed.split('\n').filter(line => line.trim()).map(line => line.trim());
        const fixedList = fixed.split('\n').filter(line => line.trim()).map(line => line.trim());

        await interaction.deferReply({ flags: 64 });

        // Find and update the release
        const release = await Release.findById(releaseId);

        if (!release) {
            return await interaction.editReply({
                content: '❌ Release not found. It may have been deleted.',
                flags: 64
            });
        }

        // Update changelog
        release.added = addedList;
        release.changed = changedList;
        release.fixed = fixedList;
        await release.save();

        // Update Discord message if it exists
        if (release.discord_message_id && release.discord_channel_id) {
            try {
                const releaseChannel = await interaction.client.channels.fetch(release.discord_channel_id);
                if (releaseChannel) {
                    const message = await releaseChannel.messages.fetch(release.discord_message_id);
                    
                    // Create updated embed
                    const embed = new EmbedBuilder()
                        .setColor(0x52A684)
                        .setTitle(`🚀 Release: ${version}`)
                        .setDescription('A new version of Echo Client is now available!')
                        .setTimestamp(release.createdAt);

                    if (addedList.length > 0) {
                        embed.addFields({
                            name: '✨ Added',
                            value: `\`\`\`diff\n${addedList.map(item => `+ ${item}`).join('\n')}\n\`\`\``,
                            inline: false
                        });
                    }

                    if (changedList.length > 0) {
                        embed.addFields({
                            name: '🔄 Changed',
                            value: `\`\`\`diff\n${changedList.map(item => `~ ${item}`).join('\n')}\n\`\`\``,
                            inline: false
                        });
                    }

                    if (fixedList.length > 0) {
                        embed.addFields({
                            name: '🐛 Fixed',
                            value: `\`\`\`diff\n${fixedList.map(item => `- ${item}`).join('\n')}\n\`\`\``,
                            inline: false
                        });
                    }

                    embed.addFields({
                        name: '📥 Download',
                        value: `\`\`\`diff\n+ Available in Dashboard\n\`\`\`\n[Click here](${FRONTEND_URL}/dashboard)`,
                        inline: false
                    });

                    await message.edit({ embeds: [embed] });
                }
            } catch (error) {
                console.error('[BOT] Error updating Discord message:', error);
                // Continue even if Discord message update fails
            }
        }

        await interaction.editReply({
            content: `✅ Changelog for version ${version} has been updated successfully!`,
            flags: 64
        });

        // Clean up cache
        if (interaction.client.editChangelogCache) {
            interaction.client.editChangelogCache.delete(modalCacheKey);
        }
    } catch (error) {
        console.error('[BOT] Error handling edit changelog modal:', error);
        if (interaction.deferred || interaction.replied) {
            await interaction.editReply({
                content: '❌ An error occurred while processing the changelog update.',
                flags: 64
            });
        } else {
            await interaction.reply({
                content: '❌ An error occurred while processing the changelog update.',
                flags: 64
            });
        }
    }
}

module.exports.handleEditChangelogModal = handleEditChangelogModal;

