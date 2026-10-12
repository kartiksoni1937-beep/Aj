package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;
import hack.echo.backend.util.MongoUtil;

import static hack.echo.backend.logger.Ansi.Color.*;

public class SetBeta extends Command {

    public SetBeta() {
        super("setbeta", "Set beta status for a user");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 2) {
            Backend.logger.error(String.format("Usage: setbeta %s", new Ansi().c(RED).s("<user> <true/false>")));
            return;
        }
        if (!args[1].equalsIgnoreCase("true") && !args[1].equalsIgnoreCase("false")) {
            Backend.logger.error(String.format("Invalid beta status: %s. Must be true or false.", new Ansi().c(RED).s(args[1]).c(BRIGHT_RED)));
            return;
        }
        String username = args[0];
        if (MongoUtil.setBeta("username", username, Boolean.parseBoolean(args[1]))) {
            Backend.logger.info(String.format("Successfully set beta status for user %s to %s", new Ansi().c(WHITE).s(username), new Ansi().c(WHITE).s(args[1])));
        } else {
            Backend.logger.error(String.format("User %s is not found", new Ansi().c(RED).s(username).c(BRIGHT_RED)));
        }
    }
}
