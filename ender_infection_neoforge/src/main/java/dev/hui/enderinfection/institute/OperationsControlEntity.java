package dev.hui.enderinfection.institute;

import dev.hui.enderinfection.design.DesignContent;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A template binding is relative to its tile-independent, normalized whole-site origin. */
public final class OperationsControlEntity extends BlockEntity {
    private boolean bound;
    private BlockPos offset = BlockPos.ZERO;
    public OperationsControlEntity(BlockPos pos, BlockState state) { super(DesignContent.OPERATIONS_ENTITY.get(), pos, state); }
    public String role() { return getBlockState().getBlock() instanceof OperationsControlBlock block ? block.role : ""; }
    public Optional<BlockPos> offset() { return bound && InstituteOperations.validOffset(offset) ? Optional.of(offset) : Optional.empty(); }
    public Optional<BlockPos> boundOrigin() { return offset().map(worldPosition::subtract); }
    public void bind(BlockPos value) {
        if (!InstituteOperations.validOffset(value)) throw new IllegalArgumentException("Invalid institute offset");
        bound = true; offset = value.immutable(); setChanged(); register();
    }
    private void register() {
        if (getLevel() instanceof ServerLevel server && offset().isPresent()) server.getServer().execute(() -> {
            if (!isRemoved() && server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4) != null
                    && server.getBlockEntity(worldPosition) == this) InstituteOperations.registerBound(server, worldPosition, offset);
        });
    }
    @Override public void onLoad() { super.onLoad(); register(); }
    @Override protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); bound = tag.getBoolean("InstituteBound");
        offset = new BlockPos(tag.getInt("InstituteOffsetX"), tag.getInt("InstituteOffsetY"), tag.getInt("InstituteOffsetZ"));
        if (!InstituteOperations.validOffset(offset)) bound = false;
    }
    @Override protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (bound) { tag.putBoolean("InstituteBound", true); tag.putInt("InstituteOffsetX", offset.getX());
            tag.putInt("InstituteOffsetY", offset.getY()); tag.putInt("InstituteOffsetZ", offset.getZ()); }
    }
    public void updatePowered(boolean powered) {
        if (getLevel() instanceof ServerLevel server && !isRemoved()) {
            var state = getBlockState();
            if (state.getValue(OperationsControlBlock.POWERED) != powered)
                server.setBlock(worldPosition, state.setValue(OperationsControlBlock.POWERED, powered), 18);
        }
    }
}
