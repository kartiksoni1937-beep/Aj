package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;

import static hack.echo.backend.logger.Ansi.Color.*;

public class Kick extends Command {

    public Kick() {
        super("kick", "Kick a user from the client");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            Backend.logger.error(String.format("Usage: kick %s", new Ansi().c(RED).s("<user> [disconnect]")));
            return;
        }

        String user = args[0];
        if (Backend.getInstance().getServer().kickClient(user, args.length == 2 && args[1].equalsIgnoreCase("true"))) {
            Backend.logger.info(String.format("User %s has been kicked", new Ansi().c(WHITE).s(user)));
        } else {
            Backend.logger.error(String.format("User %s is not found", new Ansi().c(RED).s(user).c(BRIGHT_RED)));
        }
    }
}