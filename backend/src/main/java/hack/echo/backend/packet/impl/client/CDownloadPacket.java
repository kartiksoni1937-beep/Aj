package hack.echo.backend.packet.impl.client;

import hack.echo.backend.packet.Packet;

public class CDownloadPacket extends Packet {

    public CDownloadPacket() {
        super(3);
    }

    @Override
    public String toString() {
        return "CDownloadPacket{}";
    }
}
