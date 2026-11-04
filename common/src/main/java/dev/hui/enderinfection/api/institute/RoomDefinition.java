package dev.hui.enderinfection.api.institute;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Register during mod construction. The named NBT template must ship as a data resource. */
public record RoomDefinition(String id, String templateId, RoomSize size,
                             List<RoomConnector> connectors, Set<String> tags) {
    public RoomDefinition {
        InstituteRegistry.checkId(id);
        InstituteRegistry.checkId(templateId);
        Objects.requireNonNull(size, "size");
        connectors = List.copyOf(connectors);
        tags = Set.copyOf(tags);
        var names = new HashSet<String>();
        for (var c : connectors) {
            if (!names.add(c.name())) throw new IllegalArgumentException("Duplicate connector: " + c.name());
            if (c.x() < 0 || c.y() < 0 || c.z() < 0 || c.x() >= size.x() || c.y() >= size.y() || c.z() >= size.z())
                throw new IllegalArgumentException("Connector outside template: " + c.name());
        }
        for (var tag : tags) InstituteRegistry.checkId(tag);
    }
}
