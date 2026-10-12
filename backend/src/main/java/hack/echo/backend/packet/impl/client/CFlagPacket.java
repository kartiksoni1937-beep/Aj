package hack.echo.backend.packet.impl.client;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class CFlagPacket extends Packet {

    private final byte flag;

    public CFlagPacket(byte flag) {
        super(4);

        this.flag = flag;
    }

    public String toString() {
        return String.format("CFlagPacket(flag=%d)", flag);
    }
}