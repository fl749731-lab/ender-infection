package dev.hui.instituteaddon;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;

/** A real addon-owned block; all interaction is supplied through the institute API. */
public final class ResearchConsoleBlock extends Block {
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public ResearchConsoleBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                .lightLevel(state -> state.getValue(POWERED) ? 12 : 0));
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
}
