package hack.echo.backend.spring.controller;

import com.google.gson.JsonObject;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.model.StripeObject;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.billingportal.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import hack.echo.backend.Backend;
import hack.echo.backend.config.Config;
import hack.echo.backend.network.User;
import hack.echo.backend.spring.auth.RequireAuth;
import hack.echo.backend.spring.ratelimit.RateLimit;
import hack.echo.backend.spring.service.StripeSubscriptionAdmin;
import hack.echo.backend.spring.service.StripeSubscriptionSync;
import hack.echo.backend.util.DiscordUtil;
import hack.echo.backend.util.MongoUtil;
import hack.echo.backend.util.WebUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.print.Doc;
import java.util.Date;
import java.util.Map;

/**
 * REST endpoints and Stripe webhook handlers for the payments subsystem.
 *
 * <p>Exposes Checkout Session creation (lifetime + monthly), customer portal access,
 * subscription pause/resume, and the inbound Stripe webhook. The webhook records
 * payment docs into the unified {@code payments} collection and reconciles user
 * access state for completed payments, refunds, and disputes.
 */
@Controller
@RequestMapping("/api/payments")
public class Payments {

    private static volatile String lifetimePriceIdCache;
    private static volatile String monthlyPriceIdCache;

    @Autowired
    private StripeSubscriptionSync stripeSubscriptionSync;

    @Autowired
    private StripeSubscriptionAdmin stripeSubscriptionAdmin;

    static {
        String secretKey = Backend.getInstance().getConfig().stripe.secretKey;
        if (secretKey != null && !secretKey.isEmpty()) {
            Stripe.apiKey = secretKey;
        }
    }

    /**
     * Creates a one-time Stripe Checkout Session for the lifetime plan and returns its
     * hosted URL. The caller's user record is bound to a Stripe Customer (creating one if
     * needed) before the session is opened.
     */
    @RequireAuth
    @RateLimit(limit = 5)
    @PostMapping(path = "/lifetime", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createLifetimeCheckout(HttpServletRequest request) {
        try {
            if (Backend.getInstance().getConfig().stripe.secretKey.isEmpty()) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Stripe is not configured"));
            }

            User user = MongoUtil.getUser("username", WebUtil.getUsername(request.getHeader("Authorization").replace("Bearer ", "")));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "User not found"));
            }

            String customerId = ensureStripeCustomer(user);

            ensurePricesExist();

            SessionCreateParams.LineItem lineItem = buildLineItem(true);
            com.stripe.model.checkout.Session session = com.stripe.model.checkout.Session.create(
                    SessionCreateParams.builder()
                            .setCustomer(customerId)
                            .addLineItem(lineItem)
                            .setMode(SessionCreateParams.Mode.PAYMENT)
                            .setSuccessUrl(Backend.getInstance().getConfig().frontendUrl + "/dashboard?payment=success&session_id={CHECKOUT_SESSION_ID}")
                            .setCancelUrl(Backend.getInstance().getConfig().frontendUrl + "/dashboard?payment=cancelled")
                            .setClientReferenceId(user.getId())
                            .putMetadata("userId", user.getId())
                            .putMetadata("plan", "lifetime")
                            .putMetadata("type", "one-time")
                            .build()
            );

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("sessionId", session.getId());
            response.addProperty("url", session.getUrl());
            return ResponseEntity.ok(response.toString());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to create checkout session"));
        }
    }

    /**
     * Creates a recurring Stripe Checkout Session for the monthly premium plan.
     * Rejects the request if the caller already has an active or trialing subscription.
     */
    @RequireAuth
    @RateLimit(limit = 5)
    @PostMapping(path = "/monthly", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createMonthlySubscription(HttpServletRequest request) {
        try {
            if (Backend.getInstance().getConfig().stripe.secretKey.isEmpty()) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Stripe is not configured"));
            }

            User user = MongoUtil.getUser("username", WebUtil.getUsername(request.getHeader("Authorization").replace("Bearer ", "")));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "User not found"));
            }

            if (user.getStripeSubscriptionId() != null && !user.getStripeSubscriptionId().isEmpty()) {
                try {
                    Subscription subscription = Subscription.retrieve(user.getStripeSubscriptionId());
                    if ("active".equals(subscription.getStatus()) || "trialing".equals(subscription.getStatus())) {
                        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You already have an active subscription"));
                    }
                } catch (Exception ignored) {}
            }

            String customerId = ensureStripeCustomer(user);

            ensurePricesExist();

            SessionCreateParams.LineItem lineItem = buildLineItem(false);
            com.stripe.model.checkout.Session session = com.stripe.model.checkout.Session.create(
                    SessionCreateParams.builder()
                            .setCustomer(customerId)
                            .addLineItem(lineItem)
                            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                            .setSuccessUrl(Backend.getInstance().getConfig().frontendUrl + "/dashboard?payment=success&session_id={CHECKOUT_SESSION_ID}")
                            .setCancelUrl(Backend.getInstance().getConfig().frontendUrl + "/dashboard?payment=cancelled")
                            .setClientReferenceId(user.getId())
                            .putMetadata("userId", user.getId())
                            .putMetadata("plan", "premium")
                            .putMetadata("type", "subscription")
                            .build()
            );

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("sessionId", session.getId());
            response.addProperty("url", session.getUrl());
            return ResponseEntity.ok(response.toString());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to create subscription"));
        }
    }

    /**
     * Stripe webhook entrypoint. Verifies the signature against the configured webhook
     * secret, then dispatches to the appropriate handler based on the event type.
     * Unhandled event types are silently acknowledged with 200.
     */
    @RateLimit(limit = 20)
    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handleWebhook(@RequestBody String payload, HttpServletRequest request) {
        try {
            if (Backend.getInstance().getConfig().stripe.secretKey.isEmpty()) {
                return ResponseEntity.internalServerError().body(Map.of("error", "Stripe not configured"));
            }

            String signature = request.getHeader("Stripe-Signature");
            String webhookSecret = Backend.getInstance().getConfig().stripe.webhookSecret;
            if (webhookSecret == null || webhookSecret.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Webhook secret not configured"));
            }

            Event event;
            try {
                event = Webhook.constructEvent(payload, signature, webhookSecret);
            } catch (SignatureVerificationException exception) {
                return ResponseEntity.badRequest().body(Map.of("error", "Webhook Error: " + exception.getMessage()));
            }

            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            StripeObject stripeObject = deserializer.getObject().orElse(null);
            if (stripeObject == null) {
                try {
                    stripeObject = deserializer.deserializeUnsafe();
                } catch (Exception ignored) {}
            }

            if (stripeObject == null) {
                return ResponseEntity.ok(Map.of("received", true));
            }

            switch (event.getType()) {
                case "checkout.session.completed" -> handleCheckoutCompleted((com.stripe.model.checkout.Session) stripeObject);
                case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.paused", "customer.subscription.resumed" -> handleSubscriptionUpdate((Subscription) stripeObject);
                case "customer.subscription.deleted" -> handleSubscriptionCancelled((Subscription) stripeObject);
                case "invoice.paid", "invoice.payment_succeeded" -> handleInvoicePaymentSucceeded((Invoice) stripeObject);
                case "charge.refunded" -> handleChargeRefunded((Charge) stripeObject);
                case "charge.dispute.created" -> handleChargeDisputed((Dispute) stripeObject);
                default -> {}
            }

            return ResponseEntity.ok(Map.of("received", true));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Webhook handler failed"));
        }
    }

    /**
     * Returns a Stripe Customer Portal session URL for the caller. The frontend redirects
     * the user there to manage payment methods, view invoices, and cancel subscriptions.
     */
    @RequireAuth
    @RateLimit(limit = 5)
    @PostMapping(path = "/portal", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getCustomerPortal(HttpServletRequest request) {
        try {
            if (Backend.getInstance().getConfig().stripe.secretKey.isEmpty()) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Stripe is not configured"));
            }

            User user = MongoUtil.getUser("username", WebUtil.getUsername(request.getHeader("Authorization").replace("Bearer ", "")));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "User not found"));
            }

            if (user.getStripeCustomerId() == null || user.getStripeCustomerId().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No active subscription found"));
            }

            Session session = Session.create(com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(user.getStripeCustomerId())
                    .setReturnUrl(Backend.getInstance().getConfig().frontendUrl + "/dashboard")
                    .build());

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("url", session.getUrl());
            return ResponseEntity.ok(response.toString());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to create customer portal session"));
        }
    }

    /**
     * Returns the payment and session status for a Stripe Checkout Session. Used by the
     * frontend success page to poll completion after the post-checkout redirect.
     */
    @RateLimit(limit = 20)
    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> checkPaymentStatus(HttpServletRequest request) {
        try {
            if (Backend.getInstance().getConfig().stripe.secretKey.isEmpty()) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Stripe is not configured"));
            }

            String sessionId = request.getParameter("sessionId");
            if (sessionId == null || sessionId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Session ID is required"));
            }

            com.stripe.model.checkout.Session session = com.stripe.model.checkout.Session.retrieve(sessionId);
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("paymentStatus", session.getPaymentStatus());
            response.addProperty("status", session.getStatus());
            return ResponseEntity.ok(response.toString());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to check payment status"));
        }
    }

    /**
     * Returns the configured price tiers for both Stripe and Oxapay, formatted for display.
     */
    @RateLimit(limit = 20)
    @GetMapping(path = "/prices")
    public ResponseEntity<?> prices() {
        try {
            Config config = Backend.getInstance().getConfig();
            Config.StripeConfig stripeConfig = config.stripe;
            Config.OxapayConfig oxapayConfig = config.oxapay;

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            JsonObject prices = new JsonObject();
            JsonObject stripePrices = new JsonObject();
            JsonObject stripeLifetime = new JsonObject();
            stripeLifetime.addProperty("amount", stripeConfig.lifetime.price);
            stripeLifetime.addProperty("currency", stripeConfig.lifetime.currency);
            stripeLifetime.addProperty("formatted", WebUtil.toFixed(stripeConfig.lifetime.price / 100d, 2));
            stripePrices.add("lifetime", stripeLifetime);
            JsonObject stripeMonthly = new JsonObject();
            stripeMonthly.addProperty("amount", stripeConfig.monthly.price);
            stripeMonthly.addProperty("currency", stripeConfig.monthly.currency);
            stripeMonthly.addProperty("formatted", WebUtil.toFixed(stripeConfig.monthly.price / 100d, 2));
            stripePrices.add("monthly", stripeMonthly);
            prices.add("stripe", stripePrices);

            JsonObject oxapayPrices = new JsonObject();
            JsonObject oxapayLifetime = new JsonObject();
            oxapayLifetime.addProperty("amount", oxapayConfig.lifetime.price);
            oxapayLifetime.addProperty("currency", oxapayConfig.lifetime.currency);
            oxapayLifetime.addProperty("formatted", WebUtil.toFixed(oxapayConfig.lifetime.price, 2));
            oxapayPrices.add("lifetime", oxapayLifetime);
            JsonObject oxapayMonthly = new JsonObject();
            oxapayMonthly.addProperty("amount", oxapayConfig.monthly.price);
            oxapayMonthly.addProperty("currency", oxapayConfig.monthly.currency);
            oxapayMonthly.addProperty("formatted", WebUtil.toFixed(oxapayConfig.monthly.price, 2));
            oxapayPrices.add("monthly", oxapayMonthly);
            prices.add("oxapay", oxapayPrices);
            response.add("prices", prices);
            return ResponseEntity.ok(response.toString());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to fetch prices"));
        }
    }

    /**
     * Migrates the caller's subscription to Stripe's flexible billing mode, a prerequisite
     * for the pause/resume endpoints. No-op if the subscription is already on flexible mode.
     */
    @RequireAuth
    @RateLimit(limit = 5)
    @PostMapping(path = "/subscription/migrate-flexible", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> migrateSubscriptionToFlexible(HttpServletRequest request) {
        try {
            User user = getAuthenticatedUser(request);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "User not found"));
            }

            Subscription subscription = getManagedSubscription(user);
            if (subscription == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No Stripe subscription found"));
            }

            if (stripeSubscriptionAdmin.isFlexibleBillingMode(subscription)) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Subscription already uses flexible billing mode", "subscriptionId", subscription.getId()));
            }

            Subscription migratedSubscription = stripeSubscriptionAdmin.migrateToFlexible(user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Subscription migrated to flexible billing mode",
                    "subscriptionId", migratedSubscription.getId(),
                    "status", migratedSubscription.getStatus()
            ));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to migrate subscription to flexible billing mode"));
        }
    }

    /**
     * Pauses the caller's flexible-mode subscription. Rejects the request if the
     * subscription is still on the legacy billing mode; the frontend should call
     * {@link #migrateSubscriptionToFlexible} first.
     */
    @RequireAuth
    @RateLimit(limit = 5)
    @PostMapping(path = "/subscription/pause", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pauseSubscription(HttpServletRequest request) {
        try {
            User user = getAuthenticatedUser(request);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "User not found"));
            }

            Subscription subscription = getManagedSubscription(user);
            if (subscription == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No Stripe subscription found"));
            }

            if ("paused".equals(subscription.getStatus())) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Subscription is already paused", "subscriptionId", subscription.getId()));
            }

            if (!stripeSubscriptionAdmin.isFlexibleBillingMode(subscription)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Subscription must use flexible billing mode before it can be paused"));
            }

            Subscription pausedSubscription = stripeSubscriptionAdmin.pause(user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Subscription paused",
                    "subscriptionId", pausedSubscription.getId(),
                    "status", pausedSubscription.getStatus()
            ));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to pause subscription"));
        }
    }

    /**
     * Resumes the caller's previously-paused subscription without prorating or
     * shifting the billing cycle anchor.
     */
    @RequireAuth
    @RateLimit(limit = 5)
    @PostMapping(path = "/subscription/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resumeSubscription(HttpServletRequest request) {
        try {
            User user = getAuthenticatedUser(request);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "User not found"));
            }

            Subscription subscription = getManagedSubscription(user);
            if (subscription == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No Stripe subscription found"));
            }

            if (!"paused".equals(subscription.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Subscription is not paused"));
            }

            Subscription resumedSubscription = stripeSubscriptionAdmin.resume(user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Subscription resume requested",
                    "subscriptionId", resumedSubscription.getId(),
                    "status", resumedSubscription.getStatus()
            ));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to resume subscription"));
        }
    }

    private synchronized void ensurePricesExist() throws StripeException {
        Config.StripeConfig stripeConfig = Backend.getInstance().getConfig().stripe;

        if (lifetimePriceIdCache == null || lifetimePriceIdCache.isEmpty()) {
            lifetimePriceIdCache = resolveOrCreatePrice(stripeConfig.lifetime.priceId, stripeConfig.lifetime.currency, stripeConfig.lifetime.price, "Echo Client - Lifetime License", false);
        }

        if (monthlyPriceIdCache == null || monthlyPriceIdCache.isEmpty()) {
            monthlyPriceIdCache = resolveOrCreatePrice(stripeConfig.monthly.priceId, stripeConfig.monthly.currency, stripeConfig.monthly.price, "Echo Client - Monthly Premium", true);
        }
    }

    private String resolveOrCreatePrice(String configuredPriceId, String currency, long amount, String productName, boolean recurring) throws StripeException {
        if (configuredPriceId != null && !configuredPriceId.isEmpty()) {
            return configuredPriceId;
        }

        PriceCreateParams.Builder builder = PriceCreateParams.builder().setCurrency(currency).setUnitAmount(amount).setProductData(PriceCreateParams.ProductData.builder().setName(productName).build());
        if (recurring) {
            builder.setRecurring(PriceCreateParams.Recurring.builder().setInterval(PriceCreateParams.Recurring.Interval.MONTH).build());
        }

        return Price.create(builder.build()).getId();
    }

    /**
     * Returns the Stripe Customer ID for the user, creating one and persisting it on
     * the user record if absent. The Stripe Customer is created with {@code userId} and
     * {@code username} metadata for back-traceability.
     */
    private String ensureStripeCustomer(User user) throws StripeException {
        String customerId = user.getStripeCustomerId();
        if (customerId != null && !customerId.isEmpty()) {
            return customerId;
        }

        Customer customer = Customer.create(CustomerCreateParams.builder().putMetadata("userId", user.getId()).putMetadata("username", user.getUsername()).build());
        customerId = customer.getId();
        MongoUtil.updateStripeCustomerId(user.getUsername(), customerId, null);
        return customerId;
    }

    private SessionCreateParams.LineItem buildLineItem(boolean lifetime) {
        Config.StripeConfig stripeConfig = Backend.getInstance().getConfig().stripe;
        Config.StripeConfig.Price priceConfig = lifetime ? stripeConfig.lifetime : stripeConfig.monthly;
        String cachedPriceId = lifetime ? lifetimePriceIdCache : monthlyPriceIdCache;
        String effectivePriceId = (cachedPriceId != null && !cachedPriceId.isEmpty()) ? cachedPriceId : priceConfig.priceId;

        if (effectivePriceId != null && !effectivePriceId.isEmpty()) {
            return SessionCreateParams.LineItem.builder().setPrice(effectivePriceId).setQuantity(1L).build();
        }

        SessionCreateParams.LineItem.PriceData.ProductData productData = SessionCreateParams.LineItem.PriceData.ProductData.builder().setName(lifetime ? "Echo Client - Lifetime License" : "Echo Client - Monthly Premium").build();
        SessionCreateParams.LineItem.PriceData.Builder priceData = SessionCreateParams.LineItem.PriceData.builder().setCurrency(priceConfig.currency).setUnitAmount((long) priceConfig.price).setProductData(productData);

        if (!lifetime) {
            priceData.setRecurring(SessionCreateParams.LineItem.PriceData.Recurring.builder().setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH).build());
        }

        return SessionCreateParams.LineItem.builder().setQuantity(1L).setPriceData(priceData.build()).build();
    }

    /**
     * Processes a completed Checkout Session: records the payment doc, then either
     * applies lifetime state (with cancel-at-period-end scheduling on any linked
     * subscription) or syncs subscription state for recurring plans.
     */
    private void handleCheckoutCompleted(com.stripe.model.checkout.Session session) {
        try {
            String userId = session.getMetadata() != null && session.getMetadata().get("userId") != null ? session.getMetadata().get("userId") : session.getClientReferenceId();
            if (userId == null || userId.isEmpty()) {
                return;
            }

            User user = MongoUtil.getUser("_id", new ObjectId(userId));
            if (user == null) {
                return;
            }

            recordStripeCheckoutPayment(user, session);

            if ("payment".equals(session.getMode())) {
                MongoUtil.updatePlan(user.getUsername(), "lifetime", null);
                Subscription subscription = stripeSubscriptionAdmin.scheduleCancellationAtPeriodEnd(user);

                if (subscription != null) {
                    MongoUtil.updateLifetimeStripeSubscriptionState(
                            user.getUsername(),
                            subscription.getCustomer(),
                            subscription.getId(),
                            subscription.getStatus(),
                            subscription.getPauseCollection() != null
                    );
                } else {
                    MongoUtil.clearLifetimeStripeSubscriptionState(user.getUsername(), session.getCustomer());
                }

                syncCustomerRole(user, true);
            } else if ("subscription".equals(session.getMode()) && session.getSubscription() != null) {
                Subscription subscription = Subscription.retrieve(session.getSubscription());
                stripeSubscriptionSync.applySubscriptionState(user, subscription);
            }
        } catch (Exception exception) {
            Backend.logger.error("[STRIPE] handleCheckoutCompleted failed for session " + session.getId(), exception);
        }
    }

    /**
     * Reconciles user access state when a subscription is created, updated, paused,
     * or resumed. Lookup is done by {@code stripe_customer_id} on the user record.
     */
    private void handleSubscriptionUpdate(Subscription subscription) {
        try {
            User user = MongoUtil.getUser("stripe_customer_id", subscription.getCustomer());
            if (user == null) {
                return;
            }

            stripeSubscriptionSync.applySubscriptionState(user, subscription);
        } catch (Exception exception) {
            Backend.logger.error("[STRIPE] subscription update failed for " + subscription.getId(), exception);
        }
    }

    /**
     * Handles a subscription-deleted event. Lifetime users keep their access (the
     * deleted subscription was a cancel-at-period-end tracking shell), while subscription
     * users have their premium access revoked and Discord role removed.
     */
    private void handleSubscriptionCancelled(Subscription subscription) {
        try {
            User user = MongoUtil.getUser("stripe_customer_id", subscription.getCustomer());
            if (user == null) {
                return;
            }

            if ("lifetime".equalsIgnoreCase(user.getPlan())) {
                MongoUtil.clearLifetimeStripeSubscriptionState(user.getUsername(), subscription.getCustomer());
                syncCustomerRole(user, true);
                return;
            }

            MongoUtil.updateStripeSubscriptionState(user.getUsername(), subscription.getCustomer(), null, subscription.getStatus(), false, false, null);
            syncCustomerRole(user, false);
        } catch (Exception ignored) {
            Backend.logger.error("[STRIPE] subscription cancel failed");
        }
    }

    /**
     * Records the invoice payment, then re-syncs the user's subscription state from
     * Stripe so access reflects the renewed period. Fires for both {@code invoice.paid}
     * and {@code invoice.payment_succeeded}; idempotent across redelivery.
     */
    private void handleInvoicePaymentSucceeded(Invoice invoice) {
        try {
            User user = MongoUtil.getUser("stripe_customer_id", invoice.getCustomer());
            if (user == null) {
                return;
            }

            recordStripeInvoicePayment(user, invoice);

            user = stripeSubscriptionSync.refreshUserFromStripe(user);
            if (user != null && stripeSubscriptionSync.hasActiveLicense(user)) {
                syncCustomerRole(user, true);
            }
        } catch (Exception exception) {
            Backend.logger.error("[STRIPE] invoice payment failed for " +  invoice.getId(), exception);
        }
    }

    /**
     * Marks the matching payment doc as refunded (full or partial) and revokes the
     * user's access when the charge is fully refunded. Falls back to customer-based
     * user lookup when no matching doc exists, so access can still be revoked even
     * if the audit trail is incomplete.
     */
    private void handleChargeRefunded(Charge charge) {
        try {
            Document payment = findPaymentForCharge(charge);
            User user = resolveUserForCharge(charge, payment);
            if (user == null) {
                Backend.logger.warn("[STRIPE] charge.refunded: unable to find user for charge " + charge.getId());
                return;
            }

            long charged = charge.getAmount() != null ? charge.getAmount() : 0L;
            long refunded = charge.getAmountRefunded() != null ? charge.getAmountRefunded() : 0L;
            boolean fullRefund = Boolean.TRUE.equals(charge.getRefunded()) || (charged > 0 && refunded >= charged);

            if (payment != null) {
                MongoUtil.markPaymentRefunded("_id", payment.getObjectId("_id"), refunded, fullRefund);
            }

            if (fullRefund) {
                revokeAccessForRefund(user, payment);
            }
        } catch (Exception exception) {
            Backend.logger.error("[STRIPE] handleChargeRefunded failed for charge " + charge.getId(), exception);
        }
    }

    /**
     * Marks the matching payment doc as disputed and revokes access immediately.
     * Disputes withhold funds pending resolution, so we treat them as full revocation
     * up front rather than waiting for the dispute outcome.
     */
    private void handleChargeDisputed(Dispute dispute) {
        try {
            Document payment = findPaymentForDispute(dispute);
            User user = resolveUserForDispute(dispute, payment);
            if (user == null) {
                Backend.logger.warn("[STRIPE] charge.dispute.created: unable to find user for dispute " + dispute.getId());
                return;
            }

            if (payment != null) {
                MongoUtil.markPaymentDisputed("_id", payment.getObjectId("_id"), dispute.getReason());
            }

            revokeAccessForRefund(user, payment);
            Backend.logger.warn("[STRIPE] dispute opened for user " + user.getUsername() + " (charge " + dispute.getCharge() + ", reason " + dispute.getReason() + ")");
        } catch (Exception exception) {
            Backend.logger.error("[STRIPE] handleChargeDisputed failed for dispute " + dispute.getId(), exception);
        }
    }

    /**
     * Locates the local payment doc for a Stripe Charge, preferring lookup by
     * {@code stripe_payment_intent_id} and falling back to {@code stripe_charge_id}.
     */
    private Document findPaymentForCharge(Charge charge) {
        if (charge.getPaymentIntent() != null) {
            Document doc = MongoUtil.getPayment("stripe_payment_intent_id", charge.getPaymentIntent());
            if (doc != null) {
                return doc;
            }
        }
        if (charge.getId() != null) {
            return MongoUtil.getPayment("stripe_charge_id", charge.getId());
        }
        return null;
    }

    /**
     * Locates the local payment doc for a Stripe Dispute via its
     * {@code stripe_payment_intent_id} or {@code stripe_charge_id}.
     */
    private Document findPaymentForDispute(Dispute dispute) {
        if (dispute.getPaymentIntent() != null) {
            Document doc = MongoUtil.getPayment("stripe_payment_intent_id", dispute.getPaymentIntent());
            if (doc != null) {
                return doc;
            }
        }
        if (dispute.getCharge() != null) {
            return MongoUtil.getPayment("stripe_charge_id", dispute.getCharge());
        }
        return null;
    }

    /**
     * Resolves the user a refunded charge belongs to, preferring the {@code user_id}
     * on the payment doc and falling back to a lookup by the charge's customer ID.
     */
    private User resolveUserForCharge(Charge charge, Document payment) {
        if (payment != null && payment.getString("user_id") != null) {
            User user = MongoUtil.getUser("_id", new ObjectId(payment.getString("user_id")));
            if (user != null) {
                return user;
            }
        }
        if (charge.getCustomer() != null) {
            return MongoUtil.getUser("stripe_customer_id", charge.getCustomer());
        }
        return null;
    }

    /**
     * Resolves the user a dispute belongs to using the {@code user_id} stored on the
     * matching payment doc. The dispute payload itself carries no customer reference.
     */
    private User resolveUserForDispute(Dispute dispute, Document payment) {
        if (payment != null && payment.getString("user_id") != null) {
            User user = MongoUtil.getUser("_id", new ObjectId(payment.getString("user_id")));
            if (user != null) {
                return user;
            }
        }
        return null;
    }

    /**
     * Remove premium access of user
     * @param user the user who will get their access revoked
     * @param payment
     */
    /**
     * Revokes the user's premium access (sets plan to {@code N/A}, clears subscription
     * tracking) and removes the customer Discord role. Shared by the refund and dispute handlers.
     */
    private void revokeAccessForRefund(User user, Document payment) {
        String customerId = payment != null ? payment.getString("stripe_customer_id") : user.getStripeCustomerId();
        MongoUtil.updateStripeSubscriptionState(user.getUsername(), customerId, null, user.getStripeSubscriptionStatus(), false, false, null);
        syncCustomerRole(user, false);
    }

    /**
     * Inserts a {@code provider: "stripe"} doc into the unified {@code payments}
     * collection for a completed Checkout Session. Idempotent: re-deliveries with the
     * same {@code stripe_session_id} are skipped.
     */
    private void recordStripeCheckoutPayment(User user, com.stripe.model.checkout.Session session) {
        try {
            if (session.getId() == null || MongoUtil.getPayment("stripe_session_id", session.getId()) == null) {
                return;
            }

            String plan = session.getMetadata() != null && session.getMetadata().get("plan") != null ? session.getMetadata().get("plan") : null;
            if (plan == null || plan.isEmpty()) {
                return;
            }

            Date now = new Date();
            Document payment = new Document();
            payment.put("provider", "stripe");
            payment.put("user_id", user.getId());
            payment.put("plan", plan);
            payment.put("amount", session.getAmountTotal());
            payment.put("currency", session.getCurrency());
            payment.put("status", session.getStatus());
            payment.put("stripe_sesson_id", session.getId());
            appendIfPresent(payment, "stripe_customer_id", session.getCustomer());
            appendIfPresent(payment, "stripe_subscription_id", session.getSubscription());
            appendIfPresent(payment, "stripe_invoice_id", session.getInvoice());
            appendIfPresent(payment, "stripe_payment_intent_id", session.getPaymentIntent());
            payment.append("created_at", now);
            MongoUtil.createPayment(payment);

        } catch (Exception exception) {
            Backend.logger.error("[STRIPE] recordStripeCheckoutPayment failed for session " + session.getId(), exception);
        }
    }

    /**
     * Inserts or upgrades a payment doc for a paid invoice. If a doc with the same
     * {@code stripe_invoice_id} already exists (e.g. created by
     * {@link #recordStripeCheckoutPayment} for the first invoice of a subscription),
     * its status is marked paid instead of inserting a duplicate.
     */
    private void recordStripeInvoicePayment(User user, Invoice invoice) {
        try {
            if (invoice.getId() == null) {
                return;
            }

            if (MongoUtil.getPayment("stripe_invoice_id", invoice.getId()) != null) {
                MongoUtil.markPaymentPaid("stripe_invoice_id", invoice.getId());
                return;
            }

            Date now = new Date();
            Document payment = new Document();
            payment.append("provider", "stripe");
            payment.append("user_id", user.getId());
            payment.append("plan", "premium");
            payment.append("amount", invoice.getAmountPaid());
            payment.append("currency", invoice.getCurrency());
            payment.append("status", "paid");
            payment.append("stripe_invoice_id", invoice.getId());
            appendIfPresent(payment, "stripe_customer_id", invoice.getCustomer());
            appendIfPresent(payment, "stripe_subscription_id", extractInvoiceSubscriptionId(invoice));
            payment.append("created_at", now);
            payment.append("paid_at", now);
            MongoUtil.createPayment(payment);
        } catch (Exception exception) {
            Backend.logger.error("[STRIPE] recordStripeInvoicePayment failed for invoice " + invoice.getId(), exception);
        }
    }

    private String extractInvoiceSubscriptionId(Invoice invoice) {
        try {
            if (invoice.getParent() == null || invoice.getParent().getSubscriptionDetails() == null) {
                return null;
            }
            return invoice.getParent().getSubscriptionDetails().getSubscription();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void appendIfPresent(Document document, String key, String value) {
        if (value != null && !value.isEmpty()) {
            document.append(key, value);
        }
    }

    private User getAuthenticatedUser(HttpServletRequest request) {
        if (Backend.getInstance().getConfig().stripe.secretKey.isEmpty()) {
            return null;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }

        String username = WebUtil.getUsername(authorization.replace("Bearer ", ""));
        return MongoUtil.getUser("username", username);
    }

    private Subscription getManagedSubscription(User user) throws StripeException {
        return stripeSubscriptionAdmin.getManagedSubscription(user);
    }

    /**
     * Synchronizes the Discord customer role for a user.
     *
     * @param user the user whose Discord role should be updated
     * @param grant true to add the customer role; false to remove it
     */
    private void syncCustomerRole(User user, boolean grant) {
        try {
            Config.DiscordConfig discord = Backend.getInstance().getConfig().discord;
            if (user.getDiscordId() == null || user.getDiscordId().isEmpty() || discord.customerRoleId == null || discord.customerRoleId.isEmpty()) {
                return;
            }

            if (grant) {
                DiscordUtil.addRoleToMember(user.getDiscordId(), discord.customerRoleId, discord);
            } else {
                DiscordUtil.removeRoleFromMember(user.getDiscordId(), discord.customerRoleId, discord);
            }
        } catch (Exception ignored) {}
    }
}