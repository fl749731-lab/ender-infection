package dev.hui.enderinfection.design;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Native two-part sleeping/respawn behavior with a baked medical frame. */
public final class LabMedicalBedBlock extends BedBlock {
    public LabMedicalBedBlock(Properties properties) { super(DyeColor.CYAN, properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    // Bed's entity only supplies the vanilla fabric renderer. Sleep/occupancy use block state.
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return null; }
}
