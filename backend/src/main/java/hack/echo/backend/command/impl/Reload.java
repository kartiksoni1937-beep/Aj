package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;

public class Reload extends Command {

    public Reload() {
        super("reload", "Reloads the backend configuration");
    }

    @Override
    public void execute(String[] args) {
        Backend.getInstance().reloadConfig();
        Backend.logger.info("Configuration reloaded");
    }
}
