package hack.echo.backend.spring.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hack.echo.backend.Backend;
import hack.echo.backend.config.Config;
import hack.echo.backend.network.User;
import hack.echo.backend.spring.auth.RequireAuth;
import hack.echo.backend.spring.handler.DiscordAuthHandler;
import hack.echo.backend.spring.ratelimit.RateLimit;
import hack.echo.backend.util.DiscordUtil;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/api/auth/discord")
public class Discord {

    @Autowired
    private DiscordAuthHandler authHandler;

    @RequireAuth
    @RateLimit(limit = 1)
    @GetMapping(path = "/auth-url")
    public ResponseEntity<?> authUrl(HttpServletRequest request) {
        try {
            Config.DiscordConfig discord = Backend.getInstance().getConfig().discord;
            if (discord.clientId.isEmpty()) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Discord client ID not available. Please check bot token."));
            }

            String token = request.getHeader("Authorization").replace("Bearer ", "");
            User user = MongoUtil.getUser("username", WebUtil.getUsername(token));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("false", false, "message", "User not found"));
            }

            String state = authHandler.storeLinkingState(user.getUsername());
            String authUrl = String.format("https://discord.com/api/oauth2/authorize?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s", discord.clientId, URLEncoder.encode(discord.redirectUri, StandardCharsets.UTF_8), String.join("%20", new String[]{"identify", "guilds.join"}), state);
            return ResponseEntity.status(HttpStatus.OK).body(Map.of("success", true, "authUrl", authUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error"));
        }
    }

    @RateLimit(limit = 1)
    @GetMapping(path = "/callback")
    public ResponseEntity<?> callback(HttpServletRequest request) {
        try {
            String code = request.getParameter("code");
            String state = request.getParameter("state");
            Config config = Backend.getInstance().getConfig();
            String frontendUrl = config.frontendUrl;

            if (code == null) {
                return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).header("Location", String.format("%s/auth?error=no_code&message=Discord authorization code missing", frontendUrl)).build();
            }

            if (state == null) {
                return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).header("Location", String.format("%s/auth?error=no_state&message=Invalid state parameter", frontendUrl)).build();
            }

            boolean isLoginFlow = false;
            try {
                JsonObject stateObj = JsonParser.parseString(new String(Base64.getDecoder().decode(state))).getAsJsonObject();
                isLoginFlow = stateObj.has("type") && stateObj.get("type").getAsString().equals("login");
                if (isLoginFlow && stateObj.get("timestamp").getAsLong() < System.currentTimeMillis() - 5 * 60 * 1000) {
                    return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).header("Location", String.format("%s/auth?error=expired_state&message=Authorization request expired", frontendUrl)).build();
                }
            } catch (Exception ignored) {
            }

            Config.DiscordConfig discord = config.discord;
            if (discord.clientId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).body(Map.of("success", false, "message", "/auth?error=client_id_missing&message=Discord client ID not available"));
            }

            Map<String, String> params = Map.of(
                    "client_id", discord.clientId,
                    "client_secret", discord.clientSecret,
                    "grant_type", "authorization_code",
                    "code", code,
                    "redirect_uri", discord.redirectUri
            );
            String response = WebUtil.post("https://discord.com/api/v10/oauth2/token", params);
            if (response == null || !response.contains("access_token")) {
                return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).header("Location", String.format("%s/auth?error=expired_code&message=Discord authorization code expired. Please try logging in again.", frontendUrl)).build();
            }

            JsonObject responseObj = JsonParser.parseString(response).getAsJsonObject();
            String accessToken = responseObj.get("access_token").getAsString();
            JsonObject discordUser = JsonParser.parseString(Objects.requireNonNull(WebUtil.get("https://discord.com/api/v10/users/@me", accessToken))).getAsJsonObject();

            if (isLoginFlow) {
                return handleLogin(discordUser, accessToken, request);
            } else {
                String username = authHandler.exchangeLinkingState(state);
                if (username == null) {
                    return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).header("Location", String.format("%s/auth?error=invalid_state&message=Invalid or expired state parameter", frontendUrl)).build();
                }
                return handleLinking(discordUser, accessToken, username);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).header("Location", String.format("%s/auth?error=discord_callback_failed&message=%s", Backend.getInstance().getConfig().frontendUrl, URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8))).build();
        }
    }

    @RequireAuth
    @RateLimit(limit = 20)
    @GetMapping(path = "/status")
    public ResponseEntity<?> status(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").replace("Bearer ", "");

            if (token.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("false", false, "message", "No token provided"));
            }

            User user = MongoUtil.getUser("username", WebUtil.getUsername(token));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("false", false, "message", "User not found"));
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("linked", user.getDiscordId() != null);
            response.addProperty("discord_id", user.getDiscordId());
            response.addProperty("discord_username", user.getDiscordUsername());
            return ResponseEntity.ok(response.toString());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error"));
        }
    }

    @RequireAuth
    @RateLimit(limit = 1)
    @PostMapping(path = "/unlink")
    public ResponseEntity<?> unlink(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization").replace("Bearer ", "");
            if (token.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "No token provided"));
            }

            User user = MongoUtil.getUser("username", WebUtil.getUsername(token));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "User not found"));
            }

            if (user.getDiscordId() == null || user.getDiscordId().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Discord account is not linked"));
            }

            if (!MongoUtil.unlinkDiscord(user.getUsername())) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error"));
            }

            return ResponseEntity.ok(Map.of("success", true, "message", "Discord account unlinked successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error"));
        }
    }

    @RateLimit(limit = 1, windowTime = 10)
    @GetMapping(path = "/login-url")
    private ResponseEntity<?> loginUrl() {
        try {
            Config.DiscordConfig discord = Backend.getInstance().getConfig().discord;
            if (discord.clientId.isEmpty()) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Discord client ID not available. Please check bot token."));
            }

            JsonObject stateObj = new JsonObject();
            stateObj.addProperty("type", "login");
            stateObj.addProperty("timestamp", System.currentTimeMillis());
            String state = Base64.getEncoder().encodeToString(stateObj.toString().getBytes(StandardCharsets.UTF_8));
            String authUrl = String.format(
                    "https://discord.com/api/oauth2/authorize?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s",
                    discord.clientId,
                    URLEncoder.encode(discord.redirectUri, StandardCharsets.UTF_8),
                    String.join("%20", new String[]{"identify", "guilds.join"}),
                    state
            );

            return ResponseEntity.ok(Map.of("success", true, "authUrl", authUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error"));
        }
    }

    @RateLimit(limit = 10)
    @PostMapping(path = "/exchange-code", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> exchangeAuthCode(@RequestBody String payload) {
        try {
            JsonObject object = JsonParser.parseString(payload).getAsJsonObject();
            String code = object.has("code") ? object.get("code").getAsString() : null;

            if (code == null || code.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Authorization code is required"));
            }

            String token = authHandler.exchangeAuthCode(code);
            if (token == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Invalid or expired authorization code"));
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("sessionToken", token);
            return ResponseEntity.ok(response.toString());
        } catch (Exception e) {
            Backend.logger.error("[DISCORD] Exchange auth code error", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error"));
        }
    }

    private ResponseEntity<?> handleLogin(JsonObject userObj, String accessToken, HttpServletRequest request) {
        User user = MongoUtil.getUser("discord_id", userObj.get("id").getAsString());
        if (user == null) {
            String password = Backend.getInstance().getArgon2().encode(WebUtil.randomHex(32));
            String username =  userObj.get("username").getAsString().toLowerCase().replaceAll("/[^a-z0-9]+/g", "_");
            String baseUsername = username;

            int counter = 1;
            while (MongoUtil.getUser("username", username) != null) {
                username = String.format("%s$%d", baseUsername, counter++);
            }

            Document document = new Document();
            document.append("username", username);
            document.append("password", password);
            document.append("discord_id", userObj.get("id").getAsString());
            document.append("discord_username", String.format("%s#%s", userObj.get("username").getAsString(), userObj.get("discriminator").getAsString().equals("0") ? "" : userObj.get("discriminator").getAsString()));
            document.append("createdAt", new Date());
            MongoUtil.createUser(document);

            user = MongoUtil.getUser("username", username);
        } else {
            String username = String.format("%s#%s", userObj.get("username").getAsString(), userObj.get("discriminator").getAsString().equals("0") ? "" : userObj.get("discriminator").getAsString());
            if (!user.getDiscordUsername().equals(username)) {
                MongoUtil.setDiscordData(user.getUsername(), userObj.get("id").getAsString(), username);
            }
        }

        if (user.isBanned()) {
            return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).header("Location", String.format("%s/auth?error=banned&message=Account banned: %s", Backend.getInstance().getConfig().frontendUrl, user.getBanReason())).build();
        }

        DiscordUtil.addUserToGuild(userObj.get("id").getAsString(), accessToken, Backend.getInstance().getConfig().discord);

        String token = WebUtil.generateToken(user.getUsername());
        MongoUtil.setLastAttempt(WebUtil.getClientIP(request), false);

        String authCode = authHandler.storeAuthCode(token, 5 * 60 * 1000);
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).header("Location", String.format("%s/auth?discord_login=true&code=%s", Backend.getInstance().getConfig().frontendUrl, URLEncoder.encode(authCode, StandardCharsets.UTF_8))).build();
    }

    private ResponseEntity<?> handleLinking(JsonObject userObj, String accessToken, String username) {
        Config config = Backend.getInstance().getConfig();

        String id = userObj.get("id").getAsString();
        DiscordUtil.addUserToGuild(id, accessToken, config.discord);
        if (!MongoUtil.setDiscordData(username, id, String.format("%s%s", userObj.get("username").getAsString(), userObj.get("discriminator").getAsString().equals("0") ? "" : "#" + userObj.get("discriminator").getAsString()))) {
            return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).header("Location", String.format("%s/?error=user_not_found&message=User account not found", config.frontendUrl)).build();
        }

        if (!config.discord.customerRoleId.isEmpty()) {
            User user = MongoUtil.getUser("username", username);

            if (user.getPlan().equals("lifetime") || (user.getExparationDate() != null && user.getExparationDate().getTime() > System.currentTimeMillis())) {
                DiscordUtil.addRoleToMember(id, config.discord.customerRoleId, config.discord);
            }
        }

        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).header("Location", String.format("%s/dashboard?discord_linked=true&username=%s", config.frontendUrl, URLEncoder.encode(userObj.get("username").getAsString(), StandardCharsets.UTF_8))).build();
    }
}
