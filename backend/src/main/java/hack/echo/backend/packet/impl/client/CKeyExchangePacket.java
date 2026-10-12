package hack.echo.backend.packet.impl.client;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class CKeyExchangePacket extends Packet {

    private final String key;

    public CKeyExchangePacket(String key) {
        super(10);

        this.key = key;
    }

    @Override
    public String toString() {
        return String.format("CKeyExchangePacket(key=%s)", key);
    }
}
