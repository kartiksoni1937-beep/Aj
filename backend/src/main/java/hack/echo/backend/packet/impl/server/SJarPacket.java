package hack.echo.backend.packet.impl.server;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class SJarPacket extends Packet {

    private final byte[] data;
    private final String key;

    public SJarPacket(byte[] data, String key) {
        super(5);

        this.data = data;
        this.key = key;
    }

    @Override
    public String toString() {
        return String.format("SJarPacket{dataLength=%d, key=%s}", data.length, key);
    }
}
