package hack.echo.backend.packet.impl.server;

import hack.echo.backend.packet.Packet;

public class SDisconnectPacket extends Packet {

    public SDisconnectPacket() {
        super(4);
    }

    @Override
    public String toString() {
        return "SDisconnectPacket{}";
    }
}
