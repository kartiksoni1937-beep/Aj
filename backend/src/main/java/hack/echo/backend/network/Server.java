package hack.echo.backend.network;

import hack.echo.backend.util.MongoUtil;
import lombok.Getter;
import hack.echo.backend.Backend;
import hack.echo.backend.logger.Ansi;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static hack.echo.backend.logger.Ansi.Color.*;

@Getter
public class Server {

    private final List<Client> clients = new ArrayList<>();
    private final ConcurrentLinkedDeque<ConnectionCache> cache = new ConcurrentLinkedDeque<>();

    public void init(int port) {
        Thread server = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                while (true) {
                    try {
                        Client client = new Client(serverSocket.accept());
                        if (!client.handshake()) {
                            MongoUtil.increaseVL(client);
                            continue;
                        }

                        /*for (Client client2 : Backend.getInstance().getServer().getClients()) {
                            if (client.getUsername().equals(client2.getUsername()) && client2 != client && client2.isLoggedIn()) {
                                client2.kick(true);
                            }
                        }*/

                        clients.add(client);
                        new Thread(client).start();
                        Thread.sleep(100);
                    } catch (IOException e) {
                        Backend.logger.error("Failed to handle client", e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            } catch (IOException e) {
                Backend.logger.fatal("Failed to start server", e);
            }
        });
        server.setName("Server-Thread");
        server.start();
        Backend.logger.info(String.format("Starting on port %s", new Ansi().c(BRIGHT_GREEN).s(port)));
    }

    public void removeClient(Client client) {
        clients.remove(client);
    }

    public boolean kickClient(String username, boolean disconnect) {
        Client client = getClient(username);
        if (client != null) {
            client.kick(disconnect);
            clients.remove(client);
        }
        return client != null;
    }

    public Client getClient(String username) {
        for (Client client : clients) {
            if (client.getUsername().equalsIgnoreCase(username)) {
                return client;
            }
            if (client.getIp().equals(username)) {
                return client;
            }
        }
        return null;
    }
}
