package hack.echo.backend.spring.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hack.echo.backend.Backend;
import hack.echo.backend.config.Config;
import hack.echo.backend.network.User;
import hack.echo.backend.spring.auth.RequireAuth;
import hack.echo.backend.spring.ratelimit.RateLimit;
import hack.echo.backend.util.DiscordUtil;
import hack.echo.backend.util.MongoUtil;
import hack.echo.backend.util.WebUtil;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

@Controller
@RequestMapping("/api/keys")
public class Key {

    @RequireAuth
    @RateLimit(limit = 1)
    @PostMapping(path = "/redeem", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> redeemKey(@RequestBody String payload, HttpServletRequest request) {
        try {
            JsonObject object = JsonParser.parseString(payload).getAsJsonObject();
            String key = object.has("key") ? object.get("key").getAsString() : null;

            if (key == null || key.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "License key is required"));
            }

            Document licenseKey = MongoUtil.getActiveLicenseKey(key.toUpperCase().trim());
            if (licenseKey == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Invalid or inactive license key"));
            }

            if (licenseKey.get("redeemed_by") != null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "This license key has already been redeemed"));
            }

            User user = MongoUtil.getUser("username", WebUtil.getUsername(request.getHeader("Authorization").replace("Bearer ", "")));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "User not found"));
            }

            Date now = new Date();
            Date expirationDate = null;
            Number durationDays = licenseKey.get("duration_days", Number.class);
            if (durationDays != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(now);
                calendar.add(Calendar.DAY_OF_YEAR, durationDays.intValue());
                expirationDate = calendar.getTime();
            }

            String plan = licenseKey.getString("plan");
            if (!MongoUtil.updatePlan(user.getUsername(), plan, expirationDate)) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error during key redemption"));
            }

            Config.DiscordConfig discord = Backend.getInstance().getConfig().discord;
            if (user.getDiscordId() != null && !user.getDiscordId().isEmpty() && discord.customerRoleId != null && !discord.customerRoleId.isEmpty()) {
                DiscordUtil.addRoleToMember(user.getDiscordId(), discord.customerRoleId, discord);
            }

            if (!MongoUtil.redeemLicenseKey(key.toUpperCase().trim(), user.getId(), now)) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error during key redemption"));
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", String.format("License key redeemed successfully! Plan: %s", plan == null ? "UNKNOWN" : plan.toUpperCase()));
            response.addProperty("plan", plan);
            response.addProperty("expiration_date", WebUtil.parseTime(expirationDate));
            return ResponseEntity.ok(response.toString());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error during key redemption"));
        }
    }
}
