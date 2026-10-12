package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;

public class Stop extends Command {

    public Stop() {
        super("stop", "Stop the backend");
    }

    @Override
    public void execute(String[] args) {
        Backend.logger.info("Stopping backend...");
        System.exit(0);
    }
}
