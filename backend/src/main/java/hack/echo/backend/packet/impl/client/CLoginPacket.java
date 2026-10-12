package hack.echo.backend.packet.impl.client;

import lombok.Getter;
import hack.echo.backend.packet.Packet;

@Getter
public class CLoginPacket extends Packet {

    private final String username;
    private final String password;
    private final String hwid;

    public CLoginPacket(String username, String password, String hwid) {
        super(2);

        this.username = username;
        this.password = password;
        this.hwid = hwid;
    }

    public String toString() {
        return String.format("CLoginPacket(username=%s, password=%s, hwid=%s)", username, password, hwid);
    }
}