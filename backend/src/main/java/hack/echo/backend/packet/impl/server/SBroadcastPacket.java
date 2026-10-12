package hack.echo.backend.packet.impl.server;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class SBroadcastPacket extends Packet {

    private final String message;

    public SBroadcastPacket(String message) {
        super(11);

        this.message = message;
    }

    @Override
    public String toString() {
        return String.format("SBroadcastPacket(message=%s)", message);
    }
}
