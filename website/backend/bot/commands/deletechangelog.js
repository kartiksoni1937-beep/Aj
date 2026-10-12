const { SlashCommandBuilder } = require('discord.js');
const User = require('../../models/User');
const Release = require('../../models/Release');
const { DISCORD } = require('../../config/constants');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('deletechangelog')
        .setDescription('Delete a release changelog (Admin only)')
        .addStringOption(option =>
            option
                .setName('version')
                .setDescription('Version number to delete (e.g., v2.4.2)')
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
                console.error('[BOT] Error in deletechangelog autocomplete:', error);
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

            await interaction.deferReply({ flags: 64 });

            // Try to delete Discord message if it exists
            if (release.discord_message_id && release.discord_channel_id) {
                try {
                    const releaseChannel = await interaction.client.channels.fetch(release.discord_channel_id);
                    if (releaseChannel) {
                        const message = await releaseChannel.messages.fetch(release.discord_message_id);
                        await message.delete();
                        console.log(`[BOT] Deleted Discord message for release ${version}`);
                    }
                } catch (error) {
                    console.error('[BOT] Error deleting Discord message:', error);
                    // Continue with deletion even if Discord message deletion fails
                }
            }

            // Delete the release from database
            await Release.findByIdAndDelete(release._id);

            await interaction.editReply({
                content: `✅ Successfully deleted release changelog for version ${version}.`,
                flags: 64
            });

            console.log(`[BOT] Release ${version} deleted by ${adminUser.username}`);
        } catch (error) {
            console.error('[BOT] Error in /deletechangelog command:', error);
            if (interaction.replied || interaction.deferred) {
                await interaction.editReply({
                    content: '❌ An error occurred while deleting the changelog.',
                    flags: 64
                });
            } else {
                await interaction.reply({
                    content: '❌ An error occurred while deleting the changelog.',
                    flags: 64
                });
            }
        }
    },
};

