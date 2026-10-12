package hack.echo.backend.packet.impl.client;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class CDeleteConfigPacket extends Packet {

    private final String configName;

    public CDeleteConfigPacket(String configName) {
        super(8);

        this.configName = configName;
    }

    @Override
    public String toString() {
        return String.format("CDeleteConfigPacket(configName=%s)", configName);
    }
}
