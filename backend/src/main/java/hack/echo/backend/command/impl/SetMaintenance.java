package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;

import static hack.echo.backend.logger.Ansi.Color.*;

public class SetMaintenance extends Command {

    public SetMaintenance() {
        super("setmaintenance", "Set maintenance mode on the server");
    }

    @Override
    public void execute(String[] args) {
         if (args.length != 1) {
             Backend.logger.error(String.format("Usage: setmaintenance %s", new Ansi().c(RED).s("<true/false>")));
             return;
         }
         if (!args[0].equalsIgnoreCase("true") && !args[0].equalsIgnoreCase("false")) {
             Backend.logger.error(String.format("Invalid maintenance status: %s. Must be true or false.", new Ansi().c(RED).s(args[0]).c(BRIGHT_RED)));
             return;
         }

        Backend.getInstance().setMaintenanceMode(Boolean.parseBoolean(args[0]));
        Backend.logger.info(String.format("Maintenance mode has been set to %s", new Ansi().c(WHITE).s(args[0])));
    }
}
