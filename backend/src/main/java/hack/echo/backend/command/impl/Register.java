package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;
import hack.echo.backend.util.MongoUtil;
import org.bson.Document;

import java.util.Date;

import static hack.echo.backend.logger.Ansi.Color.*;

public class Register extends Command {

    public Register() {
        super("register", "Register a new user");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 2) {
            StringBuilder sb = new StringBuilder();
            sb.append("Usage: register ");
            sb.append(new Ansi().c(RED).s("<user> <password> [arg=value]"));
            sb.append("\n");
            sb.append(new Ansi().c(BRIGHT_RED).s("Arguments:\n"));
            sb.append(new Ansi().c(RED).s("  [plan]      ").c(BRIGHT_RED).s("Options: lifetime, premium (sub)\n"));
            sb.append(new Ansi().c(RED).s("  [expires]   ").c(BRIGHT_RED).s("Format: dd\n"));
            sb.append(new Ansi().c(RED).s("  [email]     ").c(BRIGHT_RED).s("Example: test@echoclient.net\n"));
            sb.append(new Ansi().c(RED).s("  [admin]     ").c(BRIGHT_RED).s("Options: true, false\n"));
            Backend.logger.error(sb.toString());
            return;
        }

        String username = args[0];
        String password = Backend.getInstance().getArgon2().encode(args[1]);
        String plan = "";
        String email = "";
        boolean isAdmin = false;
        int expiresInDays = 0;

        for (int i = 2; i < args.length; i++) {
            String[] splitArg = args[i].split("=");
            if (splitArg.length != 2) {
                continue;
            }
            String key = splitArg[0].toLowerCase();
            String value = splitArg[1];
            switch (key) {
                case "plan" -> plan = value;
                case "expires" -> {
                    try {
                        expiresInDays = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        Backend.logger.error(String.format("Invalid expiration value: %s", new Ansi().c(RED).s(value)));
                        return;
                    }
                }
                case "email" -> email = value;
                case "admin" -> isAdmin = value.equalsIgnoreCase("true");
            }
        }

        if (MongoUtil.getUser("username", username) != null) {
            Backend.logger.error(String.format("User %s already exists", new Ansi().c(RED).s(username).c(BRIGHT_RED)));
            return;
        }

        if (!plan.isEmpty() && !plan.equalsIgnoreCase("lifetime") && !plan.equalsIgnoreCase("premium")) {
            Backend.logger.error(String.format("Invalid plan: %s", new Ansi().c(RED).s(plan)));
            return;
        }

        if (expiresInDays <= 0 && plan.equalsIgnoreCase("premium")) {
            Backend.logger.error("Expires must be greater than 0 for non-lifetime plans");
            return;
        }

        Document document = new Document();
        document.append("username", username);
        if (!email.isEmpty()) {
            document.append("email", email);
        }
        if (plan.equalsIgnoreCase("lifetime") || plan.equalsIgnoreCase("premium")) {
            document.append("userNumber", MongoUtil.getLastUserNumber() + 1);
        }
        document.append("password", password);
        document.append("plan", plan);
        if (!plan.isEmpty()) {
            document.append("plan", plan);
        } else {
            document.append("plan", "N/A");
        }
        if (expiresInDays > 0) {
            long currentTime = System.currentTimeMillis();
            long expiresAt = currentTime + (expiresInDays * 24L * 60L * 60L * 1000L);
            document.append("expiration_date", new Date(expiresAt));
        } else {
            document.append("expiration_date", null);
        }
        document.append("admin", isAdmin);
        document.append("createdAt", new Date());
        MongoUtil.createUser(document);
        Backend.logger.info(String.format("User %s has been registered successfully", new Ansi().c(WHITE).s(username)));
    }
}
