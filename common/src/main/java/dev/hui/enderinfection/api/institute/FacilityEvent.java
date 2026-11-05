package dev.hui.enderinfection.api.institute;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;

/** Dispatched on the server thread after a durable facility change. */
public record FacilityEvent(ServerLevel level, FacilityStatus previous, FacilityStatus current, String reason) {
    public FacilityEvent {
        Objects.requireNonNull(level); Objects.requireNonNull(previous); Objects.requireNonNull(current);
        Objects.requireNonNull(reason);
        if (!previous.origin().equals(current.origin())) throw new IllegalArgumentException("Facility origins differ");
    }
}
