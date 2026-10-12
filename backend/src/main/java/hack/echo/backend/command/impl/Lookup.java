package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;
import hack.echo.backend.network.User;
import hack.echo.backend.util.MongoUtil;

import static hack.echo.backend.logger.Ansi.Color.*;

public class Lookup extends Command {

    public Lookup() {
        super("lookup", "Lookup a user");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            Backend.logger.error(String.format("Usage: lookup %s", new Ansi().c(Ansi.Color.RED).s("<user>")));
            return;
        }

        User user = MongoUtil.getUser("username", args[0]);
        if (user == null) {
            Backend.logger.error(String.format("User %s is not found", new Ansi().c(RED).s(args[0]).c(BRIGHT_RED)));
            return;
        }

        StringBuilder builder = new StringBuilder().append("\n");
        builder.append("Username:        ").append(new Ansi().c(WHITE).s(user.getUsername())).append("\n");
        if (user.getEmail() != null) {
            builder.append("Email:           ").append(new Ansi().c(WHITE).s(user.getEmail())).append("\n");
        }
        builder.append("UID:             ").append(new Ansi().c(WHITE).s(user.getUserNumber())).append("\n");
        if (user.getHwid() != null) {
            builder.append("HWID:            ").append(new Ansi().c(WHITE).s(user.getHwid())).append("\n");
        }
        builder.append("Plan:            ").append(new Ansi().c(WHITE).s(user.getPlan())).append("\n");
        if (user.getExparationDate() != null) {
            builder.append("Expires:         ").append(new Ansi().c(WHITE).s(user.getExparationDate())).append("\n");
        }
        builder.append("Admin:           ").append(new Ansi().c(WHITE).s(String.valueOf(user.isAdmin()))).append("\n");
        builder.append("Beta:            ").append(new Ansi().c(WHITE).s(String.valueOf(user.isBeta()))).append("\n");
        builder.append("Banned:          ").append(new Ansi().c(WHITE).s(String.valueOf(user.isBanned()))).append("\n");
        if (user.isBanned() && user.getBanReason() != null) {
            builder.append("Ban Reason:      ").append(new Ansi().c(WHITE).s(user.getBanReason())).append("\n");
        }
        if (user.getDiscordId() != null) {
            builder.append("Discord ID:      ").append(new Ansi().c(WHITE).s(user.getDiscordId())).append("\n");
        }
        if (user.getDiscordUsername() != null) {
            builder.append("Discord Tag:     ").append(new Ansi().c(WHITE).s(user.getDiscordUsername())).append("\n");
        }
        builder.append("Violation:       ").append(new Ansi().c(WHITE).s(String.valueOf(user.getVl()))).append("\n");
        if (user.getLastIp() != null) {
            builder.append("Last IP:         ").append(new Ansi().c(WHITE).s(user.getLastIp())).append("\n");
        }
        if (user.getLastHwidReset() != null) {
            builder.append("Last HWID reset: ").append(new Ansi().c(WHITE).s(user.getLastHwidReset())).append("\n");
        }
        if (user.getLastAttempt() != null) {
            builder.append("Last Attempt:    ").append(new Ansi().c(WHITE).s(user.getLastAttempt())).append("\n");
        }
        if (user.getLastAttemptIp() != null) {
            builder.append("Last Attempt IP: ").append(new Ansi().c(WHITE).s(user.getLastAttemptIp())).append("\n");
        }
        builder.append("Created At:      ").append(new Ansi().c(WHITE).s(user.getCreatedAt()));
        Backend.logger.info(builder.toString());
    }
}
