package hack.echo.backend.spring.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.net.ApiMode;
import com.stripe.net.ApiResource;
import com.stripe.net.BaseAddress;
import com.stripe.net.RequestOptions;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.SubscriptionMigrateParams;
import com.stripe.param.SubscriptionResumeParams;
import hack.echo.backend.Backend;
import hack.echo.backend.network.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@Component
public class StripeSubscriptionAdmin {

    private static final String STRIPE_PREVIEW_VERSION = "2026-02-25.preview";

    @Autowired
    private StripeSubscriptionSync stripeSubscriptionSync;

    public Subscription getManagedSubscription(User user) throws StripeException {
        if (user == null) {
            return null;
        }

        if (user.getStripeSubscriptionId() == null || user.getStripeSubscriptionId().isEmpty()) {
            return null;
        }

        ensureStripeConfigured();
        return Subscription.retrieve(user.getStripeSubscriptionId());
    }

    public boolean isFlexibleBillingMode(Subscription subscription) {
        if (subscription == null || subscription.getBillingMode() == null) {
            return false;
        }

        return "flexible".equals(subscription.getBillingMode().getType());
    }

    public Subscription migrateToFlexible(User user) throws StripeException {
        Subscription subscription = getManagedSubscription(user);
        if (subscription == null) {
            return null;
        }

        if (isFlexibleBillingMode(subscription)) {
            return subscription;
        }

        SubscriptionMigrateParams params = SubscriptionMigrateParams.builder()
                .setBillingMode(SubscriptionMigrateParams.BillingMode.builder()
                        .setType(SubscriptionMigrateParams.BillingMode.Type.FLEXIBLE)
                        .build())
                .build();

        Subscription migratedSubscription = subscription.migrate(params);
        stripeSubscriptionSync.applySubscriptionState(user, migratedSubscription);
        return migratedSubscription;
    }

    public Subscription pause(User user) throws StripeException {
        Subscription subscription = getManagedSubscription(user);
        if (subscription == null) {
            return null;
        }

        if ("paused".equals(subscription.getStatus())) {
            return subscription;
        }

        Subscription pausedSubscription = pauseStripeSubscription(subscription.getId());
        stripeSubscriptionSync.applySubscriptionState(user, pausedSubscription);
        return pausedSubscription;
    }

    public Subscription resume(User user) throws StripeException {
        Subscription subscription = getManagedSubscription(user);
        if (subscription == null) {
            return null;
        }

        if (!"paused".equals(subscription.getStatus())) {
            return subscription;
        }

        SubscriptionResumeParams params = SubscriptionResumeParams.builder()
                .setBillingCycleAnchor(SubscriptionResumeParams.BillingCycleAnchor.UNCHANGED)
                .setProrationBehavior(SubscriptionResumeParams.ProrationBehavior.NONE)
                .build();

        Subscription resumedSubscription = subscription.resume(params);
        stripeSubscriptionSync.applySubscriptionState(user, resumedSubscription);
        return resumedSubscription;
    }

    public Subscription scheduleCancellationAtPeriodEnd(User user) throws StripeException {
        Subscription subscription = getActiveSubscription(user);
        if (subscription == null) {
            return null;
        }

        if (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())) {
            return subscription;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("cancel_at_period_end", true);
        return subscription.update(params);
    }

    public void ensureStripeConfigured() {
        String secretKey = Backend.getInstance().getConfig().stripe.secretKey;
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalStateException("Stripe is not configured");
        }

        Stripe.apiKey = secretKey;
    }

    private Subscription getActiveSubscription(User user) throws StripeException {
        if (user == null) {
            return null;
        }

        ensureStripeConfigured();

        if (user.getStripeSubscriptionId() != null && !user.getStripeSubscriptionId().isEmpty()) {
            Subscription storedSubscription = Subscription.retrieve(user.getStripeSubscriptionId());
            if (hasBillingAccess(storedSubscription)) {
                return storedSubscription;
            }
        }

        if (user.getStripeCustomerId() == null || user.getStripeCustomerId().isEmpty()) {
            return null;
        }

        return Subscription.list(SubscriptionListParams.builder()
                        .setCustomer(user.getStripeCustomerId())
                        .setStatus(SubscriptionListParams.Status.ALL)
                        .setLimit(10L)
                        .build())
                .getData()
                .stream()
                .sorted(Comparator.comparingLong(Subscription::getCreated).reversed())
                .filter(this::hasBillingAccess)
                .findFirst()
                .orElse(null);
    }

    private boolean hasBillingAccess(Subscription subscription) {
        if (subscription == null) {
            return false;
        }

        String status = subscription.getStatus();
        return "active".equals(status) || "trialing".equals(status);
    }

    private Subscription pauseStripeSubscription(String subscriptionId) throws StripeException {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "subscription");

        String path = String.format("/v1/subscriptions/%s/pause", ApiResource.urlEncodeId(subscriptionId));
        return ApiResource.getGlobalResponseGetter().request(
                BaseAddress.API,
                ApiResource.RequestMethod.POST,
                path,
                params,
                Subscription.class,
                buildPreviewRequestOptions(),
                ApiMode.V1
        );
    }

    private RequestOptions buildPreviewRequestOptions() {
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder();
        RequestOptions.RequestOptionsBuilder.unsafeSetStripeVersionOverride(builder, STRIPE_PREVIEW_VERSION);
        return builder.build();
    }
}
