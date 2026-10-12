package hack.echo.backend.packet.impl.client;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class CSaveConfigPacket extends Packet {

    private final String configName;
    private final String configData;

    public CSaveConfigPacket(String configName, String configData) {
        super(7);

        this.configName = configName;
        this.configData = configData;
    }

    @Override
    public String toString() {
        return String.format("CSaveConfig(configName=%s, configDataLength=%d)", configName, configData.length());
    }
}
