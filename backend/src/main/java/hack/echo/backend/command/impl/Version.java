package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;

import static hack.echo.backend.logger.Ansi.Color.BRIGHT_GREEN;

public class Version extends Command {

    public Version() {
        super("version", "Prints the version of the backend");
    }

    @Override
    public void execute(String[] args) {
        Backend.logger.info(String.format("Echo backend %s", new Ansi().c(BRIGHT_GREEN).s("v1.0")));
    }
}
