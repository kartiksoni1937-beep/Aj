const { EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle } = require('discord.js');
const { DISCORD, FRONTEND_URL } = require('../config/constants');

let client = null;
let cachedLogChannel = null;
const logStore = new Map();
const logOrder = [];
const MAX_LOGS = 1000;

const messageQueue = [];
let isProcessing = false;
let lastSentTime = 0;
const MIN_INTERVAL_MS = 250; // ~4 messages per second (safe rate, Discord allows 5/sec per channel)
const BATCH_DELAY_MS = 1000; // Wait up to 1 second to batch messages together
let batchTimer = null;

function generateLogId() {
    const rand = Math.random().toString(16).slice(2, 8);
    return `${Date.now().toString(36)}-${rand}`;
}

function keepStoreBounded() {
    while (logOrder.length > MAX_LOGS) {
        const oldest = logOrder.shift();
        logStore.delete(oldest);
    }
}

function setClient(discordClient) {
    client = discordClient;
    cachedLogChannel = null;
}

function getLogoUrl() {
    if (!FRONTEND_URL) return null;
    return `${FRONTEND_URL.replace(/\/$/, '')}/logo.png`;
}

async function getLogChannel() {
    if (!client || !DISCORD.LOG_CHANNEL_ID) return null;
    if (cachedLogChannel) return cachedLogChannel;

    try {
        cachedLogChannel = await client.channels.fetch(DISCORD.LOG_CHANNEL_ID);
        return cachedLogChannel;
    } catch (error) {
        console.error('[DISCORD] Unable to fetch log channel:', error.message);
        return null;
    }
}

function buildAuthEmbed(payload) {
    const {
        logId,
        status,
        username,
        uid,
        reason,
        method,
        discordId,
        discordUsername,
        source
    } = payload;

    const embed = new EmbedBuilder()
        .setColor(status === 'success' ? 0x52A684 : 0xC0392B)
        .setTitle(status === 'success' ? 'Login Success' : 'Login Failed')
        .setDescription('Client authentication event')
        .setTimestamp();

    const logoUrl = getLogoUrl();
    if (logoUrl) {
        embed.setThumbnail(logoUrl);
        embed.setFooter({ text: 'Echo Client', iconURL: logoUrl });
    }

    embed.addFields(
        { name: 'Username', value: username ? `\`${username}\`` : 'Unknown', inline: true },
        { name: 'UID', value: uid ? `\`${uid}\`` : 'Unknown', inline: true },
        { name: 'Method', value: method || 'Unknown', inline: true },
        { name: 'Source', value: source || 'websocket', inline: true },
        { name: 'Log ID', value: `\`${logId}\``, inline: false },
    );

    if (discordId || discordUsername) {
        embed.addFields({
            name: 'Discord',
            value: discordUsername
                ? `${discordUsername}${discordId ? ` (${discordId})` : ''}`
                : discordId,
            inline: true,
        });
    } else {
        embed.addFields({ name: 'Discord', value: 'Not linked', inline: true });
    }

    if (reason) {
        embed.addFields({ name: 'Details', value: reason });
    }

    return embed;
}

function buildAuthButton(logId) {
    return new ButtonBuilder()
        .setCustomId(`showlog_${logId}`)
        .setLabel('View Details')
        .setStyle(ButtonStyle.Primary);
}

function buildDownloadButton(logId) {
    return new ButtonBuilder()
        .setCustomId(`showlog_${logId}`)
        .setLabel('View Details')
        .setStyle(ButtonStyle.Primary);
}

function buildDownloadEmbed(payload) {
    const {
        logId,
        username,
        uid,
        plan,
        expiration_date,
        file_name,
        version,
        file_size,
        discordId,
        discordUsername
    } = payload;

    const embed = new EmbedBuilder()
        .setColor(0x52A684)
        .setTitle('File Download')
        .setDescription('Client file download event')
        .setTimestamp();

    const logoUrl = getLogoUrl();
    if (logoUrl) {
        embed.setThumbnail(logoUrl);
        embed.setFooter({ text: 'Echo Client', iconURL: logoUrl });
    }

    // Format file size
    const fileSizeFormatted = file_size ? formatFileSize(file_size) : 'Unknown';

    // Format expiration status
    let planStatus = plan || 'N/A';
    if (plan && plan !== 'N/A') {
        const planLower = String(plan).trim().toLowerCase();
        if (planLower === 'lifetime') {
            planStatus = 'Lifetime';
        } else if (expiration_date) {
            const expDate = new Date(expiration_date);
            const now = new Date();
            if (expDate > now) {
                planStatus = `${plan} (Expires: ${expDate.toLocaleDateString()})`;
            } else {
                planStatus = `${plan} (Expired)`;
            }
        } else {
            planStatus = plan;
        }
    }

    embed.addFields(
        { name: 'Username', value: username ? `\`${username}\`` : 'Unknown', inline: true },
        { name: 'UID', value: uid ? `\`${uid}\`` : 'Unknown', inline: true },
        { name: 'License Plan', value: planStatus, inline: true },
        { name: 'File', value: file_name || 'Unknown', inline: false },
        { name: 'Version', value: version || 'Unknown', inline: true },
        { name: 'File Size', value: fileSizeFormatted, inline: true },
        { name: 'Log ID', value: `\`${logId}\``, inline: false },
    );

    if (discordId || discordUsername) {
        embed.addFields({
            name: 'Discord',
            value: discordUsername
                ? `${discordUsername}${discordId ? ` (${discordId})` : ''}`
                : discordId,
            inline: true,
        });
    } else {
        embed.addFields({ name: 'Discord', value: 'Not linked', inline: true });
    }

    return embed;
}

function formatFileSize(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

function buildDetailedAuthEmbed(entry) {
    const logoUrl = getLogoUrl();
    const embed = new EmbedBuilder()
        .setColor(entry.status === 'success' ? 0x52A684 : 0xC0392B)
        .setTitle(`Auth Log ${entry.logId}`)
        .setDescription('Sensitive details (visible only to you)')
        .addFields(
            { name: 'Status', value: entry.status || 'unknown', inline: true },
            { name: 'Username', value: entry.username ? `\`${entry.username}\`` : 'Unknown', inline: true },
            { name: 'UID', value: entry.uid ? `\`${entry.uid}\`` : 'Unknown', inline: true },
            { name: 'IP', value: entry.ip || 'Unknown', inline: true },
            { name: 'HWID', value: entry.hwid || 'N/A', inline: true },
            { name: 'Method', value: entry.method || 'Unknown', inline: true },
            { name: 'Source', value: entry.source || 'websocket', inline: true },
        )
        .setTimestamp(entry.createdAt ? new Date(entry.createdAt) : null);

    if (entry.discordId || entry.discordUsername) {
        embed.addFields({
            name: 'Discord',
            value: entry.discordUsername
                ? `${entry.discordUsername}${entry.discordId ? ` (${entry.discordId})` : ''}`
                : entry.discordId,
            inline: true
        });
    }

    if (entry.reason) {
        embed.addFields({ name: 'Details', value: entry.reason });
    }

    if (logoUrl) {
        embed.setThumbnail(logoUrl);
        embed.setFooter({ text: 'Echo Client', iconURL: logoUrl });
    }

    return embed;
}

function buildDetailedDownloadEmbed(entry) {
    const logoUrl = getLogoUrl();
    
    // Format file size
    const fileSizeFormatted = entry.file_size ? formatFileSize(entry.file_size) : 'Unknown';

    // Format expiration status
    let planStatus = entry.plan || 'N/A';
    if (entry.plan && entry.plan !== 'N/A') {
        const planLower = String(entry.plan).trim().toLowerCase();
        if (planLower === 'lifetime') {
            planStatus = 'Lifetime';
        } else if (entry.expiration_date) {
            const expDate = new Date(entry.expiration_date);
            const now = new Date();
            if (expDate > now) {
                planStatus = `${entry.plan} (Expires: ${expDate.toLocaleDateString()})`;
            } else {
                planStatus = `${entry.plan} (Expired)`;
            }
        } else {
            planStatus = entry.plan;
        }
    }

    const embed = new EmbedBuilder()
        .setColor(0x52A684)
        .setTitle(`Download Log ${entry.logId}`)
        .setDescription('Sensitive details (visible only to you)')
        .addFields(
            { name: 'Username', value: entry.username ? `\`${entry.username}\`` : 'Unknown', inline: true },
            { name: 'UID', value: entry.uid ? `\`${entry.uid}\`` : 'Unknown', inline: true },
            { name: 'License Plan', value: planStatus, inline: true },
            { name: 'IP', value: entry.ip || 'Unknown', inline: true },
            { name: 'HWID', value: entry.hwid || 'N/A', inline: true },
            { name: 'File', value: entry.file_name || 'Unknown', inline: false },
            { name: 'Version', value: entry.version || 'Unknown', inline: true },
            { name: 'File Size', value: fileSizeFormatted, inline: true },
        )
        .setTimestamp(entry.createdAt ? new Date(entry.createdAt) : null);

    if (entry.discordId || entry.discordUsername) {
        embed.addFields({
            name: 'Discord',
            value: entry.discordUsername
                ? `${entry.discordUsername}${entry.discordId ? ` (${entry.discordId})` : ''}`
                : entry.discordId,
            inline: true
        });
    } else {
        embed.addFields({ name: 'Discord', value: 'Not linked', inline: true });
    }

    if (logoUrl) {
        embed.setThumbnail(logoUrl);
        embed.setFooter({ text: 'Echo Client', iconURL: logoUrl });
    }

    return embed;
}

async function processQueue() {
    if (isProcessing || messageQueue.length === 0) return;
    
    isProcessing = true;
    
    while (messageQueue.length > 0) {
        const now = Date.now();
        const timeSinceLastSent = now - lastSentTime;
        
        // Rate limiting: wait if we sent a message too recently
        if (timeSinceLastSent < MIN_INTERVAL_MS && lastSentTime > 0) {
            await new Promise(resolve => setTimeout(resolve, MIN_INTERVAL_MS - timeSinceLastSent));
        }
        
        const { embed, components, resolve, reject } = messageQueue.shift();
        
        try {
            const channel = await getLogChannel();
            if (!channel) {
                reject(new Error('Channel not available'));
                continue;
            }
            
            await channel.send({ embeds: [embed], components: components || [] });
            lastSentTime = Date.now();
            resolve(true);
        } catch (error) {
            console.error('[DISCORD] Failed to send queued message:', error.message);
            reject(error);
        }
    }
    
    isProcessing = false;
}

function queueMessage(embed, components = null) {
    return new Promise((resolve, reject) => {
        messageQueue.push({ embed, components, resolve, reject });
        
        // Clear any existing batch timer since we have a new message
        if (batchTimer) {
            clearTimeout(batchTimer);
            batchTimer = null;
        }
        
        if (messageQueue.length === 1 && !isProcessing) {
            batchTimer = setTimeout(() => {
                batchTimer = null;
                processQueue();
            }, BATCH_DELAY_MS);
        } else if (!isProcessing) {
            // If queue already has items or we're not processing, start immediately
            processQueue();
        }
    });
}

async function logAuthEvent(payload) {
    try {
        const logId = generateLogId();
        const stored = { ...payload, logId, logType: 'auth', createdAt: Date.now() };
        logStore.set(logId, stored);
        logOrder.push(logId);
        keepStoreBounded();

        const publicPayload = { ...payload, logId };
        const channel = await getLogChannel();
        if (!channel) return false;

        const embed = buildAuthEmbed(publicPayload);
        const button = buildAuthButton(logId);
        const row = new ActionRowBuilder().addComponents(button);
        await queueMessage(embed, [row]);
        return logId;
    } catch (error) {
        console.error('[DISCORD] Failed to queue auth log:', error.message);
        return false;
    }
}

async function logDownloadEvent(payload) {
    try {
        const logId = generateLogId();
        const stored = { ...payload, logId, logType: 'download', createdAt: Date.now() };
        logStore.set(logId, stored);
        logOrder.push(logId);
        keepStoreBounded();

        const publicPayload = { ...payload, logId };
        const channel = await getLogChannel();
        if (!channel) return false;

        const embed = buildDownloadEmbed(publicPayload);
        const button = buildDownloadButton(logId);
        const row = new ActionRowBuilder().addComponents(button);
        await queueMessage(embed, [row]);
        return logId;
    } catch (error) {
        console.error('[DISCORD] Failed to queue download log:', error.message);
        return false;
    }
}

module.exports = {
    setClient,
    logAuthEvent,
    logDownloadEvent,
    getLog: (id) => logStore.get(id) || null,
    buildDetailedAuthEmbed,
    buildDetailedDownloadEmbed,
};

