package hack.echo.client;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Auth removed: PreLaunch previously constructed the AuthManager and could open
// a Swing login prompt. There is no auth anymore, so this is a straight no-op
// and the game boots directly.
public class EchoPreLaunch implements PreLaunchEntrypoint {

    private static final Logger LOGGER = LoggerFactory.getLogger("echo-prelaunch");

    @Override
    public void onPreLaunch() {
        LOGGER.info("Auth disabled - launching directly");
    }
}
