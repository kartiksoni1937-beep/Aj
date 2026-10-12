package hack.echo.backend.command.impl;

import hack.echo.backend.Backend;
import hack.echo.backend.command.Command;
import hack.echo.backend.logger.Ansi;
import hack.echo.backend.network.Client;
import hack.echo.backend.packet.impl.server.SBroadcastPacket;

import static hack.echo.backend.logger.Ansi.Color.RED;

public class Broadcast extends Command {

    public Broadcast() {
        super("broadcast", "Broadcast a message to all clients");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            Backend.logger.error(String.format("Usage: broadcast %s", new Ansi().c(RED).s("<message>")));
            return;
        }

        String message = String.join(" ", args);
        for (Client client : Backend.getInstance().getServer().getClients()) {
            client.sendPacket(new SBroadcastPacket(message));
        }

        Backend.logger.info("Message broadcasted to all clients");
    }
}
