package dev.hui.enderinfection.api.institute;

import java.util.Objects;
import net.minecraft.core.BlockPos;

/** Immutable, shared progress for one research institute in one dimension. */
public record FacilityStatus(BlockPos origin, boolean repaired, boolean servicePowered,
        boolean containmentPowered, boolean analyzed, boolean separated, boolean archiveClaimed) {
    public FacilityStatus { origin = Objects.requireNonNull(origin, "origin").immutable(); }
}
