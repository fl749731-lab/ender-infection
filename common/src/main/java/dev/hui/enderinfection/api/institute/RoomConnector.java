package dev.hui.enderinfection.api.institute;

import java.util.Objects;
import net.minecraft.core.Direction;

/** Local template coordinate and the direction a doorway/socket faces. */
public record RoomConnector(String name, int x, int y, int z, Direction facing, String socket) {
    public RoomConnector {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(socket, "socket");
        if (!name.matches("[a-z0-9_./-]+") || !socket.matches("[a-z0-9_./:-]+"))
            throw new IllegalArgumentException("Invalid connector name or socket");
    }
}
