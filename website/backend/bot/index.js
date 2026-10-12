const { Client, GatewayIntentBits, Collection, REST, Routes } = require('discord.js');
const fs = require('fs');
const path = require('path');
const { DISCORD } = require('../config/constants');

class DiscordBot {
    constructor() {
        this.client = new Client({
            intents: [
                GatewayIntentBits.Guilds,
                GatewayIntentBits.GuildMessages,
                GatewayIntentBits.MessageContent
            ]
        });
        
        this.client.botInstance = this;
        
        this.client.commands = new Collection();
        this.commandsPath = path.join(__dirname, 'commands');
        this.eventsPath = path.join(__dirname, 'events');
        this.isReconnecting = false;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 10;
        this.reconnectDelay = 5000; // Start with 5 seconds
        
        this.setupErrorHandlers();
    }

    setupErrorHandlers() {
        // Handle client errors
        this.client.on('error', (error) => {
            console.error('[BOT] Client error:', error.message);
        });

        // Handle WebSocket errors
        this.client.on('shardError', (error, shardId) => {
            console.error(`[BOT] Shard ${shardId} error:`, error.message);
        });

        // Handle disconnections
        this.client.on('shardDisconnect', (event, shardId) => {
            console.warn(`[BOT] Shard ${shardId} disconnected (code: ${event.code})`);
            this.handleReconnect();
        });

        // Handle reconnecting state
        this.client.on('shardReconnecting', (shardId) => {
            console.log(`[BOT] Shard ${shardId} reconnecting...`);
        });

        // Handle resume (successful reconnect)
        this.client.on('shardResume', (shardId, replayedEvents) => {
            console.log(`[BOT] Shard ${shardId} resumed. Replayed ${replayedEvents} events.`);
            this.reconnectAttempts = 0;
            this.reconnectDelay = 5000;
        });

        // Handle warnings
        this.client.on('warn', (warning) => {
            console.warn('[BOT] Warning:', warning);
        });

        // Handle debug (uncomment for verbose logging)
        // this.client.on('debug', (info) => {
        //     console.debug('[BOT] Debug:', info);
        // });

        // Handle rate limits
        this.client.rest.on('rateLimited', (info) => {
            console.warn(`[BOT] Rate limited: ${info.method} ${info.route} - Retry after ${info.timeToReset}ms`);
        });
    }

    async handleReconnect() {
        if (this.isReconnecting) return;
        
        this.isReconnecting = true;
        this.reconnectAttempts++;

        if (this.reconnectAttempts > this.maxReconnectAttempts) {
            console.error(`[BOT] Max reconnection attempts (${this.maxReconnectAttempts}) reached. Manual restart required.`);
            this.isReconnecting = false;
            return;
        }

        const delay = Math.min(this.reconnectDelay * this.reconnectAttempts, 60000); // Max 60 seconds
        console.log(`[BOT] Attempting reconnection in ${delay / 1000}s (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);

        setTimeout(async () => {
            try {
                // Destroy the old client connection
                this.client.destroy();
                
                // Re-login
                await this.client.login(DISCORD.BOT_TOKEN);
                console.log('[BOT] Reconnected successfully!');
                this.reconnectAttempts = 0;
                this.reconnectDelay = 5000;
            } catch (error) {
                console.error('[BOT] Reconnection failed:', error.message);
            } finally {
                this.isReconnecting = false;
            }
        }, delay);
    }

    async loadCommands() {
        const commandFiles = fs.readdirSync(this.commandsPath).filter(file => file.endsWith('.js'));

        for (const file of commandFiles) {
            const command = require(path.join(this.commandsPath, file));
            if ('data' in command && 'execute' in command) {
                this.client.commands.set(command.data.name, command);
                console.log(`[BOT] Loaded command: ${command.data.name}`);
            } else {
                console.warn(`[BOT] Command ${file} is missing required "data" or "execute" property`);
            }
        }
    }

    async loadEvents() {
        const eventFiles = fs.readdirSync(this.eventsPath).filter(file => file.endsWith('.js'));

        for (const file of eventFiles) {
            const event = require(path.join(this.eventsPath, file));
            if (event.once) {
                this.client.once(event.name, (...args) => event.execute(...args));
            } else {
                this.client.on(event.name, (...args) => event.execute(...args));
            }
            console.log(`[BOT] Loaded event: ${event.name}`);
        }
    }

    async registerSlashCommands() {
        const commands = [];
        const commandFiles = fs.readdirSync(this.commandsPath).filter(file => file.endsWith('.js'));

        for (const file of commandFiles) {
            const command = require(path.join(this.commandsPath, file));
            if ('data' in command) {
                commands.push(command.data.toJSON());
            }
        }

        console.log(`[BOT] Found ${commands.length} commands to register`);

        const rest = new REST().setToken(DISCORD.BOT_TOKEN);

        try {
            console.log('[BOT] Registering slash commands...');

            const clientId = DISCORD.CLIENT_ID || await this.getClientId();
            const guildId = DISCORD.GUILD_ID;

            if (!clientId) {
                throw new Error('CLIENT_ID is not set and could not be fetched');
            }

            console.log(`[BOT] Using CLIENT_ID: ${clientId}`);

            // Register commands globally, actual fix
            const data = await rest.put(
                Routes.applicationCommands(clientId),
                { body: commands }
            );
            console.log(`[BOT] Successfully registered ${data.length} slash commands globally`);
        } catch (error) {
            console.error('[BOT] Error registering commands:', error);
            console.error('[BOT] Error details:', {
                message: error.message,
                status: error.status,
                statusText: error.statusText,
                code: error.code,
                requestData: error.requestData,
                rawError: error.rawError
            });
            throw error;
        }
    }

    async getClientId() {
        try {
            const axios = require('axios');
            const response = await axios.get('https://discord.com/api/v10/users/@me', {
                headers: {
                    'Authorization': `Bot ${DISCORD.BOT_TOKEN}`
                }
            });
            return response.data.id;
        } catch (error) {
            console.error('[BOT] Failed to get client ID:', error.message);
            return null;
        }
    }

    async start() {
        try {
            // Load commands and events
            await this.loadCommands();
            await this.loadEvents();

            await this.client.login(DISCORD.BOT_TOKEN);
            console.log('[BOT] Connected to Discord');
            
            // Reset reconnection state on successful start
            this.reconnectAttempts = 0;
            this.reconnectDelay = 5000;
            this.isReconnecting = false;


        } catch (error) {
            console.error('[BOT] Failed to start:', error.message);
            
            // Attempt reconnection if initial connection fails
            console.log('[BOT] Will attempt to reconnect...');
            this.handleReconnect();
        }
    }

    async stop() {
        this.client.destroy();
        console.log('[BOT] Disconnected from Discord');
    }
}

module.exports = DiscordBot;

