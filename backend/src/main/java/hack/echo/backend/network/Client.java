package hack.echo.backend.network;

import hack.echo.backend.packet.impl.server.SDisconnectPacket;
import hack.echo.backend.util.*;
import lombok.Getter;
import lombok.Setter;
import hack.echo.backend.Backend;
import hack.echo.backend.packet.Packet;
import hack.echo.backend.logger.Ansi;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.NoSuchElementException;

import static hack.echo.backend.logger.Ansi.Color.*;

@Getter @Setter
public class Client extends Thread {

    private DataInputStream input;
    private DataOutputStream output;
    private final Socket socket;
    private String ip;
    private String encryptionCKey;
    private String encryptionSKey;
    private SecretKey cSecretKey;
    private SecretKey sSecretKey;
    private String username;
    private String hwid = "Unknown";
    private String cpu;
    private long timestamp;
    private boolean isHWIDLogin = false;
    private boolean loggedIn;
    private boolean sentFeatures;
    private boolean keepAliveInit = false;
    private boolean reconnecting = false;
    private long keepAlive = 0;
    private long prevKeepAlive = 0;
    private long key = 0;

    public Client(Socket socket) throws IOException {
        this.socket = socket;

        try {
            socket.setSoTimeout(90000);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            encryptionCKey = Backend.getInstance().getConfig().encryptionKey;
            encryptionSKey = Backend.getInstance().getConfig().encryptionKey;
            updateKey(encryptionCKey, true);
            updateKey(encryptionSKey, false);
        } catch (Exception e) {
            Backend.logger.error("Failed to create client streams", e);
        }

        this.ip = socket.getInetAddress().getHostAddress();
        setName(String.format("Client-%s", ip));
    }

    public boolean handshake() {
        try {
            WebSocketUtil.Response response = WebSocketUtil.handshake(input, output);
            if (!response.ip.equals("unknown")) {
                ip = response.ip;
                setName(String.format("Client-%s", ip));
            }
            return response.success;
        } catch (NoSuchElementException ignored) {
            return false;
        }
    }

    public void setTimeout(int timeout) {
        try {
            socket.setSoTimeout(timeout);
        } catch (SocketException e) {
            Backend.logger.error(String.format("Failed to set timeout for user %s", new Ansi().c(RED).s(getUsername())), e);
        }
    }

    @Override
    public void run() {
        Backend.logger.info(String.format("User %s connected", new Ansi().c(WHITE).s(getUsername())));
        try {
            while (socket.isConnected()) {
                Packet packet = PacketUtil.readPacket(input, cSecretKey);
                if (packet == null) {
                    disconnect();
                    return;
                }

                Backend.logger.debug(String.format("Received packet from %s %s", ip, packet));
                PacketHandler.handlePacket(packet, this);
            }
        } catch (SocketTimeoutException e) {
            Backend.logger.info(String.format("User %s timed out", new Ansi().c(WHITE).s(getUsername())));
            try {
                sendPacket(new SDisconnectPacket());
                Backend.getInstance().getServer().removeClient(this);
                socket.close();
            } catch (IOException ignored) {}
            return;
        } catch (SocketException ignored) {
        } catch (Exception e) {
            Backend.logger.error(String.format("Failed to handle client %s", new Ansi().c(RED).s(ip)), e);
        }
        disconnect();
    }

    public void kick(boolean disconnect) {
        try {
            if (disconnect) {
                sendPacket(new SDisconnectPacket());
            }
            socket.close();
        } catch (Exception e) {
            Backend.logger.error(String.format("Failed to kick user %s", new Ansi().c(RED).s(getUsername())), e);
        }
    }

    private void disconnect() {
        try {
            socket.close();
            Backend.logger.info(String.format("User %s disconnected", new Ansi().c(WHITE).s(getUsername())));
            Backend.getInstance().getServer().removeClient(this);
        } catch (IOException e) {
            Backend.logger.error(String.format("Failed to disconnect user %s", new Ansi().c(RED).s(getUsername())), e);
        }
    }

    public void sendPacket(Packet packet) {
        try {
            PacketUtil.sendPacket(output, packet, sSecretKey);
        } catch (IOException e) {
            Backend.logger.error(String.format("Failed to send packet to user %s", new Ansi().c(RED).s(getUsername())), e);
        }
    }

    public void updateKey(String key, boolean isClient) {
        try {
            if (isClient) {
                setCSecretKey(EncryptionUtil.getKeyFromPassword(key, "EchoUw!*R9C&.c:/\"?}pjJCL!jM/QZ]:"));
            } else {
                setSSecretKey(EncryptionUtil.getKeyFromPassword(key, "EchoUw!*R9C&.c:/\"?}pjJCL!jM/QZ]:"));
            }
        } catch (Exception e) {
            Backend.logger.error("Failed to update secret key", e);
        }
    }

    public String getUsername() {
        return username == null ? ip : username;
    }

    public String getUserNameNoIp() {
        return username;
    }
}