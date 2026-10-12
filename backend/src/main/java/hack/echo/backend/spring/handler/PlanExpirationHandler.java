package hack.echo.backend.spring.handler;

import hack.echo.backend.Backend;
import hack.echo.backend.config.Config;
import hack.echo.backend.network.User;
import hack.echo.backend.spring.service.StripeSubscriptionSync;
import hack.echo.backend.spring.task.Task;
import hack.echo.backend.util.DiscordUtil;
import hack.echo.backend.util.MongoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PlanExpirationHandler implements Task {

    @Autowired
    private StripeSubscriptionSync stripeSubscriptionSync;

    @Override
    public long getIntervalMillis() {
        return 60L * 60L * 1000L;
    }

    @Override
    public void cleanup() {
        Config.DiscordConfig discord = Backend.getInstance().getConfig().discord;
        for (User user : MongoUtil.getExpiredUsers()) {
            User refreshedUser = stripeSubscriptionSync.refreshUserIfStripeCanFixAccess(user);
            if (stripeSubscriptionSync.hasActiveLicense(refreshedUser)) {
                continue;
            }

            DiscordUtil.removeRoleFromMember(user.getDiscordId(), discord.customerRoleId, discord);
            MongoUtil.updatePlan(user.getUsername(), "N/A", null);
        }
    }
}
