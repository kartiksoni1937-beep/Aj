package hack.echo.backend.spring.service;

import com.stripe.Stripe;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionCollection;
import com.stripe.param.SubscriptionListParams;
import hack.echo.backend.Backend;
import hack.echo.backend.config.Config;
import hack.echo.backend.network.User;
import hack.echo.backend.util.DiscordUtil;
import hack.echo.backend.util.MongoUtil;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Date;

@Component
public class StripeSubscriptionSync {

    public User refreshUserIfStripeCanFixAccess(User user) {
        if (user == null) {
            return null;
        }

        if (hasActiveLicense(user)) {
            return user;
        }

        if (!hasStripeIdentity(user)) {
            return user;
        }

        User refreshedUser = refreshUserFromStripe(user);
        if (refreshedUser == null) {
            return user;
        }

        return refreshedUser;
    }

    public User refreshUserFromStripe(User user) {
        if (user == null) {
            return null;
        }

        if (!isStripeConfigured()) {
            return user;
        }

        try {
            Subscription subscription = findSubscription(user);
            if (subscription == null) {
                return user;
            }

            applySubscriptionState(user, subscription);
            return MongoUtil.getUser("username", user.getUsername());
        } catch (Exception exception) {
            Backend.logger.error("[STRIPE] Failed to refresh subscription state", exception);
            return user;
        }
    }

    public boolean hasActiveLicense(User user) {
        if (user == null) {
            return false;
        }

        if (user.isPremiumAccess()) {
            return true;
        }

        if ("lifetime".equalsIgnoreCase(user.getPlan())) {
            return true;
        }

        if (!"premium".equalsIgnoreCase(user.getPlan())) {
            return false;
        }

        Date expirationDate = user.getExparationDate();
        if (expirationDate == null) {
            return true;
        }

        return expirationDate.getTime() >= System.currentTimeMillis();
    }

    private boolean hasStripeIdentity(User user) {
        return hasValue(user.getStripeSubscriptionId()) || hasValue(user.getStripeCustomerId());
    }

    private boolean isStripeConfigured() {
        String secretKey = Backend.getInstance().getConfig().stripe.secretKey;
        if (!hasValue(secretKey)) {
            return false;
        }

        Stripe.apiKey = secretKey;
        return true;
    }

    private Subscription findSubscription(User user) throws Exception {
        Subscription storedSubscription = null;
        if (hasValue(user.getStripeSubscriptionId())) {
            try {
                storedSubscription = Subscription.retrieve(user.getStripeSubscriptionId());
                if (hasPremiumAccess(storedSubscription)) {
                    return storedSubscription;
                }
            } catch (Exception ignored) {
            }
        }

        if (!hasValue(user.getStripeCustomerId())) {
            return storedSubscription;
        }

        SubscriptionCollection subscriptions = Subscription.list(SubscriptionListParams.builder()
                .setCustomer(user.getStripeCustomerId())
                .setStatus(SubscriptionListParams.Status.ALL)
                .setLimit(10L)
                .build());

        Subscription activeSubscription = subscriptions.getData().stream()
                .sorted(Comparator.comparingLong(Subscription::getCreated).reversed())
                .filter(this::hasPremiumAccess)
                .findFirst()
                .orElse(null);
        if (activeSubscription != null) {
            return activeSubscription;
        }

        if (storedSubscription != null) {
            return storedSubscription;
        }

        return subscriptions.getData().stream()
                .sorted(Comparator.comparingLong(Subscription::getCreated).reversed())
                .filter(this::isTerminalStatus)
                .findFirst()
                .orElse(null);
    }

    private boolean hasPremiumAccess(Subscription subscription) {
        String status = subscription.getStatus();
        if (!"active".equals(status) && !"trialing".equals(status)) {
            return false;
        }

        return !isCollectionPaused(subscription);
    }

    private boolean isTerminalStatus(Subscription subscription) {
        String status = subscription.getStatus();
        return "canceled".equals(status) || "unpaid".equals(status) || "incomplete_expired".equals(status);
    }

    private void grantPremiumAccess(User user, Subscription subscription) {
        boolean collectionPaused = isCollectionPaused(subscription);
        Long currentPeriodEnd = getCurrentPeriodEnd(subscription);
        Date expirationDate = currentPeriodEnd == null ? null : new Date(currentPeriodEnd * 1000L);
        MongoUtil.updateStripeSubscriptionState(user.getUsername(), subscription.getCustomer(), subscription.getId(), subscription.getStatus(), collectionPaused, true, expirationDate);
        syncCustomerRole(user, true);
    }

    private void revokePremiumAccess(User user, Subscription subscription) {
        MongoUtil.updateStripeSubscriptionState(user.getUsername(), subscription.getCustomer(), subscription.getId(), subscription.getStatus(), isCollectionPaused(subscription), false, null);
        syncCustomerRole(user, false);
    }

    public void applySubscriptionState(User user, Subscription subscription) {
        if (user == null || subscription == null) {
            return;
        }

        if ("lifetime".equalsIgnoreCase(user.getPlan())) {
            if (isTerminalStatus(subscription)) {
                MongoUtil.clearLifetimeStripeSubscriptionState(user.getUsername(), subscription.getCustomer());
                return;
            }

            MongoUtil.updateLifetimeStripeSubscriptionState(
                    user.getUsername(),
                    subscription.getCustomer(),
                    subscription.getId(),
                    subscription.getStatus(),
                    isCollectionPaused(subscription)
            );
            return;
        }

        if (hasPremiumAccess(subscription)) {
            grantPremiumAccess(user, subscription);
            return;
        }

        if ("paused".equals(subscription.getStatus())) {
            revokePremiumAccess(user, subscription);
            return;
        }

        if (isCollectionPaused(subscription)) {
            revokePremiumAccess(user, subscription);
            return;
        }

        if (isTerminalStatus(subscription)) {
            revokePremiumAccess(user, subscription);
        }
    }

    private boolean isCollectionPaused(Subscription subscription) {
        return subscription.getPauseCollection() != null;
    }

    private Long getCurrentPeriodEnd(Subscription subscription) {
        if (subscription == null || subscription.getItems() == null || subscription.getItems().getData() == null) {
            return null;
        }

        if (subscription.getItems().getData().isEmpty()) {
            return null;
        }

        return subscription.getItems().getData().get(0).getCurrentPeriodEnd();
    }

    private void syncCustomerRole(User user, boolean grant) {
        try {
            Config.DiscordConfig discord = Backend.getInstance().getConfig().discord;
            if (!hasValue(user.getDiscordId())) {
                return;
            }

            if (!hasValue(discord.customerRoleId)) {
                return;
            }

            if (grant) {
                DiscordUtil.addRoleToMember(user.getDiscordId(), discord.customerRoleId, discord);
                return;
            }

            DiscordUtil.removeRoleFromMember(user.getDiscordId(), discord.customerRoleId, discord);
        } catch (Exception exception) {
            Backend.logger.error("[STRIPE] Failed to sync Discord role", exception);
        }
    }

    private boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }
}
