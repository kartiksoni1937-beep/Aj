package hack.echo.backend.spring.controller;

import hack.echo.backend.spring.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Controller
@RequestMapping("/api")
public class Payload {

    @RateLimit(limit = 1)
    @GetMapping(path = "/payload")
    public ResponseEntity<?> payload(HttpServletResponse response) {
        try {
            String payloadFileName = "payload.zip";
            Path payloadPath = null;
            Path resourcesPath = Paths.get("releases", payloadFileName).toAbsolutePath().normalize();
            if (Files.exists(resourcesPath) && Files.isRegularFile(resourcesPath)) {
                payloadPath = resourcesPath;
            }

            Path rootPath = Paths.get(payloadFileName).toAbsolutePath().normalize();
            if (Files.exists(rootPath) && Files.isRegularFile(rootPath) && payloadPath == null) {
                payloadPath = rootPath;
            }

            if (payloadPath == null || !Files.exists(payloadPath) || !Files.isRegularFile(payloadPath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Payload file not found"));
            }

            long fileSize = Files.size(payloadPath);
            Resource fileResource = new FileSystemResource(payloadPath);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header("Content-Disposition", String.format("attachment; filename=\"%s\"", payloadFileName))
                    .header("Content-Length", String.valueOf(fileSize))
                    .header("Cache-Control", "no-cache")
                    .body(fileResource);
        } catch (IOException exception) {
            if (!response.isCommitted()) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to download payload file"));
            }
        } catch (Exception exception) {
            if (!response.isCommitted()) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error"));
            }
        }

        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Internal server error"));
    }
}
