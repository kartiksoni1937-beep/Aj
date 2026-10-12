package hack.echo.backend.command.impl;

import com.stripe.model.Subscription;
import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;
import hack.echo.backend.network.User;
import hack.echo.backend.spring.WebAPI;
import hack.echo.backend.spring.service.StripeSubscriptionAdmin;
import hack.echo.backend.util.MongoUtil;

import static hack.echo.backend.logger.Ansi.Color.RED;
import static hack.echo.backend.logger.Ansi.Color.WHITE;

public class MigrateFlexible extends Command {

    public MigrateFlexible() {
        super("migrate-flexible", "Migrate a user's Stripe subscription to flexible billing mode");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            Backend.logger.error(String.format("Usage: migrate-flexible %s", new Ansi().c(RED).s("<user>")));
            return;
        }

        User user = MongoUtil.getUser("username", args[0]);
        if (user == null) {
            Backend.logger.error(String.format("User %s is not found", new Ansi().c(RED).s(args[0])));
            return;
        }

        try {
            StripeSubscriptionAdmin stripeAdmin = WebAPI.getBean(StripeSubscriptionAdmin.class);
            Subscription subscription = stripeAdmin.getManagedSubscription(user);
            if (subscription == null) {
                Backend.logger.error("User has no Stripe subscription");
                return;
            }

            if (stripeAdmin.isFlexibleBillingMode(subscription)) {
                Backend.logger.info(String.format("Subscription %s already uses flexible billing mode", new Ansi().c(WHITE).s(subscription.getId())));
                return;
            }

            Subscription migratedSubscription = stripeAdmin.migrateToFlexible(user);
            Backend.logger.info(String.format("Migrated subscription %s to flexible billing mode", new Ansi().c(WHITE).s(migratedSubscription.getId())));
        } catch (Exception exception) {
            Backend.logger.error("Failed to migrate subscription to flexible billing mode", exception);
        }
    }
}
