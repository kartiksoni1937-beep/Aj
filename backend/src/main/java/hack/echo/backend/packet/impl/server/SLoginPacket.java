package hack.echo.backend.packet.impl.server;

import lombok.Getter;
import hack.echo.backend.packet.Packet;

@Getter
public class SLoginPacket extends Packet {

    private final String username;
    private final String password;
    private final String hwid;
    private final String uid;
    private final long timestamp;
    private final String sessionToken;
    private final boolean banned;
    private final int serverInteger;
    private final int secretLocalKey;
    private final int runtimeConstant;

    public SLoginPacket(String username, String password, String hwid, String uid, long timestamp, String sessionToken, boolean banned, int serverInteger, int secretLocalKey, int runtimeConstant) {
        super(2);

        this.username = username;
        this.password = password;
        this.hwid = hwid;
        this.uid = uid;
        this.timestamp = timestamp;
        this.sessionToken = sessionToken;
        this.banned = banned;
        this.serverInteger = serverInteger;
        this.secretLocalKey = secretLocalKey;
        this.runtimeConstant = runtimeConstant;
    }

    public String toString() {
        return String.format("SLoginPacket(username=%s, password=%s, hwid=%s, uid=%s, timestamp=%d, sessionToken=%s, banned=%s, serverInteger=%d, secretLocalKey=%d, runtimeConstant=%d)", username, password, hwid, uid, timestamp, sessionToken, banned, serverInteger, secretLocalKey, runtimeConstant);
    }
}