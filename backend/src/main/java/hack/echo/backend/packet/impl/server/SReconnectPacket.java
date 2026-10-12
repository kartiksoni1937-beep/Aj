package hack.echo.backend.packet.impl.server;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class SReconnectPacket extends Packet {

    private final String sessionToken;
    private final String hwid;
    private final String uid;
    private final long timestamp;
    private final boolean banned;

    public SReconnectPacket(String sessionToken, String hwid, String uid, long timestamp, boolean banned) {
        super(3);

        this.sessionToken = sessionToken;
        this.hwid = hwid;
        this.uid = uid;
        this.timestamp = timestamp;
        this.banned = banned;
    }

    @Override
    public String toString() {
        return String.format("SReconnectPacket(sessionToken=%s, hwid=%s, uid=%s, timestamp=%d, banned=%s)", sessionToken, hwid, uid, timestamp, banned);
    }
}
