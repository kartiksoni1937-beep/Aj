package hack.echo.backend.packet.impl.server;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class SKeyExchangePacket extends Packet {
    
    private final String key;

    public SKeyExchangePacket(String key) {
        super(10);

        this.key = key;
    }
    
    @Override
    public String toString() {
        return String.format("SKeyExchangePacket(key=%s)", key);
    }
}