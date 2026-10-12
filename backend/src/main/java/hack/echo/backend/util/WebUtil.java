package hack.echo.backend.util;

import hack.echo.backend.Backend;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class WebUtil {

    private static final SecureRandom random = new SecureRandom();

    public static String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);

        Backend backend = Backend.getInstance();
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + backend.getConfig().jwtExpiration))
                .signWith(backend.getJwtKey(), Jwts.SIG.HS256)
                .compact();
    }

    public static boolean isTokenValid(String token) {
        try {
            return !extractAllClaims(token).getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public static String getUsername(String token) {
        try {
            return extractAllClaims(token).get("username", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(Backend.getInstance().getJwtKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static String post(String url, Map<String, String> params) {
        try {
            String formBody = params.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            Backend.logger.error("HTTP POST request failed", e);
            return "";
        }
    }

    public static String get(String url, String token) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json");

            if (token != null) {
                requestBuilder.header("Authorization", String.format("Bearer %s", token));
            }

            HttpRequest request = requestBuilder.GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            Backend.logger.error("HTTP GET request failed", e);
            return null;
        }
    }

    public static String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }

        return xfHeader.split(",")[0];
    }

    public static String parseTime(Date date) {
        if (date == null) return "";

        return date.toInstant().toString();
    }

    public static String toFixed(double num, int decimals) {
        return new BigDecimal(num).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    }

    public static String randomHex(int length) {
        byte[] bytes = new byte[length / 2];
        random.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    public static String bytesToHex(byte[] bytes) {
        final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static class SessionInfo {

        public int status;
        public int uid;
        public long timestamp;

        public SessionInfo(int status, int uid, long timestamp) {
            this.status = status;
            this.uid = uid;
            this.timestamp = timestamp;
        }
    }
}
