package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;

public class Help extends Command {

    public Help() {
        super("help", "Display help information");
    }

    @Override
    public void execute(String[] args) {
        StringBuilder stringBuilder = new StringBuilder("Available commands");
        for (Command command : Backend.getInstance().getCommandManager().getCommands()) {
            if (command.getName().equals("help")) {
                continue;
            }
            stringBuilder.append("\n").append(command.getName()).append(" - ").append(new Ansi().c(Ansi.Color.WHITE).s(command.getDescription()));
        }
        Backend.logger.info(stringBuilder.toString());
    }
}
