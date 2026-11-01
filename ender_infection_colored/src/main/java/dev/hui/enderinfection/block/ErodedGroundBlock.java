package dev.hui.enderinfection.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import dev.hui.enderinfection.InfectedContent;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.List;

/** Three visual coverage levels within the initial erosion phase. No spreading ticker. */
public final class ErodedGroundBlock extends Block {
    public static final IntegerProperty EROSION=IntegerProperty.create("erosion",0,2);
    public static final IntegerProperty SUBSTRATE=IntegerProperty.create("substrate",0,31);
    public static final List<List<String>> BASES=List.of(
        List.of("grass_block","moss_block","podzol","mycelium"),
        List.of("dirt","coarse_dirt","rooted_dirt","mud","dirt_path","farmland","farmland_moist"),
        List.of("sand","snow_block","powder_snow","red_sand","gravel","sandstone","red_sandstone","smooth_sandstone","cut_sandstone","smooth_red_sandstone","cut_red_sandstone"),
        List.of("stone","ice","packed_ice","blue_ice","andesite","diorite","granite","deepslate","end_stone","netherrack","blackstone","basalt","tuff","calcite","clay","terracotta","white_terracotta","orange_terracotta","magenta_terracotta","light_blue_terracotta","yellow_terracotta","lime_terracotta","pink_terracotta","gray_terracotta","light_gray_terracotta","cyan_terracotta","purple_terracotta","blue_terracotta","brown_terracotta","green_terracotta","red_terracotta","black_terracotta")
    );
    private static final List<String> KEYS=List.of("eroded_grass","eroded_soil","eroded_sand","eroded_stone");
    public ErodedGroundBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(EROSION,0).setValue(SUBSTRATE,0));}
    public static BlockState fromNatural(BlockState original){
        String name=BuiltInRegistries.BLOCK.getKey(original.getBlock()).getPath();
        if(name.equals("farmland")&&original.getValue(net.minecraft.world.level.block.FarmBlock.MOISTURE)>0)name="farmland_moist";
        for(int category=0;category<BASES.size();category++){
            int index=BASES.get(category).indexOf(name);
            if(index>=0)return InfectedContent.TERRAIN.get(KEYS.get(category)).get().defaultBlockState().setValue(SUBSTRATE,index);
        }
        return InfectedContent.TERRAIN.get("eroded_stone").get().defaultBlockState();
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(EROSION,SUBSTRATE);}
    public static boolean farmland(BlockState state){return state.is(InfectedContent.TERRAIN.get("eroded_soil").get())&&state.getValue(SUBSTRATE)>=5&&state.getValue(SUBSTRATE)<=6;}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return farmland(state)?Block.box(0,0,0,16,15,16):super.getShape(state,level,pos,context);}
}
