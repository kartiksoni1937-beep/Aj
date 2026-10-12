package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.network.Server;
import hack.echo.backend.network.Client;
import hack.echo.backend.logger.Ansi;

import static hack.echo.backend.logger.Ansi.Color.WHITE;

public class List extends Command {

    public List() {
        super("list", "List all users");
    }

    @Override
    public void execute(String[] args) {
        Server server = Backend.getInstance().getServer();
        Backend.logger.info(String.format("Users (%s): ", new Ansi().c(WHITE).s(server.getClients().size())));
        for (Client client : server.getClients()) {
            Backend.logger.info(String.format("- %s:%s", new Ansi().c(WHITE).s(client.getUserNameNoIp() == null ? "Unknown" : client.getUsername()), new Ansi().c(WHITE).s(client.getIp())));
        }
    }
}
