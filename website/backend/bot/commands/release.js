const { SlashCommandBuilder, EmbedBuilder, ModalBuilder, TextInputBuilder, TextInputStyle, ActionRowBuilder } = require('discord.js');
const User = require('../../models/User');
const Release = require('../../models/Release');
const { DISCORD, FRONTEND_URL } = require('../../config/constants');
const axios = require('axios');
const fs = require('fs');
const path = require('path');
const featureInfoController = require('../../controllers/featureInfoController');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('release')
        .setDescription('Create a new release (Admin only)')
        .addStringOption(option =>
            option
                .setName('version')
                .setDescription('Version number (e.g., v2.4.2)')
                .setRequired(true))
        .addAttachmentOption(option =>
            option
                .setName('file')
                .setDescription('Release file to upload (optional - will use last version if not provided)')
                .setRequired(false))
        .addBooleanOption(option =>
            option
                .setName('reuse_file')
                .setDescription('Reuse the last version\'s file (same as not providing a file)')
                .setRequired(false))
        .addBooleanOption(option =>
            option
                .setName('silent')
                .setDescription('Silent update (no Discord announcement)')
                .setRequired(false))
        .addAttachmentOption(option =>
            option
                .setName('features_file')
                .setDescription('features.json file to upload and reload (optional)')
                .setRequired(false)),
    
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

            const attachment = interaction.options.getAttachment('file');
            const featuresAttachment = interaction.options.getAttachment('features_file');
            const version = interaction.options.getString('version');
            const silent = interaction.options.getBoolean('silent') || false;
            const reuseFile = interaction.options.getBoolean('reuse_file') || false;

            // If no attachment and not reusing file, check if we should reuse last version
            if (!attachment && !reuseFile) {
                return await interaction.reply({
                    content: '❌ Please provide a file attachment or use the `reuse_file` option to use the last version\'s file.',
                    flags: 64
                });
            }

            // Create modal for changelog
            const modal = new ModalBuilder()
                .setCustomId(`release_modal_${Date.now()}`)
                .setTitle('Release Changelog');

            const addedInput = new TextInputBuilder()
                .setCustomId('added')
                .setLabel('Added (one per line)')
                .setStyle(TextInputStyle.Paragraph)
                .setPlaceholder('New feature 1\nNew feature 2')
                .setRequired(false);

            const changedInput = new TextInputBuilder()
                .setCustomId('changed')
                .setLabel('Changed (one per line)')
                .setStyle(TextInputStyle.Paragraph)
                .setPlaceholder('Improved feature 1\nUpdated feature 2')
                .setRequired(false);

            const fixedInput = new TextInputBuilder()
                .setCustomId('fixed')
                .setLabel('Fixed (one per line)')
                .setStyle(TextInputStyle.Paragraph)
                .setPlaceholder('Fixed bug 1\nFixed bug 2')
                .setRequired(false);

            const firstRow = new ActionRowBuilder().addComponents(addedInput);
            const secondRow = new ActionRowBuilder().addComponents(changedInput);
            const thirdRow = new ActionRowBuilder().addComponents(fixedInput);

            modal.addComponents(firstRow, secondRow, thirdRow);

            // Store data in cache before showing modal
            if (!interaction.client.releaseCache) {
                interaction.client.releaseCache = new Map();
            }
            
            const cacheKey = `release_${interaction.user.id}_${Date.now()}`;
            interaction.client.releaseCache.set(cacheKey, {
                attachment,
                featuresAttachment,
                version,
                adminUserId: adminUser._id,
                silent,
                reuseFile: reuseFile || !attachment,
                expiresAt: Date.now() + 300000 // 5 minutes
            });

            // Update modal customId to include cache key
            modal.setCustomId(`release_modal_${cacheKey}`);

            await interaction.showModal(modal);
        } catch (error) {
            console.error('[BOT] Error in /release command:', error);
            if (interaction.replied || interaction.deferred) {
                await interaction.editReply({
                    content: '❌ An error occurred while creating the release.',
                    flags: 64
                });
            } else {
                await interaction.reply({
                    content: '❌ An error occurred while creating the release.',
                    flags: 64
                });
            }
        }
    },
};

// Handle modal submission separately
async function handleReleaseModal(interaction) {
    try {
        const modalCacheKey = interaction.customId.replace('release_modal_', '');
        const cached = interaction.client.releaseCache?.get(modalCacheKey);

        if (!cached || Date.now() > cached.expiresAt) {
            await interaction.reply({
                content: '❌ Release data expired. Please run /release again.',
                flags: 64
            });
            if (interaction.client.releaseCache) {
                interaction.client.releaseCache.delete(modalCacheKey);
            }
            return;
        }

        const { attachment, featuresAttachment, version, adminUserId, silent, reuseFile } = cached;

        const added = interaction.fields.getTextInputValue('added') || '';
        const changed = interaction.fields.getTextInputValue('changed') || '';
        const fixed = interaction.fields.getTextInputValue('fixed') || '';

        // Parse the text inputs into arrays
        const addedList = added.split('\n').filter(line => line.trim()).map(line => line.trim());
        const changedList = changed.split('\n').filter(line => line.trim()).map(line => line.trim());
        const fixedList = fixed.split('\n').filter(line => line.trim()).map(line => line.trim());

        await interaction.deferReply({ flags: 64 });

        // Handle features.json upload if provided
        if (featuresAttachment) {
            // Validate that it's a JSON file
            if (!featuresAttachment.name.toLowerCase().endsWith('.json')) {
                return await interaction.editReply({
                    content: '❌ Features file must be a JSON file (.json extension).',
                    flags: 64
                });
            }

            try {
                // Download the features.json file
                const featuresResponse = await axios.get(featuresAttachment.url, {
                    responseType: 'text'
                });

                // Validate JSON structure
                const parsed = JSON.parse(featuresResponse.data);
                if (!parsed.globalStrings || !Array.isArray(parsed.globalStrings)) {
                    return await interaction.editReply({
                        content: '❌ Invalid features.json structure. Must contain a "globalStrings" array.',
                        flags: 64
                    });
                }

                // Save to resources directory
                const featuresPath = path.join(__dirname, '../../resources/features.json');
                const featuresDir = path.dirname(featuresPath);
                
                if (!fs.existsSync(featuresDir)) {
                    fs.mkdirSync(featuresDir, { recursive: true });
                }

                fs.writeFileSync(featuresPath, JSON.stringify(parsed, null, 2), 'utf8');
                
                // Reload features in the controller
                const reloadSuccess = featureInfoController.reloadFeatures();
                
                if (!reloadSuccess) {
                    return await interaction.editReply({
                        content: '❌ Failed to reload features.json. File was saved but reload failed.',
                        flags: 64
                    });
                }

                console.log(`[BOT] Features.json updated and reloaded by ${interaction.user.tag} (${interaction.user.id})`);
            } catch (error) {
                console.error('[BOT] Error processing features.json:', error);
                return await interaction.editReply({
                    content: `❌ Failed to process features.json: ${error.message}`,
                    flags: 64
                });
            }
        }

        const releasesDir = path.join(__dirname, '../../releases');
        if (!fs.existsSync(releasesDir)) {
            fs.mkdirSync(releasesDir, { recursive: true });
        }

        let fileName, filePath, fileSize, originalFileName;

        // Check if we should reuse the last version's file
        if (reuseFile || !attachment) {
            // Find the last release
            const lastRelease = await Release.findOne({ is_active: true })
                .sort({ createdAt: -1 });

            if (!lastRelease) {
                return await interaction.editReply({
                    content: '❌ No previous release found. Please provide a file attachment for the first release.',
                    flags: 64
                });
            }

            // Extract the actual file path from the last release's file_url
            const lastFileName = lastRelease.file_url.split('/').pop();
            const lastFilePath = path.join(releasesDir, lastFileName);

            if (!fs.existsSync(lastFilePath)) {
                return await interaction.editReply({
                    content: `❌ Previous release file not found: ${lastFileName}. Please provide a new file.`,
                    flags: 64
                });
            }

            // Copy the file with new version name
            const fileExtension = path.extname(lastRelease.file_name);
            const baseFileName = path.basename(lastRelease.file_name, fileExtension);
            originalFileName = lastRelease.file_name;
            fileName = `${version}_${Date.now()}_${lastRelease.file_name}`;
            filePath = path.join(releasesDir, fileName);
            
            // Copy the file
            fs.copyFileSync(lastFilePath, filePath);
            fileSize = fs.statSync(filePath).size;

            console.log(`[BOT] Reusing file from version ${lastRelease.version} for new version ${version}`);
        } else {
            // Download the new file
            const fileResponse = await axios.get(attachment.url, {
                responseType: 'arraybuffer'
            });

            originalFileName = attachment.name;
            fileName = `${version}_${Date.now()}_${attachment.name}`;
            filePath = path.join(releasesDir, fileName);
            fs.writeFileSync(filePath, fileResponse.data);
            fileSize = attachment.size || fileResponse.data.length;
        }

        // Get backend URL for direct download links
        const { BACKEND_URL } = require('../../config/constants');
        
        // Create release in database (keep all versions, don't deactivate old ones)
        const release = new Release({
            version: version,
            file_url: `/api/releases/download/${fileName}`,
            file_name: originalFileName,
            file_size: fileSize,
            added: addedList,
            changed: changedList,
            fixed: fixedList,
            created_by: adminUserId,
            discord_channel_id: DISCORD.RELEASE_CHANNEL_ID || '1426552303455965297',
            is_silent: silent
        });

        await release.save();

        // Only post to Discord if not silent
        if (!silent) {
            // Create embed for Discord
            const embed = new EmbedBuilder()
                .setColor(0x52A684)
                .setTitle(`🚀 New Release: ${version}`)
                .setDescription('A new version of Echo Client is now available!')
                .setTimestamp();

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

            // Send to release channel
            const releaseChannel = await interaction.client.channels.fetch(DISCORD.RELEASE_CHANNEL_ID || '1426552303455965297');
            if (releaseChannel) {
                const message = await releaseChannel.send({ embeds: [embed] });
                release.discord_message_id = message.id;
                await release.save();
            }

            let successMessage = `✅ Release ${version} created successfully! Posted to <#${DISCORD.RELEASE_CHANNEL_ID || '1426552303455965297'}>.`;
            if (featuresAttachment) {
                successMessage += '\n✅ Features.json uploaded and reloaded.';
            }
            await interaction.editReply({
                content: successMessage,
                flags: 64
            });
        } else {
            let successMessage = `✅ Silent release ${version} created successfully! (No Discord announcement)`;
            if (featuresAttachment) {
                successMessage += '\n✅ Features.json uploaded and reloaded.';
            }
            await interaction.editReply({
                content: successMessage,
                flags: 64
            });
        }

        // Clean up cache
        if (interaction.client.releaseCache) {
            interaction.client.releaseCache.delete(modalCacheKey);
        }
    } catch (error) {
        console.error('[BOT] Error handling release modal:', error);
        if (interaction.deferred || interaction.replied) {
            await interaction.editReply({
                content: '❌ An error occurred while processing the release.',
                flags: 64
            });
        } else {
            await interaction.reply({
                content: '❌ An error occurred while processing the release.',
                flags: 64
            });
        }
    }
}

module.exports.handleReleaseModal = handleReleaseModal;
