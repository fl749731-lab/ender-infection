package dev.hui.enderinfection.api.institute;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Core loader bridge. Queries never load a chunk or create a facility. */
public interface InstituteOperationsService {
    Optional<FacilityStatus> status(ServerLevel level, BlockPos pos);
    boolean powered(ServerLevel level, BlockPos pos);
    void completeProcess(ServerLevel level, BlockPos pos, String recipeId);
}
