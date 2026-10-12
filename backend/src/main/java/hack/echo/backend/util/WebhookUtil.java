package hack.echo.backend.util;

import hack.echo.backend.Backend;
import hack.echo.backend.discord.EmbedBuilder;
import hack.echo.backend.discord.WebhookBuilder;
import hack.echo.backend.network.Client;
import hack.echo.backend.network.User;
import lombok.Getter;
import org.bson.Document;

import java.awt.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class WebhookUtil {

    public static void sendWebhook(String url, EmbedBuilder embed) {
        if (url == null || url.isEmpty()) {
            return;
        }

        WebhookBuilder webhook = new WebhookBuilder(url);
        webhook.setName("Echo");
        webhook.setAvatarUrl("https://cdn.discordapp.com/avatars/1447930965174390804/49c35c8d942d2dd242ece0d192d4a9bf.webp?size=128");
        webhook.addEmbed(embed);
        webhook.send();
    }

    public static void sendWebhook(String url, EmbedBuilder embed, String content) {
        if (url == null || url.isEmpty()) {
            return;
        }

        WebhookBuilder webhook = new WebhookBuilder(url);
        webhook.setName("Echo");
        webhook.setAvatarUrl("https://cdn.discordapp.com/avatars/1447930965174390804/49c35c8d942d2dd242ece0d192d4a9bf.webp?size=128");
        webhook.addEmbed(embed);
        webhook.setContent(content);
        webhook.send();
    }

    public static void sendEmbed(String message, String message2, Icon icon, Color color, Client client, String append) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor(message, icon.getUrl());
        StringBuilder builder = new StringBuilder();
        builder.append("**HWID**\n> ").append(client.getHwid());
        String discordId = MongoUtil.getDiscordId(client.getUsername());
        if (discordId != null) {
            builder.append("\n**Discord**\n> <@").append(discordId).append(">");
        }
        if (append != null) {
            builder.append("\n").append(append);
        }
        embed.setDescription(builder.toString());
        embed.setColor(color);
        if (message2 != null) {
            sendWebhook(Backend.getInstance().getConfig().discord.loginWebhook, embed, message2);
        } else {
            sendWebhook(Backend.getInstance().getConfig().discord.loginWebhook, embed);
        }
    }

    public static void sendEmbed(String message, String message2, Icon icon, Color color, Client client) {
        sendEmbed(message, message2, icon, color, client, null);
    }

    public static void sendEmbed(String message, Icon icon, Color color, Client client, String append) {
        sendEmbed(message, null, icon, color, client, append);
    }

    public static void sendEmbed(String message, Icon icon, Color color, Client client) {
        sendEmbed(message, null, icon, color, client);
    }

    public static boolean logDownloadEvent(User user, Document release, long fileSize, String ip, String hwid) {
        try {
            String fileName = release.getString("file_name");
            String version = release.getString("version");
            Integer storedFileSize = release.getInteger("file_size");
            String logoUrl = getLogoUrl();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("File Download")
                    .setDescription("Client file download event")
                    .setColor(new java.awt.Color(0x52, 0xA6, 0x84))
                    .addFields(
                            new EmbedBuilder.Field("Username", inlineCode(user.getUsername()), true),
                            new EmbedBuilder.Field("UID", inlineCode(user.getId()), true),
                            new EmbedBuilder.Field("License Plan", formatPlanStatus(user), true),
                            new EmbedBuilder.Field("File", defaultValue(fileName, "Unknown"), false),
                            new EmbedBuilder.Field("Version", defaultValue(version, "Unknown"), true),
                            new EmbedBuilder.Field("File Size", formatFileSize(storedFileSize == null ? fileSize : storedFileSize), true),
                            new EmbedBuilder.Field("Discord", formatDiscord(user.getDiscordUsername(), user.getDiscordId()), true),
                            new EmbedBuilder.Field("IP", defaultValue(ip, "Unknown"), true),
                            new EmbedBuilder.Field("HWID", defaultValue(hwid, "N/A"), true)
                    );

            if (logoUrl != null) {
                embed.setThumbnail(logoUrl);
                embed.setFooter("Echo Client", ZonedDateTime.now(ZoneId.of("UTC")), logoUrl);
            } else {
                embed.setFooter("Echo Client", ZonedDateTime.now(ZoneId.of("UTC")));
            }

            WebhookUtil.sendWebhook(Backend.getInstance().getConfig().discord.downloadWebhook, embed);
            return true;
        } catch (Exception exception) {
            Backend.logger.error("[API] Error logging download event", exception);
            return false;
        }
    }

    private static String getLogoUrl() {
        String frontendUrl = Backend.getInstance().getConfig().frontendUrl;
        if (frontendUrl == null || frontendUrl.isEmpty()) {
            return null;
        }

        return frontendUrl.replaceAll("/$", "") + "/logo.png";
    }

    private static String formatPlanStatus(User user) {
        String plan = user.getPlan();
        if (plan == null || plan.isEmpty()) {
            return "N/A";
        }

        if ("lifetime".equalsIgnoreCase(plan.trim())) {
            return "Lifetime";
        }

        if (user.getExparationDate() == null) {
            return plan;
        }

        if (user.getExparationDate().getTime() > System.currentTimeMillis()) {
            return String.format("%s (Expires: %s)", plan, user.getExparationDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        }

        return String.format("%s (Expired)", plan);
    }

    private static String formatDiscord(String discordUsername, String discordId) {
        boolean hasUsername = discordUsername != null && !discordUsername.isEmpty();
        boolean hasId = discordId != null && !discordId.isEmpty();

        if (!hasUsername && !hasId) {
            return "Not linked";
        }

        if (hasUsername && hasId) {
            return String.format("%s (%s)", discordUsername, discordId);
        }

        return hasUsername ? discordUsername : discordId;
    }

    private static String inlineCode(String value) {
        return String.format("`%s`", defaultValue(value, "Unknown"));
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }

        String[] units = {"B", "KB", "MB", "GB"};
        int index = (int) Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
        double scaled = bytes / Math.pow(1024, index);
        double rounded = Math.round(scaled * 100.0) / 100.0;
        return rounded + " " + units[index];
    }

    @Getter
    public enum Icon {
        BANNED("https://i.postimg.cc/k5bj1JYM/banned.png"),
        BLOCK("https://i.postimg.cc/MHfxSQWk/block.png"),
        CHECKMARK("https://i.postimg.cc/MZNQnPZ1/checkmark.png"),
        DATABASE_ERROR("https://i.postimg.cc/Gp5w1nG3/database-error.png"),
        DATABASE_SUCCESS("https://i.postimg.cc/vHtYs4gt/database-success.png"),
        FATAL("https://i.postimg.cc/ryvsdkTr/fatal.png");

        private final String url;

        Icon(String url) {
            this.url = url;
        }
    }
}
