package dev.hui.enderinfection.api.institute;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/** Platform implementation. Neither operation loads chunks; callers must enforce their own permissions. */
public interface RoomPlacementService {
    PlacementResult preview(ServerLevel level, String roomId, BlockPos origin, Rotation rotation);
    PlacementResult place(ServerLevel level, String roomId, BlockPos origin, Rotation rotation);
}
