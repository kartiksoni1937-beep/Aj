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

public class ResumeSub extends Command {

    public ResumeSub() {
        super("resume", "Resume a paused Stripe subscription for a user");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            Backend.logger.error(String.format("Usage: resume %s", new Ansi().c(RED).s("<user>")));
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

            if (!"paused".equals(subscription.getStatus())) {
                Backend.logger.error("Subscription is not paused");
                return;
            }

            Subscription resumedSubscription = stripeAdmin.resume(user);
            Backend.logger.info(String.format("Resumed subscription %s with status %s",
                    new Ansi().c(WHITE).s(resumedSubscription.getId()),
                    new Ansi().c(WHITE).s(resumedSubscription.getStatus())));
        } catch (Exception exception) {
            Backend.logger.error("Failed to resume subscription", exception);
        }
    }
}
