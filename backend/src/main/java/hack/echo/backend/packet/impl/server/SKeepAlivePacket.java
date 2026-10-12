package hack.echo.backend.packet.impl.server;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class SKeepAlivePacket extends Packet {

    private final long value;
    private final long key;

    public SKeepAlivePacket(long value, long key) {
        super(1);

        this.value = value;
        this.key = key;
    }

    public String toString() {
        return String.format("SKeepAlivePacket(value=%d)", value);
    }
}