package hack.echo.backend.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import hack.echo.backend.logger.Ansi;
import hack.echo.backend.network.Client;
import hack.echo.backend.network.User;
import org.bson.Document;
import hack.echo.backend.Backend;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static hack.echo.backend.logger.Ansi.Color.*;
import static hack.echo.backend.util.WebhookUtil.Icon;

public class MongoUtil {

    private static final Collation USERNAME_COLLATION = Collation.builder()
            .locale("en")
            .collationStrength(CollationStrength.SECONDARY)
            .build();

    private static MongoCollection<Document> users() {
        return Backend.getInstance().getDatabase().getCollection("users");
    }

    private static MongoCollection<Document> payments() {
        return Backend.getInstance().getDatabase().getCollection("payments");
    }

    private static MongoCollection<Document> releases() {
        return Backend.getInstance().getDatabase().getCollection("releases");
    }

    private static MongoCollection<Document> keys() {
        return Backend.getInstance().getDatabase().getCollection("keys");
    }

    private static Document findUser(String key, Object value) {
        if ("username".equals(key)) {
            return users().find(new Document("username", value)).collation(USERNAME_COLLATION).first();
        }
        return users().find(new Document(key, value)).first();
    }

    private static Document findUserByUsername(String username) {
        return users().find(new Document("username", username)).collation(USERNAME_COLLATION).first();
    }

    private static void replaceUser(String key, Document document) {
        users().replaceOne(new Document(key, document.get(key)), document);
    }

    private static void replaceUserByUsername(Document document) {
        users().replaceOne(new Document("username", document.get("username")), document);
    }

    private static boolean updateUser(String key, Object value, Consumer<Document> mutator) {
        Document document = findUser(key, value);
        if (document == null) return false;
        mutator.accept(document);
        replaceUser(key, document);
        return true;
    }

    private static boolean updateUserByUsername(String username, Consumer<Document> mutator) {
        Document document = findUserByUsername(username);
        if (document == null) return false;
        mutator.accept(document);
        replaceUserByUsername(document);
        return true;
    }

    private static void putOrRemove(Document document, String key, String value) {
        if (value == null) {
            document.remove(key);
        } else {
            document.put(key, value);
        }
    }

    private static void putOrRemoveIfBlank(Document document, String key, String value) {
        if (value == null || value.isEmpty()) {
            document.remove(key);
        } else {
            document.put(key, value);
        }
    }

    public static User getUser(String key, Object value) {
        Document document = findUser(key, value);
        if (document == null) {
            return null;
        }
        return toUser(document);
    }

    public static List<User> getUsers() {
        FindIterable<Document> documents = users().find();

        List<User> users = new ArrayList<>();
        for (Document document : documents) {
            users.add(toUser(document));
        }

        return users;
    }

    public static List<User> getExpiredUsers() {
        FindIterable<Document> documents = users().find(new Document("plan", new Document("$nin", List.of("N/A", "free")))
                .append("expiration_date", new Document("$lte", new Date()).append("$ne", null)));

        List<User> users = new ArrayList<>();
        for (Document document : documents) {
            users.add(toUser(document));
        }

        return users;
    }

    public static int getLastUserNumber() {
        Document document = users().find(new Document("userNumber", new Document("$exists", true)))
                .sort(new Document("userNumber", -1))
                .first();
        if (document == null) {
            return 0;
        }
        return document.getInteger("userNumber", 0);
    }

    public static boolean createUser(Document document) {
        Document existingDocument = findUserByUsername(document.getString("username"));
        if (existingDocument != null) {
            return false;
        }

        users().insertOne(document);
        return true;
    }

    public static int banUser(String key, String value, String reason) {
        Document document = findUser(key, "username".equals(key) ? value.toLowerCase() : value);
        if (document == null) {
            return 0;
        }

        if (document.getBoolean("banned", true)) {
            return 2;
        }

        document.put("banned", true);
        if (!reason.isEmpty()) {
            document.put("ban_reason", reason);
        }
        replaceUser(key, document);
        return 1;
    }

    public static int unbanUser(String key, String value) {
        Document document = findUser(key, value);
        if (document == null) {
            return 0;
        }

        if (!document.getBoolean("banned", false)) {
            return 2;
        }

        document.put("banned", false);
        document.remove("ban_reason");
        replaceUser(key, document);
        return 1;
    }

    public static boolean setHWID(String key, String value, String hwid) {
        return updateUser(key, value, d -> d.put("hwid", hwid));
    }

    public static boolean setBeta(String key, String value, boolean beta) {
        return updateUser(key, value, d -> d.put("beta", beta));
    }

    public static User increaseVL(Client client) {
        String key, value;
        if (!client.getHwid().equals("Unknown")) {
            key = "hwid";
            value = client.getHwid();
        } else if (client.getUserNameNoIp() != null) {
            key = "username";
            value = client.getUserNameNoIp();
        } else {
            key = "last_login_ip";
            value = client.getIp();
        }

        Document document = findUser(key, value);
        if (document == null) {
            document = users().find(new Document("last_attempt_ip", value)).first();
            if (document == null) {
                if (!key.equals("last_login_ip")) {
                    Backend.logger.warn(String.format("Failed to find user with %s (increaseVL) %s", key, value));
                    WebhookUtil.sendEmbed(String.format("Failed to find user with %s (increaseVL) %s", key, value), Icon.DATABASE_ERROR, new Color(241, 196, 15), client);
                }
                return null;
            }
            key = "last_attempt_ip";
        }

        int vl = 0;
        if (document.containsKey("vl")) {
            vl = document.getInteger("vl");
        }
        document.put("vl", vl + 1);

        boolean banned = document.getBoolean("banned", false);
        if (vl + 1 >= 3) {
            document.put("banned", true);
            document.put("ban_reason", "Exceeded maximum VL");

            if (!banned) {
                Backend.logger.info(String.format("User %s has been banned for 3 VL", new Ansi().c(WHITE).s(document.getString("username"))));
                WebhookUtil.sendEmbed(String.format("User %s has been banned for 3 VL", document.getString("username")), "@everyone", Icon.BANNED, new Color(230, 74, 57), client);
            }
        }

        replaceUser(key, document);
        return toUser(document);
    }

    public static boolean setVL(String username, int vl) {
        return updateUserByUsername(username, d -> d.put("vl", vl));
    }

    public static void setLastAttempt(Client client, boolean failed) {
        String key, value, ip = client.getIp();
        if (client.getHwid() != null) {
            key = "hwid";
            value = client.getHwid();
        } else if (client.getUserNameNoIp() != null) {
            key = "username";
            value = client.getUserNameNoIp();
        } else {
            key = "lastIp";
            value = client.getUsername();
        }

        Document document = findUser(key, value);
        if (setLastAttempt(ip, failed, document)) return;
        replaceUser(key, document);
    }

    public static void setLastAttempt(String ip, boolean failed) {
        MongoCollection<Document> collection = users();
        Document document = collection.find(new Document("last_attempt_ip", ip)).first();

        if (setLastAttempt(ip, failed, document)) return;
        collection.replaceOne(new Document("last_attempt_ip", ip), document);
    }

    private static boolean setLastAttempt(String ip, boolean failed, Document document) {
        if (document == null) {
            return true;
        }

        document.put("last_attempt", new Date());
        document.put("last_attempt_ip", ip);
        if (failed) {
            document.put("last_failed_attempt", new Date());
            document.append("last_failed_attempt_ip", ip);
        } else {
            document.put("last_login_ip", ip);
        }
        return false;
    }

    public static boolean setDiscordData(String username, String discordId, String discordUsername) {
        return updateUserByUsername(username, d -> {
            d.put("discord_id", discordId);
            d.put("discord_username", discordUsername);
        });
    }

    public static boolean unlinkDiscord(String username) {
        return updateUserByUsername(username, d -> {
            d.remove("discord_id");
            d.remove("discord_username");
        });
    }

    public static boolean removeField(String username, String key) {
        return updateUserByUsername(username, d -> d.remove(key));
    }

    public static int resetHWID(String username) {
        Document document = findUserByUsername(username);
        if (document == null) {
            return -1;
        }

        if (document.containsKey("last_hwid_reset")) {
            int daysSinceReset = (int) ((new Date().getTime() - document.getDate("last_hwid_reset").getTime()) / (1000 * 60 * 60 * 24));
            if (daysSinceReset < 30) {
                return 30 - daysSinceReset;
            }
        }

        document.remove("hwid");
        document.put("last_hwid_reset", new Date());
        replaceUserByUsername(document);
        return 0;
    }

    public static boolean updatePassword(String username, String password) {
        return updateUserByUsername(username, d -> d.put("password", password));
    }

    public static boolean updatePlan(String username, String plan, Date expirationDate) {
        return updateUserByUsername(username, d -> {
            d.put("plan", plan);
            d.put("expiration_date", expirationDate);
            d.put("premium_access", resolvePremiumAccess(plan, expirationDate));
            if (!d.containsKey("userNumber")) {
                d.put("userNumber", getLastUserNumber() + 1);
            }
        });
    }

    public static void updateStripeCustomerId(String username, String customerId, String subscriptionId) {
        updateUserByUsername(username, d -> {
            putOrRemove(d, "stripe_customer_id", customerId);
            putOrRemove(d, "stripe_subscription_id", subscriptionId);
        });
    }

    public static void updateStripeSubscriptionState(String username, String customerId, String subscriptionId, String subscriptionStatus, boolean collectionPaused, boolean premiumAccess, Date expirationDate) {
        updateUserByUsername(username, d -> {
            putOrRemove(d, "stripe_customer_id", customerId);
            putOrRemove(d, "stripe_subscription_id", subscriptionId);
            putOrRemoveIfBlank(d, "stripe_subscription_status", subscriptionStatus);
            d.put("stripe_collection_paused", collectionPaused);
            d.put("premium_access", premiumAccess);
            d.put("expiration_date", expirationDate);
            d.put("plan", premiumAccess ? "premium" : "N/A");
        });
    }

    public static void updateLifetimeStripeSubscriptionState(String username, String customerId, String subscriptionId, String subscriptionStatus, boolean collectionPaused) {
        updateUserByUsername(username, d -> {
            d.put("plan", "lifetime");
            d.put("premium_access", true);
            d.put("expiration_date", null);
            putOrRemoveIfBlank(d, "stripe_customer_id", customerId);
            putOrRemoveIfBlank(d, "stripe_subscription_id", subscriptionId);
            putOrRemoveIfBlank(d, "stripe_subscription_status", subscriptionStatus);
            d.put("stripe_collection_paused", collectionPaused);
        });
    }

    public static void clearLifetimeStripeSubscriptionState(String username, String customerId) {
        updateUserByUsername(username, d -> {
            d.put("plan", "lifetime");
            d.put("premium_access", true);
            d.put("expiration_date", null);
            putOrRemoveIfBlank(d, "stripe_customer_id", customerId);
            d.remove("stripe_subscription_id");
            d.remove("stripe_subscription_status");
            d.put("stripe_collection_paused", false);
        });
    }

    private static User toUser(Document document) {
        return new User(document.getObjectId("_id").toString(),
                document.getString("username"),
                resolveRealUsername(document),
                document.getString("email"),
                document.getInteger("userNumber", -1),
                document.getString("password"),
                document.getString("loaded_config"),
                document.getString("hwid"),
                document.getString("plan"),
                document.getDate("expiration_date"),
                document.getBoolean("admin", false),
                document.getBoolean("beta", false),
                document.getBoolean("banned", false),
                document.getString("ban_reason"),
                document.getString("discord_id"),
                document.getString("discord_username"),
                document.getInteger("vl", 0),
                document.getString("last_login_ip"),
                document.getDate("last_hwid_reset"),
                document.getDate("last_attempt"),
                document.getString("last_attempt_ip"),
                document.getDate("last_failed_attempt"),
                document.getString("stripe_customer_id"),
                document.getString("stripe_subscription_id"),
                document.getString("stripe_subscription_status"),
                document.getBoolean("stripe_collection_paused", false),
                readPremiumAccess(document),
                document.getDate("createdAt"));
    }

    private static boolean readPremiumAccess(Document document) {
        if (document.containsKey("premium_access")) {
            return document.getBoolean("premium_access", false);
        }

        return resolvePremiumAccess(document.getString("plan"), document.getDate("expiration_date"));
    }

    private static boolean resolvePremiumAccess(String plan, Date expirationDate) {
        if (plan == null) {
            return false;
        }

        if ("lifetime".equalsIgnoreCase(plan)) {
            return true;
        }

        if (!"premium".equalsIgnoreCase(plan)) {
            return false;
        }

        if (expirationDate == null) {
            return true;
        }

        return expirationDate.getTime() >= System.currentTimeMillis();
    }

    public static Document getActiveLicenseKey(String key) {
        return keys().find(new Document("key", key).append("is_active", true)).first();
    }

    public static boolean redeemLicenseKey(String key, String userId, Date redeemedAt) {
        MongoCollection<Document> collection = keys();
        Document document = collection.find(new Document("key", key).append("is_active", true)).first();

        if (document == null) {
            return false;
        }

        document.put("redeemed_by", userId);
        document.put("redeemed_at", redeemedAt);
        collection.replaceOne(new Document("key", key).append("is_active", true), document);
        return true;
    }

    public static void createPayment(Document payment) {
        payments().insertOne(payment);
    }

    public static void deletePaymentByOrderId(String orderId) {
        payments().deleteOne(new Document("order_id", orderId)).getDeletedCount();
    }

    public static Document getPayment(String field, Object value) {
        return payments().find(new Document(field, value)).first();
    }

    public static Document getPaymentByTrackId(String trackId) {
        return getPayment("track_id", trackId);
    }

    public static Document getPaymentByOrderId(String orderId) {
        return getPayment("order_id", orderId);
    }

    public static void updatePaymentInvoiceData(String orderId, String trackId, String paymentUrl, Date expiredAt) {
        updatePayment("order_id", orderId, d -> {
            d.put("track_id", trackId);
            d.put("payment_url", paymentUrl);
            d.put("expired_at", expiredAt);
        });
    }

    public static void updatePaymentStatus(String trackId, String status, Date paidAt) {
        updatePayment("track_id", trackId, d -> {
            d.put("status", status);
            if (paidAt != null) {
                d.put("paid_at", paidAt);
            }
        });
    }

    public static void markPaymentPaid(String field, Object value) {
        updatePayment(field, value, d -> {
            d.put("status", "paid");
            if (!d.containsKey("paid_at")) {
                d.put("paid_at", new Date());
            }
        });
    }

    public static void markPaymentRefunded(String field, Object value, long refundedAmount, boolean fullRefund) {
        updatePayment(field, value, d -> {
            d.put("status", fullRefund ? "refunded" : "partially_refunded");
            d.put("refunded_amount", refundedAmount);
            d.put("refunded_at", new Date());
        });
    }

    public static void markPaymentDisputed(String field, Object value, String reason) {
        updatePayment(field, value, d -> {
            d.put("status", "disputed");
            d.put("disputed_at", new Date());
            if (reason != null && !reason.isEmpty()) {
                d.put("dispute_reason", reason);
            }
        });
    }

    private static void updatePayment(String field, Object value, Consumer<Document> mutator) {
        MongoCollection<Document> collection = payments();
        Document document = collection.find(new Document(field, value)).first();
        if (document == null) {
            return;
        }
        mutator.accept(document);
        collection.replaceOne(new Document(field, value), document);
    }

    public static String getDiscordId(String username) {
        Document document = findUserByUsername(username);
        if (document == null) {
            return null;
        }
        return document.getString("discord_id");
    }

    public static JsonArray getPublicReleasesJson() {
        FindIterable<Document> documents = releases().find(new Document("is_active", true))
                .sort(new Document("createdAt", -1))
                .limit(50);

        JsonArray result = new JsonArray();
        for (Document document : documents) {
            JsonObject releaseJson = new JsonObject();
            releaseJson.addProperty("version", document.getString("version"));
            releaseJson.add("added", toJsonArray(getStringList(document, "added")));
            releaseJson.add("changed", toJsonArray(getStringList(document, "changed")));
            releaseJson.add("fixed", toJsonArray(getStringList(document, "fixed")));

            Date createdAt = document.getDate("createdAt");
            if (createdAt == null) {
                releaseJson.add("date", JsonNull.INSTANCE);
            } else {
                releaseJson.addProperty("date", createdAt.toInstant().toString());
            }

            result.add(releaseJson);
        }

        return result;
    }

    public static JsonArray getReleasesJson() {
        FindIterable<Document> documents = releases().find(new Document("is_active", true))
                .sort(new Document("createdAt", -1))
                .limit(50);

        JsonArray result = new JsonArray();
        for (Document document : documents) {
            JsonObject releaseJson = new JsonObject();
            releaseJson.addProperty("version", document.getString("version"));
            releaseJson.add("added", toJsonArray(getStringList(document, "added")));
            releaseJson.add("changed", toJsonArray(getStringList(document, "changed")));
            releaseJson.addProperty("download_url", document.getString("file_url"));
            releaseJson.addProperty("file_name", document.getString("file_name"));
            releaseJson.addProperty("file_size", document.getInteger("file_size"));
            releaseJson.addProperty("is_silent", document.getBoolean("is_silent"));
            releaseJson.add("fixed", toJsonArray(getStringList(document, "fixed")));

            Date createdAt = document.getDate("createdAt");
            if (createdAt == null) {
                releaseJson.add("date", JsonNull.INSTANCE);
            } else {
                releaseJson.addProperty("date", createdAt.toInstant().toString());
            }

            result.add(releaseJson);
        }

        return result;
    }

    public static Document getActiveRelease(String fileName) {
        return releases().find(new Document("file_url", String.format("/api/releases/download/%s", fileName)).append("is_active", true)).first();
    }

    private static JsonArray toJsonArray(java.util.List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static java.util.List<String> getStringList(Document document, String key) {
        List<String> values = document.getList(key, String.class);
        if (values == null) {
            return Collections.emptyList();
        }

        return values;
    }

    private static String resolveRealUsername(Document document) {
        String realUsername = document.getString("realUsername");
        if (realUsername != null && !realUsername.isEmpty()) {
            return realUsername;
        }

        return document.getString("username");
    }
}
