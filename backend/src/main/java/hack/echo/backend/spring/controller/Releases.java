package hack.echo.backend.spring.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import hack.echo.backend.Backend;
import hack.echo.backend.network.User;
import hack.echo.backend.spring.auth.RequireAuth;
import hack.echo.backend.spring.ratelimit.RateLimit;
import hack.echo.backend.spring.service.StripeSubscriptionSync;
import hack.echo.backend.util.MongoUtil;
import hack.echo.backend.util.WebUtil;
import hack.echo.backend.util.WebhookUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Controller
@RequestMapping("/api/releases")
public class Releases {

    @Autowired
    private StripeSubscriptionSync stripeSubscriptionSync;

    @RateLimit(limit = 20)
    @GetMapping(path = "/public", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPublicReleases() {
        try {
            JsonArray releases = MongoUtil.getPublicReleasesJson();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.add("releases", releases);
            return ResponseEntity.ok(response.toString());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "Failed to fetch releases"));
        }
    }

    @RateLimit(limit = 20)
    @RequireAuth(subOnly = true, noMessage = true)
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReleases() {
        try {
            JsonArray releases = MongoUtil.getReleasesJson();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.add("releases", releases);
            return ResponseEntity.ok(response.toString());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "Failed to fetch releases"));
        }
    }

    @RequireAuth
    @RateLimit(limit = 1)
    @GetMapping(path = "/download/{fileName}")
    public ResponseEntity<?> download(@PathVariable("fileName") String fileName, HttpServletRequest request, HttpServletResponse response) {
        try {
            User user = MongoUtil.getUser("username", WebUtil.getUsername(request.getHeader("Authorization").replace("Bearer ", "")));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("false", false, "message", "User not found"));
            }

            user = stripeSubscriptionSync.refreshUserIfStripeCanFixAccess(user);

            /*if (!user.isBeta()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Downloads are currently beta-only for your account"));
            }*/

            if (user.isBanned()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success",false, "message", String.format("Account banned: %s", !user.getBanReason().isEmpty() ? user.getBanReason() : "No reason provided")));
            }

            if (!stripeSubscriptionSync.hasActiveLicense(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "You need an active license to download releases"));
            }

            String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            Path releasesDir = Paths.get("releases").toAbsolutePath().normalize();
            Path filePath = releasesDir.resolve(decodedFileName).normalize();

            if (!filePath.startsWith(releasesDir)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Invalid file path"));
            }

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "File not found"));
            }

            Document release = MongoUtil.getActiveRelease(decodedFileName);
            if (release == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Release not found or no longer available"));
            }

            if (!release.getBoolean("is_active", false)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "This release is no longer available"));
            }

            long fileSize = Files.size(filePath);
            if (!WebhookUtil.logDownloadEvent(user, release, fileSize, WebUtil.getClientIP(request), request.getHeader("X-HWID"))) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to log download event. Download blocked."));
            }

            Resource fileResource = new FileSystemResource(filePath);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", String.format("attachment; filename=\"%s\"", release.getString("file_name")))
                    .header("Content-Length", String.valueOf(fileSize))
                    .body(fileResource);
        } catch (IOException exception) {
            Backend.logger.error("[API] Error streaming release", exception);
            if (!response.isCommitted()) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to download release"));
            }
        } catch (Exception exception) {
            Backend.logger.error("[API] Error in download release", exception);
            if (!response.isCommitted()) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to download release"));
            }
        }
        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to download release"));
    }
}
