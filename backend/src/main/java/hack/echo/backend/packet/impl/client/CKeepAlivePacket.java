package hack.echo.backend.packet.impl.client;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class CKeepAlivePacket extends Packet {

    private final long value;

    public CKeepAlivePacket(long value) {
        super(1);

        this.value = value;
    }

    public String toString() {
        return String.format("CKeepAlivePacket(value=%d)", value);
    }
}
