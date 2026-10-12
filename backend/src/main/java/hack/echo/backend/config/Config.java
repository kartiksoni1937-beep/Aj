package hack.echo.backend.config;

import com.google.gson.JsonObject;

public class Config {

    public int port;
    public int backendPort;
    public String database;
    public String dbName;
    public String encryptionKey;
    public String secret;
    public String clientJar;
    public String configPath;
    public String featuresJson;
    public long tokenExpiry;
    public String jwtSecret;
    public int jwtExpiration;
    public String backendUrl;
    public String frontendUrl;
    public DiscordConfig discord;
    public StripeConfig stripe;
    public OxapayConfig oxapay;
    public String turnstileSecret;

    public Config() {
        JsonObject config = ConfigManager.loadConfig();
        this.port = config.get("port").getAsInt();
        this.backendPort = config.get("backendPort").getAsInt();
        this.database = config.get("database").getAsString();
        this.dbName = config.get("dbName").getAsString();
        this.encryptionKey = config.get("encryptionKey").getAsString();
        this.secret = config.get("secret").getAsString();
        this.clientJar = config.get("clientJar").getAsString();
        this.configPath = config.get("configPath").getAsString();
        this.featuresJson = config.get("featuresJson").getAsString();
        this.tokenExpiry = config.get("tokenExpiry").getAsLong();
        this.jwtSecret = config.get("jwtSecret").getAsString();
        this.jwtExpiration = config.get("jwtExpiration").getAsInt();
        this.backendUrl = config.get("backendUrl").getAsString();
        this.frontendUrl = config.get("frontendUrl").getAsString();
        JsonObject discord = config.get("discord").getAsJsonObject();
        String discordClientId = discord.get("clientId").getAsString();
        String discordClientSecret = discord.get("clientSecret").getAsString();
        String discordBotToken = discord.get("botToken").getAsString();
        String discordGuildId = discord.get("guildId").getAsString();
        String discordRedirectUri = discord.get("redirectUri").getAsString();
        String discordCustomerRoleId = discord.get("customerRoleId").getAsString();
        String loginWebhook = discord.get("loginWebhook").getAsString();
        String downloadWebhook = discord.get("downloadWebhook").getAsString();
        this.discord = new DiscordConfig(discordClientId, discordClientSecret, discordBotToken, discordGuildId, discordRedirectUri, discordCustomerRoleId, loginWebhook, downloadWebhook);

        JsonObject stripe = config.get("stripe").getAsJsonObject();
        String stripeSecretKey = stripe.get("secretKey").getAsString();
        String stripePublishableKey = stripe.get("publishableKey").getAsString();
        String stripeWebhookSecret = stripe.get("webhookSecret").getAsString();

        JsonObject stripeLifetimeObj = stripe.get("prices").getAsJsonObject().get("lifetime").getAsJsonObject();
        int stripeLifetimePrice = stripeLifetimeObj.get("amount").getAsInt();
        String stripeLifetimeCurrency = stripeLifetimeObj.get("currency").getAsString();
        String stripeLifetimePriceId = stripeLifetimeObj.get("priceId").getAsString();
        StripeConfig.Price stripeLifetime = new StripeConfig.Price(stripeLifetimePrice, stripeLifetimeCurrency, stripeLifetimePriceId);

        JsonObject stripeMonthlyObj = stripe.get("prices").getAsJsonObject().get("monthly").getAsJsonObject();
        int stripeMonthlyPrice = stripeMonthlyObj.get("amount").getAsInt();
        String stripeMonthlyCurrency = stripeMonthlyObj.get("currency").getAsString();
        String stripeMonthlyPriceId = stripeMonthlyObj.get("priceId").getAsString();
        StripeConfig.Price stripeMonthly = new StripeConfig.Price(stripeMonthlyPrice, stripeMonthlyCurrency, stripeMonthlyPriceId);
        this.stripe = new StripeConfig(stripeSecretKey, stripePublishableKey, stripeWebhookSecret, stripeLifetime, stripeMonthly);

        JsonObject oxapay = config.get("oxapay").getAsJsonObject();
        String oxapayMerchantApiKey = oxapay.get("merchantApiKey").getAsString();
        JsonObject oxapayLifetimeObj = oxapay.get("prices").getAsJsonObject().get("lifetime").getAsJsonObject();
        double oxapayLifetimePrice = oxapayLifetimeObj.get("amount").getAsDouble();
        String oxapayLifetimeCurrency = oxapayLifetimeObj.get("currency").getAsString();
        OxapayConfig.Price oxapayLifetime = new OxapayConfig.Price(oxapayLifetimePrice, oxapayLifetimeCurrency);

        JsonObject oxapayMonthlyObj = oxapay.get("prices").getAsJsonObject().get("monthly").getAsJsonObject();
        double oxapayMonthlyPrice = oxapayMonthlyObj.get("amount").getAsDouble();
        String oxapayMonthlyCurrency = oxapayMonthlyObj.get("currency").getAsString();
        OxapayConfig.Price oxapayMonthly = new OxapayConfig.Price(oxapayMonthlyPrice, oxapayMonthlyCurrency);

        boolean oxapaySandbox = oxapay.get("sandbox").getAsBoolean();
        this.oxapay = new OxapayConfig(oxapayMerchantApiKey, oxapayLifetime, oxapayMonthly, oxapaySandbox);

        JsonObject turnstile = config.get("turnstile").getAsJsonObject();
        this.turnstileSecret = turnstile.get("secret").getAsString();
    }

    public static class DiscordConfig {

        public String clientId;
        public String clientSecret;
        public String botToken;
        public String guildId;
        public String redirectUri;
        public String customerRoleId;
        public String loginWebhook;
        public String downloadWebhook;

        public DiscordConfig(String clientId, String clientSecret, String botToken, String guildId, String redirectUri, String customerRoleId, String loginWebhook, String downloadWebhook) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.botToken = botToken;
            this.guildId = guildId;
            this.redirectUri = redirectUri;
            this.customerRoleId = customerRoleId;
            this.loginWebhook = loginWebhook;
            this.downloadWebhook = downloadWebhook;
        }
    }

    public static class StripeConfig {

        public String secretKey;
        public String publishableKey;
        public String webhookSecret;
        public Price lifetime;
        public Price monthly;

        public StripeConfig(String secretKey, String publishableKey, String webhookSecret, Price lifetime, Price monthly) {
            this.secretKey = secretKey;
            this.publishableKey = publishableKey;
            this.webhookSecret = webhookSecret;
            this.lifetime = lifetime;
            this.monthly = monthly;
        }

        public static class Price {

            public int price;
            public String currency;
            public String priceId;

            public Price(int price, String currency, String priceId) {
                this.price = price;
                this.currency = currency;
                this.priceId = priceId;
            }
        }
    }

    public static class OxapayConfig {

        public String merchantApiKey;
        public Price lifetime;
        public Price monthly;
        public boolean sandbox;

        public OxapayConfig(String merchantApiKey, Price lifetime, Price monthly, boolean sandbox) {
            this.merchantApiKey = merchantApiKey;
            this.lifetime = lifetime;
            this.monthly = monthly;
            this.sandbox = sandbox;
        }

        public static class Price {

            public double price;
            public String currency;

            public Price(double price, String currency) {
                this.price = price;
                this.currency = currency;
            }
        }
    }
}
