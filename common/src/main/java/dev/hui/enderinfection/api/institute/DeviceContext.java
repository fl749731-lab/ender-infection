package dev.hui.enderinfection.api.institute;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

/** Server-side interaction after the loader's protection/cancellation checks. */
public record DeviceContext(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
                            InteractionHand hand, Direction face) {
    public DeviceContext {
        Objects.requireNonNull(level); Objects.requireNonNull(player); Objects.requireNonNull(pos);
        Objects.requireNonNull(state); Objects.requireNonNull(hand); Objects.requireNonNull(face);
        pos = pos.immutable();
    }
}
