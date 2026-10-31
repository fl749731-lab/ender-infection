package dev.hui.enderinfection.block;

import dev.hui.enderinfection.InfectedContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class ErodedVegetationBlock extends BushBlock {
    public static final IntegerProperty SOURCE=IntegerProperty.create("source",0,63),EROSION=IntegerProperty.create("erosion",0,1);
    public ErodedVegetationBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(SOURCE,0).setValue(EROSION,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(SOURCE,EROSION);}
    @Override protected boolean mayPlaceOn(BlockState state,BlockGetter level,BlockPos pos){return super.mayPlaceOn(state,level,pos)||state.is(InfectedContent.NEST.get())||InfectedContent.terrainKey(state)!=null;}
}
