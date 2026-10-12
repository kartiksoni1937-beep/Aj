package hack.echo.backend.packet.impl.server;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class SConfigPacket extends Packet {

    private final byte[] data;

    public SConfigPacket(byte[] data) {
        super(6);

        this.data = data;
    }

    @Override
    public String toString() {
        return String.format("SConfig(dataLength=%d)", data.length);
    }
}
