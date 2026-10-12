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
import jakarta.servlet.http.HttpServletRequest;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

@Controller
@RequestMapping("/api/payments/oxapay")
public class Oxapay {

    @RequireAuth
    @RateLimit(limit = 1)
    @PostMapping(path = "/lifetime", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createLifetimeInvoice(HttpServletRequest request) {
        return createInvoice(request, true);
    }

    @RequireAuth
    @RateLimit(limit = 1)
    @PostMapping(path = "/monthly", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createMonthlyInvoice(HttpServletRequest request) {
        return createInvoice(request, false);
    }

    @RateLimit(limit = 20)
    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> checkPaymentStatus(HttpServletRequest request) {
        try {
            String orderId = request.getParameter("orderId");
            if (orderId == null || orderId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Order ID is required"));
            }

            Document payment = MongoUtil.getPaymentByOrderId(orderId);
            if (payment == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Payment not found"));
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("status", payment.getString("status"));
            response.addProperty("trackId", payment.getString("track_id"));
            response.addProperty("paymentUrl", payment.getString("payment_url"));
            return ResponseEntity.ok(response.toString());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to check payment status"));
        }
    }

    @RateLimit(limit = 20)
    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handleWebhook(@RequestBody String rawBody, HttpServletRequest request) {
        try {
            Config.OxapayConfig oxapay = Backend.getInstance().getConfig().oxapay;
            if (oxapay.merchantApiKey == null || oxapay.merchantApiKey.isEmpty()) {
                return ResponseEntity.internalServerError().body("OxaPay not configured");
            }

            String hmacHeader = request.getHeader("hmac");
            if (hmacHeader == null || hmacHeader.isEmpty()) {
                hmacHeader = request.getHeader("HMAC");
            }
            if (hmacHeader == null || hmacHeader.isEmpty()) {
                return ResponseEntity.badRequest().body("Missing HMAC signature");
            }

            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(oxapay.merchantApiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] signature = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String calculatedHmac = WebUtil.bytesToHex(signature);
            if (!calculatedHmac.equalsIgnoreCase(hmacHeader)) {
                return ResponseEntity.badRequest().body("Invalid HMAC signature");
            }

            JsonObject data = JsonParser.parseString(rawBody).getAsJsonObject();
            String trackId = data.has("track_id") ? data.get("track_id").getAsString() : null;
            String status = data.has("status") ? data.get("status").getAsString() : null;
            if (trackId == null || status == null) {
                return ResponseEntity.badRequest().body("Missing required fields");
            }

            Document payment = MongoUtil.getPaymentByTrackId(trackId);
            if (payment == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Payment not found");
            }

            switch (status) {
                case "Paying" -> MongoUtil.updatePaymentStatus(trackId, "paying", null);
                case "Paid" -> {
                    if (!"paid".equalsIgnoreCase(payment.getString("status"))) {
                        MongoUtil.updatePaymentStatus(trackId, "paid", new Date());

                        User user = MongoUtil.getUser("_id", new ObjectId(payment.getString("user_id")));
                        if (user == null) {
                            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
                        }

                        Calendar calendar = Calendar.getInstance();
                        if ("premium".equalsIgnoreCase(payment.getString("plan"))) {
                            calendar.add(Calendar.DAY_OF_YEAR, 30);
                        }
                        Date expirationDate = calendar.getTime();
                        if ("lifetime".equalsIgnoreCase(payment.getString("plan"))) {
                            expirationDate = null;
                        }
                        String plan = "lifetime".equalsIgnoreCase(payment.getString("plan")) ? "lifetime" : "premium";
                        MongoUtil.updatePlan(user.getUsername(), plan, expirationDate);
                        Config.DiscordConfig discord = Backend.getInstance().getConfig().discord;
                        if (user.getDiscordId() != null && !user.getDiscordId().isEmpty() && discord.customerRoleId != null && !discord.customerRoleId.isEmpty()) {
                            DiscordUtil.addRoleToMember(user.getDiscordId(), discord.customerRoleId, discord);
                        }
                    }
                }
                case "Failed", "Expired" -> MongoUtil.updatePaymentStatus(trackId, status.toLowerCase(), null);
            }

            return ResponseEntity.ok("OK");
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body("Webhook handler failed");
        }
    }

    private ResponseEntity<?> createInvoice(HttpServletRequest request, boolean lifetime) {
        try {
            Config config = Backend.getInstance().getConfig();
            Config.OxapayConfig oxapay = config.oxapay;
            if (oxapay.merchantApiKey == null || oxapay.merchantApiKey.isEmpty()) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "OxaPay is not configured"));
            }

            User user = MongoUtil.getUser("username", WebUtil.getUsername(request.getHeader("Authorization").replace("Bearer ", "")));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "User not found"));
            }

            String plan = lifetime ? "lifetime" : "premium";
            Config.OxapayConfig.Price price = lifetime ? oxapay.lifetime : oxapay.monthly;
            long timestamp = System.currentTimeMillis();
            String random = WebUtil.randomHex(6).toUpperCase();
            String orderId = String.format("ECHO-%s-%d-%s", plan.toUpperCase(), timestamp, random);

            Document payment = new Document();
            payment.append("provider", "oxapay");
            payment.append("user_id", user.getId());
            payment.append("order_id", orderId);
            payment.append("plan", plan);
            payment.append("amount", price.price);
            payment.append("currency", price.currency);
            payment.append("status", "pending");
            payment.append("createdAt", new Date());
            MongoUtil.createPayment(payment);

            JsonObject invoiceData = new JsonObject();
            invoiceData.addProperty("amount", price.price);
            invoiceData.addProperty("currency", price.currency);
            invoiceData.addProperty("lifetime", 60);
            invoiceData.addProperty("fee_paid_by_payer", 0);
            invoiceData.addProperty("callback_url", String.format("%s/api/payments/oxapay/webhook", config.backendUrl));
            invoiceData.addProperty("return_url", String.format("%s/dashboard?payment=success&order_id=%s", config.frontendUrl, orderId));
            invoiceData.addProperty("order_id", orderId);
            invoiceData.addProperty("thanks_message", lifetime
                    ? "Thank you for your purchase! Your lifetime license is being activated."
                    : "Thank you for your purchase! Your premium subscription is being activated.");
            invoiceData.addProperty("description", String.format("Echo Client - %s for %s", lifetime ? "Lifetime License" : "Monthly Premium", user.getUsername()));
            invoiceData.addProperty("sandbox", oxapay.sandbox);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.oxapay.com/v1/payment/invoice"))
                    .header("merchant_api_key", oxapay.merchantApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(invoiceData.toString()))
                    .build();

            String body = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString()).body();
            JsonObject response = JsonParser.parseString(body).getAsJsonObject();
            if (response.has("status") && response.get("status").getAsInt() == 200 && response.has("data")) {
                JsonObject data = response.getAsJsonObject("data");
                String trackId = data.get("track_id").getAsString();
                String paymentUrl = data.get("payment_url").getAsString();
                Date expiredAt = new Date(data.get("expired_at").getAsLong() * 1000L);
                MongoUtil.updatePaymentInvoiceData(orderId, trackId, paymentUrl, expiredAt);

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("trackId", trackId);
                result.addProperty("paymentUrl", paymentUrl);
                result.addProperty("orderId", orderId);
                return ResponseEntity.ok(result.toString());
            }

            MongoUtil.deletePaymentByOrderId(orderId);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", response.has("message") ? response.get("message").getAsString() : "Failed to create invoice"));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to create payment invoice"));
        }
    }
}
