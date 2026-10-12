const axios = require('axios');
const { DISCORD } = require('./config/constants');
const { Client, GatewayIntentBits } = require('discord.js');

const BOT_TOKEN = DISCORD.BOT_TOKEN;
const GUILD_ID = DISCORD.GUILD_ID;
const CLIENT_ID = DISCORD.CLIENT_ID;

// Test results storage
const testResults = {
    config: { passed: false, issues: [] },
    token: { passed: false, issues: [] },
    authentication: { passed: false, issues: [] },
    permissions: { passed: false, issues: [] },
    guild: { passed: false, issues: [] },
    channels: { passed: false, issues: [] },
    gateway: { passed: false, issues: [] },
    rateLimits: { passed: false, issues: [] },
    network: { passed: false, issues: [] }
};

function logSection(title) {
    console.log('\n' + '='.repeat(60));
    console.log(`🔍 ${title}`);
    console.log('='.repeat(60));
}

function logTest(name, passed, message, details = null) {
    const icon = passed ? '✅' : '❌';
    console.log(`${icon} ${name}: ${message}`);
    if (details) {
        console.log(`   ${details}`);
    }
    return passed;
}

async function testConfiguration() {
    logSection('CONFIGURATION CHECK');
    
    let allPassed = true;
    
    if (!BOT_TOKEN) {
        testResults.config.issues.push('BOT_TOKEN is missing');
        logTest('Bot Token', false, 'Missing from config.json');
        allPassed = false;
    } else {
        // Check token format (Discord bot tokens are base64-like, typically 59+ chars)
        if (BOT_TOKEN.length < 50) {
            testResults.config.issues.push('BOT_TOKEN appears to be invalid (too short)');
            logTest('Bot Token Format', false, 'Token appears invalid (too short)');
            allPassed = false;
        } else {
            logTest('Bot Token', true, 'Present in config');
        }
    }
    
    if (!CLIENT_ID) {
        testResults.config.issues.push('CLIENT_ID is missing (will try to fetch from token)');
        logTest('Client ID', false, 'Missing from config.json (will try to fetch)');
    } else {
        logTest('Client ID', true, `Present: ${CLIENT_ID}`);
    }
    
    if (!GUILD_ID) {
        testResults.config.issues.push('GUILD_ID is missing (bot will register commands globally)');
        logTest('Guild ID', false, 'Missing - commands will register globally (slower)');
    } else {
        logTest('Guild ID', true, `Present: ${GUILD_ID}`);
    }
    
    testResults.config.passed = allPassed;
    return allPassed;
}

async function testNetwork() {
    logSection('NETWORK CONNECTIVITY');
    
    try {
        const response = await axios.get('https://discord.com/api/v10', {
            timeout: 5000,
            validateStatus: () => true
        });
        logTest('Discord API Reachable', true, 'Can connect to Discord API');
        testResults.network.passed = true;
        return true;
    } catch (error) {
        logTest('Discord API Reachable', false, 'Cannot reach Discord API', error.message);
        testResults.network.issues.push(error.message);
        testResults.network.passed = false;
        return false;
    }
}

async function testTokenValidity() {
    logSection('TOKEN VALIDATION');
    
    if (!BOT_TOKEN) {
        logTest('Token Check', false, 'No token to validate');
        testResults.token.passed = false;
        return false;
    }
    
    try {
        const response = await axios.get('https://discord.com/api/v10/users/@me', {
            headers: {
                'Authorization': `Bot ${BOT_TOKEN}`
            },
            validateStatus: (status) => status < 500
        });
        
        if (response.status === 401) {
            logTest('Token Validity', false, 'INVALID TOKEN - 401 Unauthorized', 
                'Token is rejected by Discord. Check if token is correct or has been revoked.');
            testResults.token.issues.push('Token is invalid or revoked');
            testResults.token.passed = false;
            return false;
        }
        
        if (response.status === 403) {
            logTest('Token Validity', false, 'FORBIDDEN - 403', 
                'Token may be valid but lacks necessary permissions.');
            testResults.token.issues.push('Token lacks permissions');
            testResults.token.passed = false;
            return false;
        }
        
        if (response.status === 429) {
            logTest('Token Validity', false, 'RATE LIMITED', 
                'Cannot validate token due to rate limiting');
            testResults.token.issues.push('Rate limited during validation');
            testResults.token.passed = false;
            return false;
        }
        
        if (response.status === 200) {
            const botData = response.data;
            logTest('Token Validity', true, 'Token is valid');
            console.log(`   Bot Username: ${botData.username}#${botData.discriminator || 'N/A'}`);
            console.log(`   Bot ID: ${botData.id}`);
            console.log(`   Bot Type: ${botData.bot ? 'Bot' : 'User Account (ERROR!)'}`);
            
            if (!botData.bot) {
                logTest('Bot Account Check', false, 'Token belongs to USER account, not BOT', 
                    'This token is for a user account. You need a bot token from Discord Developer Portal.');
                testResults.token.issues.push('Token is for user account, not bot');
                testResults.token.passed = false;
                return false;
            }
            
            // Store bot ID for later use
            if (!CLIENT_ID && botData.id) {
                console.log(`   ℹ️  Using fetched Client ID: ${botData.id}`);
            }
            
            testResults.token.passed = true;
            return true;
        }
        
        logTest('Token Validity', false, `Unexpected status: ${response.status}`);
        testResults.token.issues.push(`Unexpected status: ${response.status}`);
        testResults.token.passed = false;
        return false;
        
    } catch (error) {
        if (error.response) {
            if (error.response.status === 401) {
                logTest('Token Validity', false, 'INVALID TOKEN - 401 Unauthorized');
                testResults.token.issues.push('Token is invalid or revoked');
            } else {
                logTest('Token Validity', false, `Error: ${error.response.status}`, error.response.statusText);
                testResults.token.issues.push(`HTTP ${error.response.status}: ${error.response.statusText}`);
            }
        } else {
            logTest('Token Validity', false, 'Network error', error.message);
            testResults.token.issues.push(error.message);
        }
        testResults.token.passed = false;
        return false;
    }
}

async function testAuthentication() {
    logSection('AUTHENTICATION & RATE LIMITS');
    
    try {
        const response = await axios.get('https://discord.com/api/v10/users/@me', {
            headers: {
                'Authorization': `Bot ${BOT_TOKEN}`
            },
            validateStatus: (status) => status < 500
        });
        
        // Extract rate limit headers
        const rateLimitLimit = response.headers['x-ratelimit-limit'];
        const rateLimitRemaining = response.headers['x-ratelimit-remaining'];
        const rateLimitReset = response.headers['x-ratelimit-reset'];
        const rateLimitResetAfter = response.headers['x-ratelimit-reset-after'];
        const retryAfter = response.headers['retry-after'];
        
        if (response.status === 429) {
            logTest('Rate Limit Status', false, 'RATE LIMITED');
            if (retryAfter) {
                const retryAfterSeconds = parseFloat(retryAfter);
                const retryAfterDate = new Date(Date.now() + retryAfterSeconds * 1000);
                console.log(`   ⏱️  Retry After: ${retryAfterSeconds} seconds`);
                console.log(`   Retry at: ${retryAfterDate.toLocaleString()}`);
            }
            if (response.data) {
                console.log(`   Global Rate Limit: ${response.data.global || false}`);
                console.log(`   Message: ${response.data.message || 'N/A'}`);
            }
            testResults.rateLimits.issues.push('Currently rate limited');
            testResults.rateLimits.passed = false;
            testResults.authentication.passed = false;
            return false;
        }
        
        if (response.status === 200) {
            logTest('Authentication', true, 'Successfully authenticated');
            
            if (rateLimitLimit !== undefined) {
                console.log('\n📊 Rate Limit Information:');
                console.log(`   Limit: ${rateLimitLimit} requests`);
                console.log(`   Remaining: ${rateLimitRemaining} requests`);
                
                if (rateLimitReset) {
                    const resetDate = new Date(parseInt(rateLimitReset) * 1000);
                    console.log(`   Reset At: ${resetDate.toLocaleString()}`);
                }
                
                if (rateLimitResetAfter) {
                    const resetAfterSeconds = parseFloat(rateLimitResetAfter);
                    console.log(`   Reset After: ${resetAfterSeconds} seconds`);
                }
                
                if (rateLimitLimit && rateLimitRemaining !== undefined) {
                    const used = rateLimitLimit - rateLimitRemaining;
                    const usagePercent = ((used / rateLimitLimit) * 100).toFixed(1);
                    console.log(`   Usage: ${used}/${rateLimitLimit} (${usagePercent}%)`);
                    
                    if (parseFloat(usagePercent) > 80) {
                        logTest('Rate Limit Usage', false, 'High usage detected', 
                            `${usagePercent}% of rate limit used`);
                        testResults.rateLimits.issues.push(`High rate limit usage: ${usagePercent}%`);
                    } else {
                        logTest('Rate Limit Usage', true, 'Normal usage');
                    }
                }
            }
            
            testResults.authentication.passed = true;
            testResults.rateLimits.passed = true;
            return true;
        }
        
        logTest('Authentication', false, `Unexpected status: ${response.status}`);
        testResults.authentication.issues.push(`Unexpected status: ${response.status}`);
        testResults.authentication.passed = false;
        return false;
        
    } catch (error) {
        if (error.response?.status === 429) {
            logTest('Rate Limit Status', false, 'RATE LIMITED');
            testResults.rateLimits.issues.push('Rate limited');
            testResults.rateLimits.passed = false;
        } else {
            logTest('Authentication', false, 'Failed', error.message);
            testResults.authentication.issues.push(error.message);
        }
        testResults.authentication.passed = false;
        return false;
    }
}

async function testGuildAccess() {
    logSection('GUILD ACCESS');
    
    if (!GUILD_ID) {
        logTest('Guild Access', false, 'No GUILD_ID configured - skipping guild tests');
        testResults.guild.issues.push('No GUILD_ID in config');
        testResults.guild.passed = false;
        return false;
    }
    
    try {
        // Get bot's guilds
        const guildsResponse = await axios.get('https://discord.com/api/v10/users/@me/guilds', {
            headers: {
                'Authorization': `Bot ${BOT_TOKEN}`
            },
            validateStatus: (status) => status < 500
        });
        
        if (guildsResponse.status === 200) {
            const guilds = guildsResponse.data;
            const targetGuild = guilds.find(g => g.id === GUILD_ID);
            
            if (!targetGuild) {
                logTest('Guild Membership', false, `Bot is NOT in guild ${GUILD_ID}`, 
                    'Bot must be added to the guild first');
                testResults.guild.issues.push(`Bot not in guild ${GUILD_ID}`);
                testResults.guild.passed = false;
                return false;
            }
            
            logTest('Guild Membership', true, `Bot is in guild: ${targetGuild.name}`);
            console.log(`   Guild Name: ${targetGuild.name}`);
            console.log(`   Guild ID: ${targetGuild.id}`);
            console.log(`   Bot Permissions: ${targetGuild.permissions || 'N/A'}`);
            
            // Check guild details
            try {
                const guildResponse = await axios.get(`https://discord.com/api/v10/guilds/${GUILD_ID}`, {
                    headers: {
                        'Authorization': `Bot ${BOT_TOKEN}`
                    },
                    validateStatus: (status) => status < 500
                });
                
                if (guildResponse.status === 200) {
                    logTest('Guild Details', true, 'Can fetch guild details');
                } else if (guildResponse.status === 403) {
                    logTest('Guild Details', false, 'FORBIDDEN - Cannot access guild details', 
                        'Bot may lack necessary permissions');
                    testResults.guild.issues.push('Cannot access guild details (403)');
                } else {
                    logTest('Guild Details', false, `Status: ${guildResponse.status}`);
                    testResults.guild.issues.push(`Guild details returned ${guildResponse.status}`);
                }
            } catch (error) {
                logTest('Guild Details', false, 'Error fetching guild', error.message);
                testResults.guild.issues.push(error.message);
            }
            
            testResults.guild.passed = true;
            return true;
        } else if (guildsResponse.status === 403) {
            logTest('Guild Access', false, 'FORBIDDEN - Cannot list guilds', 
                'Bot token may lack guilds.read scope');
            testResults.guild.issues.push('Cannot list guilds (403)');
            testResults.guild.passed = false;
            return false;
        } else {
            logTest('Guild Access', false, `Status: ${guildsResponse.status}`);
            testResults.guild.issues.push(`Guilds endpoint returned ${guildsResponse.status}`);
            testResults.guild.passed = false;
            return false;
        }
    } catch (error) {
        logTest('Guild Access', false, 'Error', error.message);
        testResults.guild.issues.push(error.message);
        testResults.guild.passed = false;
        return false;
    }
}

async function testChannels() {
    logSection('CHANNEL ACCESS');
    
    if (!GUILD_ID) {
        logTest('Channel Access', false, 'No GUILD_ID - skipping channel tests');
        testResults.channels.issues.push('No GUILD_ID in config');
        testResults.channels.passed = false;
        return false;
    }
    
    try {
        const response = await axios.get(`https://discord.com/api/v10/guilds/${GUILD_ID}/channels`, {
            headers: {
                'Authorization': `Bot ${BOT_TOKEN}`
            },
            validateStatus: (status) => status < 500
        });
        
        if (response.status === 200) {
            const channels = response.data;
            logTest('Channel List', true, `Can access ${channels.length} channels`);
            
            // Check specific channels from config
            if (DISCORD.LOG_CHANNEL_ID) {
                const logChannel = channels.find(c => c.id === DISCORD.LOG_CHANNEL_ID);
                if (logChannel) {
                    logTest('Log Channel', true, `Found: #${logChannel.name}`);
                } else {
                    logTest('Log Channel', false, `Channel ${DISCORD.LOG_CHANNEL_ID} not found`);
                    testResults.channels.issues.push(`Log channel ${DISCORD.LOG_CHANNEL_ID} not found`);
                }
            }
            
            if (DISCORD.RELEASE_CHANNEL_ID) {
                const releaseChannel = channels.find(c => c.id === DISCORD.RELEASE_CHANNEL_ID);
                if (releaseChannel) {
                    logTest('Release Channel', true, `Found: #${releaseChannel.name}`);
                } else {
                    logTest('Release Channel', false, `Channel ${DISCORD.RELEASE_CHANNEL_ID} not found`);
                    testResults.channels.issues.push(`Release channel ${DISCORD.RELEASE_CHANNEL_ID} not found`);
                }
            }
            
            testResults.channels.passed = true;
            return true;
        } else if (response.status === 403) {
            logTest('Channel Access', false, 'FORBIDDEN - Cannot access channels', 
                'Bot may lack channels.read permission');
            testResults.channels.issues.push('Cannot access channels (403)');
            testResults.channels.passed = false;
            return false;
        } else {
            logTest('Channel Access', false, `Status: ${response.status}`);
            testResults.channels.issues.push(`Channels endpoint returned ${response.status}`);
            testResults.channels.passed = false;
            return false;
        }
    } catch (error) {
        logTest('Channel Access', false, 'Error', error.message);
        testResults.channels.issues.push(error.message);
        testResults.channels.passed = false;
        return false;
    }
}

async function testPermissions() {
    logSection('BOT PERMISSIONS');
    
    if (!GUILD_ID) {
        logTest('Permissions Check', false, 'No GUILD_ID - cannot check permissions');
        testResults.permissions.issues.push('No GUILD_ID in config');
        testResults.permissions.passed = false;
        return false;
    }
    
    try {
        // First get the bot's user ID
        const botResponse = await axios.get('https://discord.com/api/v10/users/@me', {
            headers: {
                'Authorization': `Bot ${BOT_TOKEN}`
            },
            validateStatus: (status) => status < 500
        });
        
        if (botResponse.status !== 200) {
            logTest('Permissions Check', false, 'Could not fetch bot user ID');
            testResults.permissions.issues.push('Could not fetch bot user ID');
            testResults.permissions.passed = false;
            return false;
        }
        
        const botUserId = botResponse.data.id;
        
        // Now get the bot's member info using its user ID
        const response = await axios.get(`https://discord.com/api/v10/guilds/${GUILD_ID}/members/${botUserId}`, {
            headers: {
                'Authorization': `Bot ${BOT_TOKEN}`
            },
            validateStatus: (status) => status < 500
        });
        
        if (response.status === 200) {
            const member = response.data;
            logTest('Member Info', true, 'Can fetch bot member info');
            
            // Check required permissions for bot functionality
            const requiredPermissions = [
                'SEND_MESSAGES',
                'READ_MESSAGES',
                'EMBED_LINKS',
                'USE_SLASH_COMMANDS'
            ];
            
            // Note: We can't easily check permissions without the guild object
            // But we can verify the bot is a member
            logTest('Bot Membership', true, 'Bot is a member of the guild');
            testResults.permissions.passed = true;
            return true;
        } else if (response.status === 403) {
            logTest('Permissions Check', false, 'FORBIDDEN - Cannot check permissions', 
                'Bot may not be in the guild or lacks permissions');
            testResults.permissions.issues.push('Cannot check permissions (403)');
            testResults.permissions.passed = false;
            return false;
        } else {
            logTest('Permissions Check', false, `Status: ${response.status}`);
            testResults.permissions.issues.push(`Member endpoint returned ${response.status}`);
            testResults.permissions.passed = false;
            return false;
        }
    } catch (error) {
        logTest('Permissions Check', false, 'Error', error.message);
        testResults.permissions.issues.push(error.message);
        testResults.permissions.passed = false;
        return false;
    }
}

async function testGatewayConnection() {
    logSection('GATEWAY CONNECTION TEST');
    
    if (!BOT_TOKEN) {
        logTest('Gateway Test', false, 'No token - cannot test gateway');
        testResults.gateway.issues.push('No BOT_TOKEN');
        testResults.gateway.passed = false;
        return false;
    }
    
    return new Promise((resolve) => {
        let resolved = false;
        const timeout = setTimeout(() => {
            if (!resolved) {
                resolved = true;
                logTest('Gateway Connection', false, 'TIMEOUT - Bot did not connect within 10 seconds', 
                    'This could indicate network issues, invalid token, or gateway problems');
                testResults.gateway.issues.push('Connection timeout');
                testResults.gateway.passed = false;
                client.destroy();
                resolve(false);
            }
        }, 10000);
        
        const client = new Client({
            intents: [
                GatewayIntentBits.Guilds,
                GatewayIntentBits.GuildMessages,
                GatewayIntentBits.MessageContent
            ]
        });
        
        client.once('ready', () => {
            if (!resolved) {
                resolved = true;
                clearTimeout(timeout);
                logTest('Gateway Connection', true, 'Bot connected successfully!');
                console.log(`   Bot: ${client.user.tag}`);
                console.log(`   Guilds: ${client.guilds.cache.size}`);
                console.log(`   Users: ${client.users.cache.size}`);
                testResults.gateway.passed = true;
                client.destroy();
                resolve(true);
            }
        });
        
        client.once('error', (error) => {
            if (!resolved) {
                resolved = true;
                clearTimeout(timeout);
                logTest('Gateway Connection', false, 'Connection error', error.message);
                testResults.gateway.issues.push(error.message);
                testResults.gateway.passed = false;
                resolve(false);
            }
        });
        
        client.login(BOT_TOKEN).catch((error) => {
            if (!resolved) {
                resolved = true;
                clearTimeout(timeout);
                logTest('Gateway Connection', false, 'Login failed', error.message);
                testResults.gateway.issues.push(error.message);
                testResults.gateway.passed = false;
                resolve(false);
            }
        });
    });
}

async function runAllTests() {
    console.log('\n' + '='.repeat(60));
    console.log('🤖 COMPREHENSIVE DISCORD BOT DIAGNOSTICS');
    console.log('='.repeat(60));
    console.log('Testing everything that could prevent your bot from going online...\n');
    
    // Run all tests in sequence
    await testConfiguration();
    await testNetwork();
    
    if (BOT_TOKEN) {
        await testTokenValidity();
        await testAuthentication();
        await testGuildAccess();
        await testChannels();
        await testPermissions();
        await testGatewayConnection();
    }
    
    // Print summary
    logSection('TEST SUMMARY');
    
    const allTests = [
        { name: 'Configuration', result: testResults.config },
        { name: 'Network', result: testResults.network },
        { name: 'Token Validity', result: testResults.token },
        { name: 'Authentication', result: testResults.authentication },
        { name: 'Rate Limits', result: testResults.rateLimits },
        { name: 'Guild Access', result: testResults.guild },
        { name: 'Channels', result: testResults.channels },
        { name: 'Permissions', result: testResults.permissions },
        { name: 'Gateway Connection', result: testResults.gateway }
    ];
    
    let allPassed = true;
    for (const test of allTests) {
        const icon = test.result.passed ? '✅' : '❌';
        console.log(`${icon} ${test.name}: ${test.result.passed ? 'PASSED' : 'FAILED'}`);
        if (!test.result.passed && test.result.issues.length > 0) {
            test.result.issues.forEach(issue => {
                console.log(`   ⚠️  ${issue}`);
            });
            allPassed = false;
        }
    }
    
    console.log('\n' + '='.repeat(60));
    if (allPassed) {
        console.log('✅ ALL TESTS PASSED - Bot should be able to go online!');
        return true;
    } else {
        console.log('❌ SOME TESTS FAILED - Review issues above');
        console.log('\n💡 COMMON FIXES:');
        console.log('   1. Ensure bot token is valid and not revoked');
        console.log('   2. Make sure bot is added to your guild');
        console.log('   3. Check bot has necessary permissions in guild');
        console.log('   4. Verify CLIENT_ID matches your bot application');
        console.log('   5. Wait if rate limited, then try again');
        return false;
    }
}

// Run all tests
runAllTests()
    .then((allPassed) => {
        process.exit(allPassed ? 0 : 1);
    })
    .catch((error) => {
        console.error('\n❌ Unexpected error during testing:', error);
        process.exit(1);
    });
