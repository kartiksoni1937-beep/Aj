package hack.echo.backend.packet.impl.server;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class SHandshake extends Packet {

    private final String key;

    public SHandshake(String key) {
        super(0);

        this.key = key;
    }

    @Override
    public String toString() {
        return String.format("SHandshake(key=%s)", key);
    }
}