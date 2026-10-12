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

public class PauseSub extends Command {

    public PauseSub() {
        super("pause", "Pause a user's Stripe subscription");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            Backend.logger.error(String.format("Usage: pause %s", new Ansi().c(RED).s("<user>")));
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

            if (!stripeAdmin.isFlexibleBillingMode(subscription)) {
                Backend.logger.error("Subscription must be migrated to flexible billing mode first");
                return;
            }

            Subscription pausedSubscription = stripeAdmin.pause(user);
            Backend.logger.info(String.format("Paused subscription %s with status %s",
                    new Ansi().c(WHITE).s(pausedSubscription.getId()),
                    new Ansi().c(WHITE).s(pausedSubscription.getStatus())));
        } catch (Exception exception) {
            Backend.logger.error("Failed to pause subscription", exception);
        }
    }
}
