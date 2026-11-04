package dev.hui.enderinfection.api.institute;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Preview and placement share all validation. Bounds are null for an unknown/missing template. */
public record PlacementResult(boolean success, String message, BoundingBox bounds, List<WorldConnector> connectors) {
    public PlacementResult { connectors = List.copyOf(connectors); }
    public static PlacementResult failure(String message) { return new PlacementResult(false, message, null, List.of()); }
}
