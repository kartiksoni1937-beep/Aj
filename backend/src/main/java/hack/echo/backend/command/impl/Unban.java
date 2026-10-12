package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;
import hack.echo.backend.util.MongoUtil;

import static hack.echo.backend.logger.Ansi.Color.*;

public class Unban extends Command {

    public Unban() {
        super("unban", "Unban a user");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            Backend.logger.error(String.format("Usage: unban %s", new Ansi().c(RED).s("<user>")));
            return;
        }

        String username = args[0];
        int result = MongoUtil.unbanUser("username", username);
        switch (result) {
            case 0 -> Backend.logger.error(String.format("User %s is not found", new Ansi().c(RED).s(username).c(BRIGHT_RED)));
            case 1 -> {
                MongoUtil.setVL(username, 0);
                Backend.logger.info(String.format("User %s has been unbanned", new Ansi().c(WHITE).s(username)));
            }
            case 2 -> Backend.logger.error(String.format("User %s is not banned", new Ansi().c(RED).s(username).c(BRIGHT_RED)));
        }
    }
}
