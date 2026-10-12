package hack.echo.backend.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import hack.echo.backend.Backend;

import java.io.File;
import java.nio.file.Files;

public class ConfigManager {

    public static JsonObject loadConfig() {
        try {
            File file = new File("config.json");
            if (!file.exists()) {
                if (file.createNewFile()) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    JsonObject config = new JsonObject();
                    config.addProperty("port", "6767");
                    config.addProperty("backendPort", "3001");
                    config.addProperty("database", "mongodb://localhost:27017");
                    config.addProperty("dbName", "");
                    config.addProperty("encryptionKey", "ExampleEncryptionKey");
                    config.addProperty("secret", "ExampleEncryptionKey");
                    config.addProperty("clientJar", "client.jar");
                    config.addProperty("configPath", "./config");
                    config.addProperty("featuresJson", "features.json");
                    config.addProperty("tokenExpiry", 30000);
                    config.addProperty("jwtSecret", "");
                    config.addProperty("jwtExpiration", 86400000);
                    config.addProperty("backendUrl", "http://localhost:3001");
                    config.addProperty("frontendUrl", "http://localhost:3000");
                    JsonObject discord = new JsonObject();
                    discord.addProperty("clientId", "");
                    discord.addProperty("clientSecret", "");
                    discord.addProperty("botToken", "");
                    discord.addProperty("guildId", "");
                    discord.addProperty("redirectUri", "");
                    discord.addProperty("customerRoleId", "");
                    discord.addProperty("loginWebhook", "");
                    discord.addProperty("downloadWebhook", "");
                    config.add("discord", discord);
                    JsonObject stripe = new JsonObject();
                    stripe.addProperty("secretKey", "");
                    stripe.addProperty("publishableKey", "");
                    stripe.addProperty("webhookSecret", "");
                    JsonObject stripePrices = new JsonObject();
                    JsonObject stripeLifetime = new JsonObject();
                    stripeLifetime.addProperty("amount", 3500);
                    stripeLifetime.addProperty("currency", "eur");
                    stripeLifetime.addProperty("priceId", "");
                    stripePrices.add("lifetime", stripeLifetime);
                    JsonObject stripeMonthly = new JsonObject();
                    stripeMonthly.addProperty("amount", 670);
                    stripeMonthly.addProperty("currency", "eur");
                    stripeMonthly.addProperty("priceId", "");
                    stripePrices.add("monthly", stripeMonthly);
                    stripe.add("prices", stripePrices);
                    config.add("stripe", stripe);
                    JsonObject oxapay = new JsonObject();
                    oxapay.addProperty("merchantApiKey", "");
                    JsonObject oxapayPrices = new JsonObject();
                    JsonObject oxapayLifetime = new JsonObject();
                    oxapayLifetime.addProperty("amount", 35.00);
                    oxapayLifetime.addProperty("currency", "EUR");
                    oxapayPrices.add("lifetime", oxapayLifetime);
                    JsonObject oxapayMonthly = new JsonObject();
                    oxapayMonthly.addProperty("amount", 6.70);
                    oxapayMonthly.addProperty("currency", "EUR");
                    oxapayPrices.add("monthly", oxapayMonthly);
                    oxapay.add("prices", oxapayPrices);
                    oxapay.addProperty("sandbox", true);
                    config.add("oxapay", oxapay);
                    JsonObject turnstile = new JsonObject();
                    turnstile.addProperty("secret", "");
                    config.add("turnstile", turnstile);
                    Files.write(file.toPath(), gson.toJson(config).getBytes());
                    return config;
                } else {
                    Backend.logger.fatal("Failed to create configuration file.");
                    System.exit(1);
                }
            }
            return new Gson().fromJson(new String(Files.readAllBytes(file.toPath())), JsonObject.class);
        } catch (Exception e) {
            Backend.logger.fatal(String.format("Failed to load configuration file.%n%s", e));
            System.exit(1);
        }
        return null;
    }
}
