package dev.hui.enderinfection.design;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Single-cell shelves, freezers, crates and carts retain their baked block models. */
public final class LabStorageEntity extends LabStorageInventory {
    public LabStorageEntity(BlockPos pos, BlockState state) { super(DesignContent.STORAGE_ENTITY.get(), pos, state); }
    @Override public boolean isStorage() { return true; }
}
