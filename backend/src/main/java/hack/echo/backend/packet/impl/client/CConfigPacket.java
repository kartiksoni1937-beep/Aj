package hack.echo.backend.packet.impl.client;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class CConfigPacket extends Packet {

    private final String configName;

    public CConfigPacket(String configName) {
        super(6);

        this.configName = configName;
    }

    @Override
    public String toString() {
        return String.format("CConfigRequest(configName=%s)", configName);
    }
}
