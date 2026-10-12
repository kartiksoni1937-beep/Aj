package hack.echo.backend.util;

import com.google.gson.JsonObject;
import hack.echo.backend.Backend;
import hack.echo.backend.config.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DiscordUtil {

    public static String addUserToGuild(String userId, String accessToken, Config.DiscordConfig discord) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            String url = String.format("https://discord.com/api/guilds/%s/members/%s", discord.guildId, userId);
            JsonObject body = new JsonObject();
            body.addProperty("access_token", accessToken);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", String.format("Bot %s", discord.botToken))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            Backend.logger.error("Failed to add user to guild", e);
            return null;
        }
    }

    public static String addRoleToMember(String userId, String roleId, Config.DiscordConfig discord) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = String.format("https://discord.com/api/guilds/%s/members/%s/roles/%s", discord.guildId, userId, roleId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", String.format("Bot %s", discord.botToken))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();

            return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            Backend.logger.error("Failed to add role to member", e);
            return null;
        }
    }

    public static String removeRoleFromMember(String userId, String roleId, Config.DiscordConfig discord) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = String.format("https://discord.com/api/guilds/%s/members/%s/roles/%s", discord.guildId, userId, roleId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", String.format("Bot %s", discord.botToken))
                    .header("Content-Type", "application/json")
                    .DELETE()
                    .build();

            return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            Backend.logger.error("Failed to remove role from member", e);
            return null;
        }
    }
}
