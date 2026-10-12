package hack.echo.backend.packet.impl.server;

import hack.echo.backend.packet.Packet;

public class SSaveConfigPacket extends Packet {

    public SSaveConfigPacket() {
        super(7);
    }

    @Override
    public String toString() {
        return "SSaveConfigPacket{}";
    }
}
