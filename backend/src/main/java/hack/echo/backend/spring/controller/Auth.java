package hack.echo.backend.spring.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hack.echo.backend.Backend;
import hack.echo.backend.network.User;
import hack.echo.backend.spring.auth.RequireAuth;
import hack.echo.backend.spring.ratelimit.RateLimit;
import hack.echo.backend.spring.service.StripeSubscriptionSync;
import hack.echo.backend.util.MongoUtil;
import hack.echo.backend.util.WebUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/api/auth")
public class Auth {

    @Autowired
    private StripeSubscriptionSync stripeSubscriptionSync;

    @RateLimit(limit = 1, windowTime = 1, unit = TimeUnit.DAYS)
    @PostMapping(path = "/register", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> register(@RequestBody String payload) {
        try {
            JsonObject object = JsonParser.parseString(payload).getAsJsonObject();
            String username = object.has("username") ? object.get("username").getAsString() : null;
            String password = object.has("password") ? object.get("password").getAsString() : null;
            String turnstile = object.has("turnstileToken") ? object.get("turnstileToken").getAsString() : null;

            if (username == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Username and password are required"));
            }

            if (turnstile == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Verification required. Please complete the security check."));
            }

            String turnstileSecret = Backend.getInstance().getConfig().turnstileSecret;
            if (turnstileSecret.isEmpty()) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Verification service configuration error"));
            }

            Map<String, String> params = Map.of(
                    "secret", turnstileSecret,
                    "response", turnstile
            );
            JsonObject response = JsonParser.parseString(WebUtil.post("https://challenges.cloudflare.com/turnstile/v0/siteverify", params)).getAsJsonObject();
            if (!response.has("success") || !response.get("success").getAsBoolean()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("success", false, "message", "Verification failed. Please try again."));
            }

            User user = MongoUtil.getUser("username", username);
            if (user != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false, "message", "Username already exists"));
            }

            String hashedPassword = Backend.getInstance().getArgon2().encode(password);
            Document document = new Document();
            document.append("username", username);
            document.append("password", hashedPassword);
            document.append("createdAt", new Date());
            MongoUtil.createUser(document);

            user = MongoUtil.getUser("username", username);
            JsonObject responseObj = new JsonObject();
            responseObj.addProperty("success", true);
            responseObj.addProperty("message", "Account created successfully");
            JsonObject userObj = new JsonObject();
            userObj.addProperty("id", user.getId());
            userObj.addProperty("username", user.getRealUsername());
            responseObj.add("user", userObj);
            return ResponseEntity.ok().body(responseObj.toString());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error during registration"));
        }
    }

    @RateLimit(limit = 10)
    @PostMapping(path = "/login", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@RequestBody String payload, HttpServletRequest request) {
        try {
            JsonObject object = JsonParser.parseString(payload).getAsJsonObject();
            String username = object.has("username") ? object.get("username").getAsString() : null;
            String password = object.has("password") ? object.get("password").getAsString() : null;

            if (username == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Username and password are required"));
            }

            User user = MongoUtil.getUser("username", username);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Invalid username or password"));
            }

            if (!Backend.getInstance().getArgon2().matches(password, user.getPassword())) {
                MongoUtil.setLastAttempt(WebUtil.getClientIP(request), true);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Invalid username or password"));
            }

            if (user.isBanned()) {
                MongoUtil.setLastAttempt(WebUtil.getClientIP(request), true);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", String.format("Account banned: %s", !user.getBanReason().isEmpty() ? user.getBanReason() : "No reason provided")));
            }

            MongoUtil.setLastAttempt(WebUtil.getClientIP(request), false);
            JsonObject responseObj = new JsonObject();
            responseObj.addProperty("success", true);
            responseObj.addProperty("message", "Logged in successfully");
            JsonObject userInfo = new JsonObject();
            userInfo.addProperty("id", user.getUserNumber());
            userInfo.addProperty("username", user.getRealUsername());
            responseObj.add("user", userInfo);
            responseObj.addProperty("sessionToken", WebUtil.generateToken(user.getUsername()));
            responseObj.addProperty("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok().body(responseObj.toString());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error during authentication"));
        }
    }

    @RequireAuth
    @RateLimit(limit = 1)
    @PostMapping(path = "/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok().body(Map.of("success", true, "message", "Logged out successfully"));
    }

    @RequireAuth
    @RateLimit(limit = 20)
    @GetMapping(path = "/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        try {
            User user = MongoUtil.getUser("username", WebUtil.getUsername(request.getHeader("Authorization").replace("Bearer ", "")));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("false", false, "message", "User not found"));
            }

            user = stripeSubscriptionSync.refreshUserIfStripeCanFixAccess(user);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            JsonObject userInfo = new JsonObject();
            userInfo.addProperty("id", user.getId());
            userInfo.addProperty("username", user.getRealUsername());
            userInfo.addProperty("hwid", user.getHwid());
            userInfo.addProperty("userNumber", user.getUserNumber());
            userInfo.addProperty("plan", user.getPlan());
            userInfo.addProperty("expiration_date", WebUtil.parseTime(user.getExparationDate()));
            userInfo.addProperty("admin", user.isAdmin());
            userInfo.addProperty("beta", user.isBeta());
            userInfo.addProperty("banned", user.isBanned());
            userInfo.addProperty("ban_reason", user.getBanReason());
            userInfo.addProperty("discord_id", user.getDiscordId());
            userInfo.addProperty("discord_username", user.getDiscordUsername());
            userInfo.addProperty("last_hwid_reset", WebUtil.parseTime(user.getLastHwidReset()));
            userInfo.addProperty("last_login", WebUtil.parseTime(user.getLastAttempt()));
            userInfo.addProperty("createdAt", WebUtil.parseTime(user.getCreatedAt()));
            userInfo.addProperty("stripe_customer_id", user.getStripeCustomerId());
            userInfo.addProperty("stripe_subscription_id", user.getStripeSubscriptionId());
            userInfo.addProperty("stripe_subscription_status", user.getStripeSubscriptionStatus());
            userInfo.addProperty("stripe_collection_paused", user.isStripeCollectionPaused());
            userInfo.addProperty("premium_access", user.isPremiumAccess());
            response.add("user", userInfo);
            return ResponseEntity.ok().body(response.toString());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error"));
        }
    }

    @RequireAuth
    @RateLimit(limit = 1)
    @PostMapping(path = "/reset-hwid")
    public ResponseEntity<?> resetHWID(HttpServletRequest request) {
        try {
            User user = MongoUtil.getUser("username", WebUtil.getUsername(request.getHeader("Authorization").replace("Bearer ", "")));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("false", false, "message", "User not found"));
            }

            int status = MongoUtil.resetHWID(user.getUsername());
            if (status == 0) {
                return ResponseEntity.ok().body(Map.of("success", true, "message", "HWID reset successfully"));
            } else if (status == -1) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to reset HWID"));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", String.format("HWID can only be reset once per month. Please wait %s more day(s).", status),
                        "daysRemaining", status
                ));
            }
        }  catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error"));
        }
    }

    @RequireAuth
    @RateLimit(limit = 2)
    @PostMapping(path = "/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody String payload, HttpServletRequest request) {
        try {
            JsonObject object = JsonParser.parseString(payload).getAsJsonObject();
            String password = object.has("newPassword") ? object.get("newPassword").getAsString() : null;

            if (password == null || password.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "New password is required"));
            }

            if (password.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Password must be at least 6 characters long"));
            }

            User user = MongoUtil.getUser("username", WebUtil.getUsername(request.getHeader("Authorization").replace("Bearer ", "")));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "User not found"));
            }

            if (user.isBanned()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", String.format("Account banned: %s", !user.getBanReason().isEmpty() ? user.getBanReason() : "No reason provided")));
            }

            String hashPassword = Backend.getInstance().getArgon2().encode(password);
            if (MongoUtil.updatePassword(user.getUsername(), hashPassword)) {
                return ResponseEntity.ok().body(Map.of("success", true, "message", "Password reset successfully"));
            } else {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error"));
            }
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error"));
        }
    }
}
