package hack.echo.backend.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import hack.echo.backend.Backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class WebhookBuilder {

    private final String url;
    private String content = "";
    private final List<EmbedBuilder> embeds = new ArrayList<>();
    private String name;
    private String avatarUrl;

    public WebhookBuilder(String url) {
        this.url = url;
    }

    public WebhookBuilder setContent(String content) {
        this.content = content;
        return this;
    }

    public WebhookBuilder addEmbed(EmbedBuilder embed) {
        embeds.add(embed);
        return this;
    }

    public WebhookBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public WebhookBuilder setAvatarUrl(String avatarUrl) {
        if (!avatarUrl.startsWith("http") && !avatarUrl.startsWith("https")) {
            avatarUrl = "https://" + avatarUrl;
        }
        this.avatarUrl = avatarUrl;
        return this;
    }

    public void send() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("content", content);

        JsonArray jsonArray = new JsonArray();
        if (embeds.isEmpty()) {
            jsonArray.add(new JsonObject());
        } else {
            for (EmbedBuilder embedBuilder : embeds) {
                JsonObject embed = new JsonObject();
                embed.addProperty("title", embedBuilder.getTitle());
                embed.addProperty("description", embedBuilder.getDescription());
                embed.addProperty("url", embedBuilder.getUrl());
                embed.addProperty("color", embedBuilder.getColor());
                JsonObject author = new JsonObject();
                author.addProperty("name", embedBuilder.getAuthor());
                author.addProperty("icon_url", embedBuilder.getAuthorIcon());
                embed.add("author", author);

                JsonObject footer = new JsonObject();
                footer.addProperty("text", embedBuilder.getFooter());
                footer.addProperty("icon_url", embedBuilder.getIconURL());
                if (embedBuilder.getTimestamp() != null || embedBuilder.getIconURL() != null) {
                    embed.add("footer", footer);
                }
                if (embedBuilder.getTimestamp() != null) {
                    embed.addProperty("timestamp", embedBuilder.getTimestamp());
                }

                JsonObject image = new JsonObject();
                image.addProperty("url", embedBuilder.getImage());
                if (embedBuilder.getImage() != null) {
                    embed.add("image", image);
                }

                JsonObject thumbnail = new JsonObject();
                thumbnail.addProperty("url", embedBuilder.getThumbnail());
                if (embedBuilder.getThumbnail() != null) {
                    embed.add("thumbnail", thumbnail);
                }

                if (!embedBuilder.getFields().isEmpty()) {
                    JsonArray fields = new JsonArray();
                    for (EmbedBuilder.Field fieldBuilder : embedBuilder.getFields()) {
                        JsonObject field = new JsonObject();
                        field.addProperty("name", fieldBuilder.getName());
                        field.addProperty("value", fieldBuilder.getValue());
                        field.addProperty("inline", fieldBuilder.isInline());
                        fields.add(field);
                    }
                    embed.add("fields", fields);
                }

                jsonArray.add(embed);
            }
            jsonObject.add("embeds", jsonArray);
        }

        jsonObject.addProperty("username", name);
        jsonObject.addProperty("avatar_url", avatarUrl);
        jsonObject.add("attachments", new JsonArray());

        try {
            HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonObject.toString()))
                    .build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            Backend.logger.error("Failed to send to webhook", e);
        }
    }
}
