package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;
import hack.echo.backend.util.MongoUtil;

import static hack.echo.backend.logger.Ansi.Color.*;

public class ResetVL extends Command {

    public ResetVL() {
        super("resetvl", "Resets a user's violation level.");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            Backend.logger.error(String.format("Usage: resetvl %s", new Ansi().c(RED).s("<user>")));
            return;
        }

        String username = args[0];
        if (MongoUtil.setVL(username, 0)) {
            Backend.logger.info(String.format("Successful VL reset for user %s", new Ansi().c(WHITE).s(username)));
        } else {
            Backend.logger.error(String.format("User %s is not found", new Ansi().c(RED).s(username).c(BRIGHT_RED)));
        }
    }
}
