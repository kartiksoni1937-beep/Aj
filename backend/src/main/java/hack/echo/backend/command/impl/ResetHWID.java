package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;
import hack.echo.backend.util.MongoUtil;

import static hack.echo.backend.logger.Ansi.Color.BRIGHT_RED;

public class ResetHWID extends Command {

    public ResetHWID() {
        super("resethwid", "Resets a user's HWID");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            Backend.logger.error(String.format("Usage: resethwid %s", new Ansi().c(Ansi.Color.RED).s("<user>")));
            return;
        }

        String username = args[0];
        if (MongoUtil.removeField(username, "hwid")) {
            Backend.logger.info(String.format("Successful HWID reset for user %s", new Ansi().c(Ansi.Color.WHITE).s(username)));
        } else {
            Backend.logger.error(String.format("User %s is not found", new Ansi().c(Ansi.Color.RED).s(username).c(BRIGHT_RED)));
        }
    }
}
