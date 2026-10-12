package hack.echo.backend.network;

import hack.echo.backend.Backend;
import hack.echo.backend.client.JsonParser;
import hack.echo.backend.config.Config;
import hack.echo.backend.discord.EmbedBuilder;
import hack.echo.backend.logger.Ansi;
import hack.echo.backend.packet.Packet;
import hack.echo.backend.packet.impl.client.*;
import hack.echo.backend.packet.impl.server.*;
import hack.echo.backend.util.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static hack.echo.backend.logger.Ansi.Color.*;
import static hack.echo.backend.util.WebhookUtil.Icon;

public class PacketHandler {

    private static final SecureRandom random = new SecureRandom();

    public static void handlePacket(Packet packet, Client client) {
        switch (packet.getId()) {
            case 0 -> handleHandshakePacket((CHandshake) packet, client);
            case 1 -> handleKeepAlivePacket((CKeepAlivePacket) packet, client);
            case 2 -> handleLoginPacket((CLoginPacket) packet, client);
            case 3 -> handleDownloadPacket((CDownloadPacket) packet, client);
            case 4 -> handleFlagPacket((CFlagPacket) packet, client);
            case 5 -> handleReconnectPacket((CReconnectPacket) packet, client);
            case 6 -> handleConfigPacket((CConfigPacket) packet, client);
            case 7 -> handleSaveConfigPacket((CSaveConfigPacket) packet, client);
            case 8 -> handleDeleteConfigPacket((CDeleteConfigPacket) packet, client);
            case 9 -> handleFeaturesPacket((CFeaturesPacket) packet, client);
            case 10 -> handleKeyExchangePacket((CKeyExchangePacket) packet, client);
            default -> {
                MongoUtil.increaseVL(client);
                client.kick(true);
                MongoUtil.setLastAttempt(client, true);

                Backend.logger.warn(String.format("Received invalid packet with id %s from %s.", new Ansi().c(YELLOW).s(String.valueOf(packet.getId())).r(false).c(BRIGHT_YELLOW), new Ansi().c(YELLOW).s(client.getIp()).r(false).c(BRIGHT_YELLOW)));

                EmbedBuilder embed = new EmbedBuilder();
                embed.setAuthor(String.format("User %s has sent invalid packet", client.getUsername()), "https://i.postimg.cc/MHfxSQWk/block.png");
                StringBuilder builder = new StringBuilder();
                builder.append("**ID**\n> ").append(packet.getId());
                if (client.getHwid() != null) {
                    builder.append("\n**HWID**\n> ").append(client.getHwid());
                }
                builder.append("\n**Username**\n> ").append(client.getUsername());
                String discordId = MongoUtil.getDiscordId(client.getUsername());
                if (discordId != null) {
                    builder.append("\n**Discord**\n> <@").append(discordId).append(">");
                }
                embed.setDescription(builder.toString());
                embed.setColor(new Color(230, 74, 57));
                WebhookUtil.sendWebhook(Backend.getInstance().getConfig().discord.loginWebhook, embed, "@everyone");
            }
        }
    }

    private static void handleHandshakePacket(CHandshake packet, Client client) {
        if (client.isLoggedIn()) {
            MongoUtil.increaseVL(client);
            client.kick(true);
            MongoUtil.setLastAttempt(client, true);

            Backend.logger.info(String.format("User %s has been kicked for sending multiple handshakes", new Ansi().c(WHITE).s(client.getUsername())));
            WebhookUtil.sendEmbed(String.format("User %s has been kicked for sending multiple handshakes", client.getUsername()), "@everyone", Icon.BLOCK, new Color(230, 74, 57), client);
            return;
        }

        client.setEncryptionCKey(packet.getKey());
        client.updateKey(packet.getKey(), true);

        long currentTime = System.currentTimeMillis();
        String sKey = String.format("%s:%s:%s", currentTime, client.getEncryptionSKey(), client.getIp());
        String encryptedSKey = new String(Base64.getEncoder().encode(EncryptionUtil.encrypt(sKey.getBytes(), "um4iE~06^i!?g#3U71IB.f$}wYww)@($:yX70%i+%fXT23 .8cvX/uAm:OWOI:>d", true)));
        client.sendPacket(new SHandshake(encryptedSKey));
        client.setEncryptionSKey(encryptedSKey);
        client.updateKey(encryptedSKey, false);
    }

    private static void handleKeepAlivePacket(CKeepAlivePacket packet, Client client) {
        if (client.getEncryptionCKey().equals(Backend.getInstance().getConfig().encryptionKey)) {
            MongoUtil.increaseVL(client);
            client.kick(true);
            MongoUtil.setLastAttempt(client, true);

            Backend.logger.info(String.format("User %s has been kicked for invalid encryption key", new Ansi().c(WHITE).s(client.getUsername())));
            WebhookUtil.sendEmbed(String.format("User %s has been kicked for invalid encryption key", client.getUsername()), "@everyone", Icon.BLOCK, new Color(230, 74, 57), client);
            return;
        }

        if (!loginCheck(client)) {
            return;
        }

        if ((packet.getValue() != client.getKeepAlive())) {
            MongoUtil.increaseVL(client);
            client.kick(true);
            MongoUtil.setLastAttempt(client, true);

            Backend.logger.info(String.format("User %s has been kicked for invalid keep alive", new Ansi().c(WHITE).s(client.getUsername())));
            WebhookUtil.sendEmbed(String.format("User %s has send invalid keep alive", client.getUsername()), "@everyone", Icon.BLOCK, new Color(230, 74, 57), client);
            return;
        } else if (client.getPrevKeepAlive() == -1) {
            client.setPrevKeepAlive(packet.getValue());
        }

        if (!client.isKeepAliveInit()) {
            long key = random.nextLong();
            long value = (client.getHwid().hashCode() & Integer.MAX_VALUE | ((long) client.getHwid().hashCode() << 32)) ^ key;

            client.setKeepAlive(value);
            client.setKey(key);
            client.sendPacket(new SKeepAlivePacket(value, key));
            client.setKeepAliveInit(true);
            return;
        }

        long value = packet.getValue() ^ client.getKey();
        long key = client.getKey() ^ client.getKey() << 3 ^ client.getUserNameNoIp().hashCode();
        client.setKeepAlive(value);
        client.setKey(key);
        client.sendPacket(new SKeepAlivePacket(value, key));
    }

    private static void handleLoginPacket(CLoginPacket packet, Client client) {
        if (!packet.getUsername().equals("HWID_LOGIN_TEMPLATE")) {
            client.setUsername(packet.getUsername());
        }
        client.setHwid(packet.getHwid());

        User user;
        if (!packet.getUsername().equals("HWID_LOGIN_TEMPLATE")) {
            user = MongoUtil.getUser("username", packet.getUsername());
        } else {
            user = MongoUtil.getUser("hwid", packet.getHwid());
        }

        if (user == null) {
            kick(client, String.format("User %s not found", new Ansi().c(WHITE).s(packet.getUsername())), false);
            WebhookUtil.sendEmbed(String.format("Invalid login user %s not found", packet.getUsername()), Icon.BLOCK, new Color(230, 74, 57), client);
            return;
        }

        if (packet.getUsername().equals("HWID_LOGIN_TEMPLATE")) {
            client.setUsername(user.getRealUsername());
        }

        for (Client client2 : Backend.getInstance().getServer().getClients()) {
            if (client.getUsername() != null && client.getUsername().equals(client2.getUsername()) && client2.isLoggedIn()) {
                if (client.getIp().equals(client2.getIp())) {
                    client2.kick(false);
                } else {
                    MongoUtil.increaseVL(client);
                    client.kick(true);
                    client2.kick(true);
                    MongoUtil.setLastAttempt(client, true);

                    Backend.logger.info(String.format("Kicked existing connection for %s", new Ansi().c(WHITE).s(client2.getUsername())));
                    WebhookUtil.sendEmbed(String.format("Kicked existing connection for %s", client2.getUsername()), "@everyone", Icon.BLOCK, new Color(230, 74, 57), client);
                    return;
                }
            }
        }

        /*if (!user.isBeta()) {
            kick(client, String.format("User %s is not a beta user", packet.getUsername()), false);
            WebhookUtil.sendEmbed(String.format("Invalid login user %s is not a beta user", new Ansi().c(WHITE).s(client.getUsername())), Icon.BLOCK, new Color(230, 74, 57), client);
            return;
        }*/

        if (Backend.getInstance().isMaintenanceMode() && !user.isAdmin()) {
            kick(client, String.format("User %s cannot log in during maintenance", new Ansi().c(WHITE).s(client.getUsername())), false);
            WebhookUtil.sendEmbed(String.format("Invalid login user %s cannot log in during maintenance", client.getUsername()), Icon.BLOCK, new Color(230, 74, 57), client);
            return;
        }

        if (!packet.getUsername().equals("HWID_LOGIN_TEMPLATE") && !Backend.getInstance().getArgon2().matches(packet.getPassword(), user.getPassword())) {
            kick(client, String.format("Invalid password for user %s", new Ansi().c(WHITE).s(client.getUsername())), false);
            MongoUtil.setLastAttempt(client, true);
            WebhookUtil.sendEmbed(String.format("Invalid login password for user %s", client.getUsername()), Icon.BLOCK, new Color(230, 74, 57), client);
            return;
        }

        if (packet.getUsername().equals("HWID_LOGIN_TEMPLATE") && !client.getIp().equals(user.getLastIp())) {
            kick(client, String.format("IP mismatch for user %s", new Ansi().c(WHITE).s(client.getUsername())), false);
            MongoUtil.setLastAttempt(client, true);
            WebhookUtil.sendEmbed(String.format("HWID login attempt with IP mismatch for user %s", client.getUsername()), Icon.BLOCK, new Color(230, 74, 57), client, String.format("**Expected IP**\n> %s\n**Actual IP**\n> %s", user.getLastIp(), client.getIp()));
            return;
        }

        if (user.getHwid() == null || user.getHwid().isEmpty()) {
            MongoUtil.setHWID("username", client.getUsername(), packet.getHwid());
            Backend.logger.info(String.format("Set HWID for user %s", new Ansi().c(WHITE).s(client.getUsername())));
            WebhookUtil.sendEmbed(String.format("Set HWID for user %s", client.getUsername()), Icon.DATABASE_SUCCESS, new Color(46, 204, 116), client, String.format("**HWID**\n> %s", packet.getHwid()));
        } else if (!user.getHwid().equals(packet.getHwid())) {
            kick(client, String.format("HWID mismatch for user %s", new Ansi().c(WHITE).s(client.getUsername())), false);
            MongoUtil.setLastAttempt(client, true);
            WebhookUtil.sendEmbed(String.format("HWID mismatch for user %s", client.getUsername()), Icon.BLOCK, new Color(230, 74, 57), client);
            return;
        }

        if (user.isBanned()) {
            kick(client, String.format("User %s is banned", new Ansi().c(WHITE).s(client.getUsername())), true);
            MongoUtil.setLastAttempt(client, true);
            WebhookUtil.sendEmbed(String.format("Invalid login user %s is banned", client.getUsername()), Icon.BLOCK, new Color(230, 74, 57), client);
            return;
        }

        List<User> users = MongoUtil.getUsers();
        for (User u : users) {
            if (u.isBanned() && u.getHwid() != null && u.getHwid().equals(packet.getHwid())) {
                kick(client, String.format("HWID %s is banned", new Ansi().c(WHITE).s(packet.getHwid())), true);
                WebhookUtil.sendEmbed(String.format("Invalid login HWID %s is banned", packet.getHwid()), Icon.BLOCK, new Color(230, 74, 57), client, String.format("**User**\n> %s\n**IP**\n> %s", client.getUsername(), client.getIp()));
                return;
            }
        }

        if (packet.getUsername().equals("HWID_LOGIN_TEMPLATE")) {
            client.setHWIDLogin(true);
        }

        if (client.getIp().equals(user.getLastIp())) {
            Backend.logger.info(String.format("User %s has logged in", new Ansi().c(WHITE).s(client.getUsername())));
            WebhookUtil.sendEmbed(String.format("User %s has logged in", client.getUsername()), Icon.CHECKMARK, new Color(46, 204, 116), client);
        } else {
            Backend.logger.info(String.format("User %s has logged in from new IP", new Ansi().c(WHITE).s(client.getUsername())));
            WebhookUtil.sendEmbed(String.format("User %s has logged in from new IP", client.getUsername()), Icon.DATABASE_SUCCESS, new Color(46, 204, 116), client);
        }

        int serverInteger = random.nextInt(0, 0x7fffffff);
        int secretLocalKey = random.nextInt(0, 0x7fffffff);
        int runtimeConstant = (serverInteger ^ secretLocalKey) + 21332;

        client.sendPacket(new SLoginPacket(packet.getUsername(), BCryptUtil.hashpw(packet.getPassword(), BCryptUtil.gensalt()), packet.getHwid(), String.valueOf(user.getUserNumber()), System.currentTimeMillis(), generateSessionToken(String.valueOf(user.getUserNumber()), client.getHwid()), false, serverInteger, secretLocalKey, runtimeConstant));
        client.setLoggedIn(true);
        MongoUtil.setLastAttempt(client, false);
    }

    private static void handleDownloadPacket(CDownloadPacket ignored, Client client) {
        if (!loginCheck(client)) {
            return;
        }

        Config config = Backend.getInstance().getConfig();
        File jarFile = new File(config.clientJar);
        if (!jarFile.exists() || !jarFile.isFile()) {
            Backend.logger.fatal(String.format("Jar file not found at path: %s", new Ansi().c(RED).s(config.clientJar)));
            WebhookUtil.sendEmbed(String.format("Jar file not found at path: %s", config.clientJar), "@everyone", Icon.FATAL, new Color(231, 76, 60), client);
            client.kick(true);
            System.exit(632);
        } else {
            try {
                ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(jarFile.toPath()));
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
                String key = randomKey(16);
                for (ZipEntry entry = zipInputStream.getNextEntry(); entry != null; entry = zipInputStream.getNextEntry()) {
                    zipOutputStream.putNextEntry(new ZipEntry(entry.getName()));
                    ByteArrayOutputStream entryBytes = new ByteArrayOutputStream();
                    try {
                        zipInputStream.transferTo(entryBytes);
                    } catch (Exception e) {
                        Backend.logger.error("Failed to read jar entry", e);
                        WebhookUtil.sendEmbed("Failed to read jar entry", "@everyone", Icon.FATAL, new Color(231, 76, 60), client);
                        client.kick(true);
                        System.exit(634);
                    }
                    byte[] encryptedBytes = EncryptionUtil.encrypt(entryBytes.toByteArray(), key, false);
                    zipOutputStream.write(encryptedBytes);
                    zipOutputStream.closeEntry();
                }
                byte[] encryptedJar = EncryptionUtil.encrypt(byteArrayOutputStream.toByteArray(), key, true);
                client.sendPacket(new SJarPacket(encryptedJar, key));
                zipOutputStream.close();
                zipInputStream.close();
            } catch (Exception e) {
                Backend.logger.error("Failed to read jar file", e);
                WebhookUtil.sendEmbed("Failed to read jar file", "@everyone", Icon.FATAL, new Color(231, 76, 60), client);
                client.kick(true);
                System.exit(633);
            }
        }
    }

    private static void handleFlagPacket(CFlagPacket packet, Client client) {
        User user = MongoUtil.increaseVL(client);
        client.kick(true);

        if (user != null) {
            if (!user.isBanned()) {
                Backend.logger.info(String.format("Client %s has been flagged with id %s", new Ansi().c(WHITE).s(client.getUsername()),  new Ansi().c(WHITE).s(packet.getFlag())));
                WebhookUtil.sendEmbed(String.format("User %s has been flagged", client.getUsername()), "@everyone", Icon.BLOCK, new Color(230, 74, 57), client, String.format("**Flag**\n> %s\nFlag ID\n> %s", FlagUtil.resolve(packet.getFlag()), packet.getFlag()));
            } else {
                Backend.logger.info(String.format("Banned client %s has been flagged with id %s", new Ansi().c(WHITE).s(client.getUsername()),  new Ansi().c(WHITE).s(packet.getFlag())));
                WebhookUtil.sendEmbed(String.format("Banned user %s has been flagged", client.getUsername()), Icon.BLOCK, new Color(230, 74, 57), client, String.format("**Flag**\n> %s\nFlag ID\n> %s", FlagUtil.resolve(packet.getFlag()), packet.getFlag()));
            }
        } else {
            Backend.logger.info(String.format("Unknown client with IP %s has been flagged with id %s", new Ansi().c(WHITE).s(client.getIp()), new Ansi().c(WHITE).s(packet.getFlag())));
            WebhookUtil.sendEmbed(String.format("Unknown user with IP %s has been flagged", client.getIp()), Icon.BLOCK, new Color(230, 74, 57), client, String.format("**Flag**\n> %s\nFlag ID\n> %s", FlagUtil.resolve(packet.getFlag()), packet.getFlag()));
        }
    }

    private static void handleReconnectPacket(CReconnectPacket packet, Client client) {
        int result = verifySessionToken(packet.getSessionToken(), packet.getHwid()).status;
        client.setHwid(packet.getHwid());
        switch (result) {
            case 0, 1, 2, 3 -> {
                client.kick(true);
                MongoUtil.setLastAttempt(client, true);
                User user = MongoUtil.increaseVL(client);
                if (user != null) {
                    client.setUsername(user.getRealUsername());
                }

                String reason = switch (result) {
                    case 0 -> "Invalid session token";
                    case 1 -> "invalid signature";
                    case 2 -> "expired token";
                    default -> "malformed token";
                };
                Backend.logger.info(String.format("User %s has been kicked for %s", new Ansi().c(WHITE).s(client.getUsername()), reason));
                WebhookUtil.sendEmbed(String.format("User %s has been kicked for %s", client.getUsername(), reason), "@everyone", Icon.BLOCK, new Color(230, 74, 57), client);
            }
            case 4 -> {
                User user = MongoUtil.getUser("hwid", packet.getHwid());
                if (user == null) {
                    MongoUtil.increaseVL(client);
                    client.kick(true);
                    MongoUtil.setLastAttempt(client, true);

                    Backend.logger.info(String.format("User %s has been kicked for invalid HWID during reconnect", new Ansi().c(WHITE).s(client.getUsername())));
                    WebhookUtil.sendEmbed(String.format("User %s has been kicked for invalid HWID during reconnect", client.getUsername()), "@everyone", Icon.BLOCK, new Color(230, 74, 57), client);
                    return;
                }

                ConnectionCache cache = null;
                for (ConnectionCache connection : Backend.getInstance().getServer().getCache()) {
                    if (connection.getAdded() < System.currentTimeMillis() - 86400000) {
                        Backend.getInstance().getServer().getCache().remove(connection);
                        continue;
                    }

                    if (client.getHwid().equals(connection.getHwid())) {
                        cache = connection;
                    }
                }

                if (cache == null) {
                    //MongoUtil.increaseVL(client);
                    client.kick(true);
                    MongoUtil.setLastAttempt(client, true);

                    Backend.logger.info(String.format("User %s has been kicked for missing connection cache during reconnect", new Ansi().c(WHITE).s(client.getUsername())));
                    WebhookUtil.sendEmbed(String.format("User %s has been kicked for missing connection cache during reconnect", client.getUsername()), "@everyone", Icon.BLOCK, new Color(230, 74, 57), client);
                    return;
                }
                for (Client client2 : Backend.getInstance().getServer().getClients()) {
                    if (client.getUsername() != null && client.getUsername().equals(client2.getUsername()) && client2.isLoggedIn() && client.getIp().equals(client2.getIp())) {
                        client2.kick(false);
                    }
                }

                client.sendPacket(new SReconnectPacket(packet.getSessionToken(), packet.getHwid(), String.valueOf(user.getUserNumber()), System.currentTimeMillis(), user.isBanned()));
                client.setEncryptionCKey(new String(cache.getCSecretKey().getEncoded()));
                client.setUsername(user.getRealUsername());
                client.setLoggedIn(true);
                client.setSentFeatures(true);

                client.setCSecretKey(cache.getCSecretKey());
                client.setSSecretKey(cache.getSSecretKey());

                Backend.logger.info(String.format("User %s has reconnected successfully", new Ansi().c(WHITE).s(client.getUsername())));
                WebhookUtil.sendEmbed(String.format("User %s has reconnected successfully", client.getUsername()), Icon.CHECKMARK, new Color(46, 204, 116), client);
            }
        }
    }

    private static void handleConfigPacket(CConfigPacket packet, Client client) {
        if (!loginCheck(client)) {
            return;
        }

        try {
            File configFile = new File(Backend.getInstance().getConfig().configPath, String.format("%s/%s", client.getUsername(), packet.getConfigName()));
            if (!configFile.exists() || !configFile.isFile()) {
                client.sendPacket(new SConfigPacket(null));
                return;
            }
            client.sendPacket(new SConfigPacket(Files.readAllBytes(configFile.toPath())));
        } catch (Exception e) {
            Backend.logger.error(String.format("Failed to load config for user %s", new Ansi().c(RED).s(client.getUsername())), e);
        }
    }

    private static void handleSaveConfigPacket(CSaveConfigPacket packet, Client client) {
        if (!loginCheck(client)) {
            return;
        }

        Config config = Backend.getInstance().getConfig();
        try {
            File userDir = new File(config.configPath, client.getUsername());
            if (!userDir.exists()) {
                if (!userDir.mkdirs()) {
                    Backend.logger.error(String.format("Failed to create config directory for user %s", new Ansi().c(RED).s(client.getUsername())));
                    return;
                }
            }
            Files.write(new File(config.configPath, String.format("%s/%s", client.getUsername(), packet.getConfigName())).toPath(), JsonParser.serializeConfig(packet.getConfigData()));
        } catch (Exception e) {
            Backend.logger.error(String.format("Failed to create config directory for user %s", new Ansi().c(RED).s(client.getUsername())), e);
        }
    }

    private static void handleDeleteConfigPacket(CDeleteConfigPacket packet, Client client) {
        if (!loginCheck(client)) {
            return;
        }

        Config config = Backend.getInstance().getConfig();
        try {
            File configFile = new File(config.configPath, String.format("%s/%s", client.getUsername(), packet.getConfigName()));
            if (configFile.exists() && configFile.isFile()) {
                if (!configFile.delete()) {
                    Backend.logger.error(String.format("Failed to delete config file for user %s", new Ansi().c(RED).s(client.getUsername())));
                    return;
                }
                client.sendPacket(new SDeleteConfigPacket());
            }
        } catch (Exception e) {
            Backend.logger.error(String.format("Failed to delete config file for user %s", new Ansi().c(RED).s(client.getUsername())), e);
        }
    }

    private static void handleFeaturesPacket(CFeaturesPacket ignore, Client client) {
        if (!loginCheck(client)) {
            return;
        }

        client.setTimeout(30000);

        Config config = Backend.getInstance().getConfig();
        String key = String.format("%s#%s#%s#%s", client.isHWIDLogin() ? "HWID_LOGIN_TEMPLATE" : client.getUserNameNoIp(), client.getHwid(), config.encryptionKey, client.getKey());
        byte[] cKeyBytes = key.getBytes();
        String encryptedKey = Base64.getEncoder().encodeToString(cKeyBytes);
        client.setEncryptionCKey(encryptedKey);
        client.updateKey(encryptedKey, true);
        String sKey = randomKey(encryptedKey.length());
        client.sendPacket(new SKeyExchangePacket(sKey));
        client.setEncryptionSKey(sKey);
        client.updateKey(sKey, false);

        Backend.logger.info(String.format("Cache for user %s has been created.", new Ansi().c(WHITE).s(client.getUsername())));
        Backend.getInstance().getServer().getCache().add(new ConnectionCache(client.getHwid(), client.getCSecretKey(), client.getSSecretKey()));

        try {
            File file = new File(config.featuresJson);
            if (!file.exists() || !file.isFile()) {
                Backend.logger.fatal(String.format("Features file not found at path: %s", new Ansi().c(RED).s(config.featuresJson)));
                WebhookUtil.sendEmbed(String.format("Features file not found at path: %s", config.featuresJson), "@everyone", Icon.FATAL, new Color(231, 76, 60), client);
                client.kick(true);
                System.exit(635);
            }

            Thread.sleep(100);
            client.sendPacket(new SFeaturesPacket(JsonParser.serializeFeatures(Files.readString(file.toPath()))));
            client.setSentFeatures(true);
        } catch (Exception e) {
            Backend.logger.error(String.format("Failed to load features for user %s", new Ansi().c(RED).s(client.getUsername())), e);
        }
    }

    private static void handleKeyExchangePacket(CKeyExchangePacket packet, Client client) {
        if (!loginCheck(client)) {
            return;
        }

        if (!client.isSentFeatures()) {
            return;
        }

        Config config = Backend.getInstance().getConfig();
        String cKey = String.format("%s&%s&%s&%s&client", client.isHWIDLogin() ? "HWID_LOGIN_TEMPLATE" : client.getUserNameNoIp(), client.getHwid(), config.encryptionKey, client.getKey());
        String encryptedCKey = new String(EncryptionUtil.encrypt(cKey.getBytes(StandardCharsets.UTF_8), client.getEncryptionCKey(), true));
        if (!encryptedCKey.equals(packet.getKey())) {
            MongoUtil.increaseVL(client);
            client.kick(true);
            MongoUtil.setLastAttempt(client, true);

            Backend.logger.info(String.format("User %s has been kicked for invalid key exchange", new Ansi().c(WHITE).s(client.getUsername())));
            WebhookUtil.sendEmbed(String.format("User %s has been kicked for invalid key exchange", client.getUsername()), "@everyone", Icon.BLOCK, new Color(230, 74, 57), client);
        }

        client.setEncryptionCKey(encryptedCKey);
        client.updateKey(encryptedCKey, true);
        String sKey = String.format("%s&%s&%s&%s&server", client.isHWIDLogin() ? "HWID_LOGIN_TEMPLATE" : client.getUserNameNoIp(), client.getHwid(), config.encryptionKey, client.getKey());
        String encryptedSKey = new String(EncryptionUtil.encrypt(sKey.getBytes(StandardCharsets.UTF_8), client.getEncryptionSKey(), true));
        client.setEncryptionSKey(encryptedSKey);
        client.updateKey(encryptedSKey, false);
    }

    private static boolean loginCheck(Client client) {
        if (!client.isLoggedIn()) {
            MongoUtil.increaseVL(client);
            client.kick(true);
            MongoUtil.setLastAttempt(client, true);

            Backend.logger.info(String.format("User %s has been kicked for not being logged in", new Ansi().c(WHITE).s(client.getUsername())));
            WebhookUtil.sendEmbed(String.format("User %s has been kicked for not being logged in", client.getUsername()), "@everyone", Icon.BLOCK, new Color(230, 74, 57), client);
            return false;
        }
        return true;
    }

    public static String generateSessionToken(String uid, String hwid) {
        try {
            byte[] randomBytes = new byte[16];
            random.nextBytes(randomBytes);
            String randomHex = WebUtil.bytesToHex(randomBytes);

            String data = String.format("%s.%s.%s.%s", uid, System.currentTimeMillis(), randomHex, hwid);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(Backend.getInstance().getConfig().secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            String combined = String.format("%s.%s", data, WebUtil.bytesToHex(signatureBytes));
            return Base64.getEncoder().encodeToString(combined.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Backend.logger.error("Failed to generate session token", e);
            return null;
        }
    }

    public static WebUtil.SessionInfo verifySessionToken(String token, String hwid) {
        try {
            Config config = Backend.getInstance().getConfig();
            byte[] decodedBytes = Base64.getDecoder().decode(token);
            String decoded = new String(decodedBytes, StandardCharsets.UTF_8);

            String[] parts = decoded.split("\\.");
            if (parts.length != 5) return new WebUtil.SessionInfo(0, -1, -1);

            String data = String.format("%s.%s.%s.%s", parts[0], parts[1], parts[2], hwid);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(config.secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            if (!WebUtil.bytesToHex(signatureBytes).equals(parts[4])) return new WebUtil.SessionInfo(1, -1, -1);

            if (System.currentTimeMillis() - Long.parseLong(parts[1]) > config.tokenExpiry) return new WebUtil.SessionInfo(2, -1, -1);

            return new WebUtil.SessionInfo(4, Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception e) {
            return new WebUtil.SessionInfo(3, -1, -1);
        }
    }

    private static String randomKey(int length) {
        String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(characters.length());
            sb.append(characters.charAt(index));
        }
        return sb.toString();
    }

    public static void kick(Client client, String reason, boolean banned) {
        Backend.logger.info(reason);
        client.sendPacket(new SLoginPacket("", "", "", "", 0, "", banned, 0, 0, 0));
        Backend.getInstance().getServer().kickClient(client.getUsername(), false);
    }
}
