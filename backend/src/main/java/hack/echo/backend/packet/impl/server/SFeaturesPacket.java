package hack.echo.backend.packet.impl.server;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class SFeaturesPacket extends Packet {

    private final byte[] data;

    public SFeaturesPacket(byte[] data) {
        super(9);

        this.data = data;
    }

    @Override
    public String toString() {
        return String.format("SFeaturesPacket(dataLength=%d)", data.length);
    }
}
