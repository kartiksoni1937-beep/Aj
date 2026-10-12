package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;
import hack.echo.backend.util.MongoUtil;

import static hack.echo.backend.logger.Ansi.Color.BRIGHT_RED;
import static hack.echo.backend.logger.Ansi.Color.RED;

public class SetHWID extends Command {

    public SetHWID() {
        super("sethwid", "Set a user's HWID");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 2) {
            Backend.logger.error(String.format("Usage: sethwid %s", new Ansi().c(Ansi.Color.RED).s("<user> <hwid>")));
            return;
        }

        String username = args[0];
        if (MongoUtil.setHWID("username", username, args[1])) {
            Backend.logger.info(String.format("Successfully set HWID for user %s to %s", new Ansi().c(Ansi.Color.WHITE).s(username), new Ansi().c(Ansi.Color.WHITE).s(args[1])));
        } else {
            Backend.logger.error(String.format("User %s is not found", new Ansi().c(RED).s(username).c(BRIGHT_RED)));
        }
    }
}
