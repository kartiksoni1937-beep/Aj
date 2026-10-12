package hack.echo.backend.packet.impl.client;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class CHandshake extends Packet {

    private final String key;

    public CHandshake(String key) {
        super(0);

        this.key = key;
    }

    @Override
    public String toString() {
        return String.format("CHandshake(key=%s)", key);
    }
}
