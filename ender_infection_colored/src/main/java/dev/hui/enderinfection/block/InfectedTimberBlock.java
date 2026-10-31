package dev.hui.enderinfection.block;

import dev.hui.enderinfection.InfectedContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import java.util.List;

/** Preserve native bark, end grain, axis and stripped/wood forms during erosion. */
public final class InfectedTimberBlock extends RotatedPillarBlock {
    public static final List<String> SPECIES=List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","crimson","warped","bamboo");
    public static final IntegerProperty WOOD=IntegerProperty.create("wood",0,10);
    public static final BooleanProperty STRIPPED=BooleanProperty.create("stripped"),BARK=BooleanProperty.create("bark"),KNOWN=BooleanProperty.create("source_known");
    public InfectedTimberBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(WOOD,0).setValue(STRIPPED,false).setValue(BARK,false).setValue(KNOWN,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(WOOD,STRIPPED,BARK,KNOWN);}
    public static BlockState from(BlockState source,boolean mature){
        BlockState result=InfectedContent.TERRAIN.get(mature?"infected_log":"eroded_log").get().defaultBlockState();
        if(source.getBlock() instanceof InfectedTimberBlock)result=result.setValue(WOOD,source.getValue(WOOD)).setValue(STRIPPED,source.getValue(STRIPPED)).setValue(BARK,source.getValue(BARK)).setValue(KNOWN,source.getValue(KNOWN));
        else{
            String name=BuiltInRegistries.BLOCK.getKey(source.getBlock()).getPath();int wood=0;
            for(int i=0;i<SPECIES.size();i++)if(name.startsWith(SPECIES.get(i)+"_")||name.startsWith("stripped_"+SPECIES.get(i)+"_")){wood=i;break;}
            result=result.setValue(WOOD,wood).setValue(STRIPPED,name.startsWith("stripped_")).setValue(BARK,name.endsWith("_wood")||name.endsWith("_hyphae")).setValue(KNOWN,true);
        }
        if(source.hasProperty(AXIS))result=result.setValue(AXIS,source.getValue(AXIS));return result;
    }
}
