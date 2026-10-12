package hack.echo.backend.packet.impl.server;

import hack.echo.backend.packet.Packet;

public class SDeleteConfigPacket extends Packet {

    public SDeleteConfigPacket() {
        super(8);
    }

    @Override
    public String toString() {
        return "SDeleteConfigPacket{}";
    }
}
