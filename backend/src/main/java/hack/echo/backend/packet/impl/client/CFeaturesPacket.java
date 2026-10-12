package hack.echo.backend.packet.impl.client;

import hack.echo.backend.packet.Packet;

public class CFeaturesPacket extends Packet {

    public CFeaturesPacket() {
        super(9);
    }

    @Override
    public String toString() {
        return "CFeaturesPacket{}";
    }
}
