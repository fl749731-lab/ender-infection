package dev.hui.enderinfection.api.institute;

import java.util.Objects;
import java.util.Set;

/** A device handler is bound explicitly to one or more registered block IDs. */
public record DeviceDefinition(String id, Set<String> blockIds, DeviceHandler handler) {
    public DeviceDefinition {
        InstituteRegistry.checkId(id);
        blockIds = Set.copyOf(blockIds);
        if (blockIds.isEmpty()) throw new IllegalArgumentException("A device needs at least one block binding");
        blockIds.forEach(InstituteRegistry::checkId);
        Objects.requireNonNull(handler, "handler");
    }
}
