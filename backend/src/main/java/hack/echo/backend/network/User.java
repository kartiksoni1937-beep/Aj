package hack.echo.backend.network;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@Getter @RequiredArgsConstructor
public class User {

    private final String id;
    private final String username;
    private final String realUsername;
    private final String email;
    private final int userNumber;
    private final String password;
    private final String loadedConfig;
    private final String hwid;
    private final String plan;
    private final Date exparationDate;
    private final boolean admin;
    private final boolean beta;
    private final boolean banned;
    private final String banReason;
    private final String discordId;
    private final String discordUsername;
    private final int vl;
    private final String lastIp;
    private final Date lastHwidReset;
    private final Date lastAttempt;
    private final String lastAttemptIp;
    private final Date lastFailedAttempt;
    private final String stripeCustomerId;
    private final String stripeSubscriptionId;
    private final String stripeSubscriptionStatus;
    private final boolean stripeCollectionPaused;
    private final boolean premiumAccess;
    private final Date createdAt;
}
