package hack.echo.backend.packet.impl.client;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class CReconnectPacket extends Packet {

    private final String sessionToken;
    private final String hwid;

    public CReconnectPacket(String sessionToken, String hwid) {
        super(5);

        this.sessionToken = sessionToken;
        this.hwid = hwid;
    }

    public String toString() {
        return String.format("CReconnectPacket(sessionToken=%s, hwid=%s)", sessionToken, hwid);
    }
}
