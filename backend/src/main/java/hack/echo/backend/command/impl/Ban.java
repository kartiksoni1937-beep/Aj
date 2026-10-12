package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;
import hack.echo.backend.util.MongoUtil;

import static hack.echo.backend.logger.Ansi.Color.*;

public class Ban extends Command {

    public Ban() {
        super("ban", "Revoke access to license for a user");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            Backend.logger.error(String.format("Usage: ban %s", new Ansi().c(RED).s("<user>")));
            return;
        }

        String reason = "";
        if (args.length >= 2) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }
        String username = args[0];
        int result = MongoUtil.banUser("username", username, reason);
        switch (result) {
            case 0 -> Backend.logger.error(String.format("User %s is not found", new Ansi().c(RED).s(username).c(BRIGHT_RED)));
            case 1 -> {
                Backend.getInstance().getServer().kickClient(username, true);
                Backend.logger.info(String.format("User %s has been banned", new Ansi().c(WHITE).s(username)));
            }
            case 2 -> Backend.logger.error(String.format("User %s is already banned", new Ansi().c(RED).s(username).c(BRIGHT_RED)));
        }
    }
}
