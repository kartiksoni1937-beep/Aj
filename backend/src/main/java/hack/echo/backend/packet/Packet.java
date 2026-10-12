package hack.echo.backend.packet;

import lombok.Getter;

@Getter
public abstract class Packet {

    private final int id;

    public Packet(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return String.format("Packet{id=%d}", id);
    }
}
