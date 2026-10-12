package hack.echo.backend.packet.impl.client;

import hack.echo.backend.packet.Packet;
import lombok.Getter;

@Getter
public class CInvalidPacket extends Packet {

    private final int id;
    
    public CInvalidPacket(int id) {
        super(-1);

        this.id = id;
    }

    @Override
    public String toString() {
        return String.format("CInvalidPacket(id=%d)", id);
    }
}
