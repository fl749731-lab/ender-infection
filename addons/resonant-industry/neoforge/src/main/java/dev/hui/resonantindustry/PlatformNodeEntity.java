package dev.hui.resonantindustry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
public abstract class PlatformNodeEntity extends BlockEntity {
    protected PlatformNodeEntity(BlockEntityType<?> type,BlockPos p,BlockState s){super(type,p,s);}
}
