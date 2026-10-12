package hack.echo.backend.discord;

import lombok.Getter;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.ZonedDateTime;

@Getter
public class EmbedBuilder {

    private String title;
    private String description;
    private String url;
    private int color = 16777215;
    private String author;
    private String authorIcon;
    private String iconURL;
    private String footer;
    private String timestamp;
    private String image;
    private String thumbnail;
    private final List<Field> fields = new ArrayList<>();

    public EmbedBuilder setTitle(String title) {
        this.title = title;
        return this;
    }

    public EmbedBuilder setDescription(String ... description) {
        StringBuilder sb = new StringBuilder();
        for (String s : description) {
            sb.append(s).append("\n");
        }
        sb = new StringBuilder(sb.reverse().toString().replaceFirst("\n", ""));
        this.description = sb.reverse().toString();
        return this;
    }

    public EmbedBuilder setDescription(String description) {
        this.description = description.replace("%newline%", "\n");
        return this;
    }

    public EmbedBuilder setUrl(String url) {
        if (!url.startsWith("http") && !url.startsWith("https")) {
            url = String.format("https://%s", url);
        }
        this.url = url;
        return this;
    }

    public EmbedBuilder setColor(Color color) {
        this.color = (color.getRed() * 256 * 256) + (color.getGreen() * 256) + (color.getBlue());;
        return this;
    }

    public EmbedBuilder setAuthor(String author, String icon) {
        if (!icon.startsWith("http") && !icon.startsWith("https")) {
            icon = String.format("https://%s", icon);
        }
        this.author = author;
        this.authorIcon = icon;
        return this;
    }

    public EmbedBuilder setAuthor(String author) {
        this.author = author;
        return this;
    }

    public EmbedBuilder setFooter(String footer, ZonedDateTime timestamp, String icon) {
        if (!icon.startsWith("http") && !icon.startsWith("https")) {
            icon = String.format("https://%s", icon);
        }
        this.iconURL = icon;
        this.footer = footer;
        this.timestamp = timestamp.toOffsetDateTime().toString();
        return this;
    }

    public EmbedBuilder setFooter(String footer, ZonedDateTime timestamp) {
        this.footer = footer;
        this.timestamp = timestamp.toOffsetDateTime().toString();
        return this;
    }

    public EmbedBuilder addImage(String image) {
        if (!image.startsWith("http") && !image.startsWith("https")) {
            image = String.format("https://%s", image);
        }
        this.image = image;
        return this;
    }

    public EmbedBuilder setThumbnail(String thumbnail) {
        if (!thumbnail.startsWith("http") && !thumbnail.startsWith("https")) {
            thumbnail = String.format("https://%s", thumbnail);
        }
        this.thumbnail = thumbnail;
        return this;
    }

    public EmbedBuilder addField(String name, String value, boolean inline) {
        this.fields.add(new Field(name, value, inline));
        return this;
    }

    public EmbedBuilder addFields(Field... fields) {
        this.fields.addAll(Arrays.asList(fields));
        return this;
    }

    @Getter
    public static class Field {

        private final String name;
        private final String value;
        private final boolean inline;

        public Field(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }
    }
}
