package dev.hui.enderinfection.block;

import dev.hui.enderinfection.InfectedContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.List;

public final class Vegetation {
    private Vegetation(){}
    public static final List<String> SINGLE=List.of("grass","fern","dead_bush","dandelion","poppy","blue_orchid","allium","azure_bluet","red_tulip","orange_tulip","white_tulip","pink_tulip","oxeye_daisy","cornflower","lily_of_the_valley","wither_rose","brown_mushroom","red_mushroom","crimson_fungus","warped_fungus","crimson_roots","warped_roots","nether_sprouts","torchflower","oak_sapling","spruce_sapling","birch_sapling","jungle_sapling","acacia_sapling","dark_oak_sapling","cherry_sapling");
    public static final List<String> TALL=List.of("tall_grass","large_fern","sunflower","lilac","rose_bush","peony","pitcher_plant");
    public static String source(int index){if(index<SINGLE.size())return SINGLE.get(Math.max(0,index));return index<39?"wheat":index<47?"carrots":index<55?"potatoes":index<59?"beetroots":"sweet_berry_bush";}
    public static int singleIndex(BlockState state){
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());if(!id.getNamespace().equals("minecraft"))return -1;String name=id.getPath();int index=SINGLE.indexOf(name);if(index>=0)return index;
        int age=state.hasProperty(BlockStateProperties.AGE_7)?state.getValue(BlockStateProperties.AGE_7):state.hasProperty(BlockStateProperties.AGE_3)?state.getValue(BlockStateProperties.AGE_3):0;
        return switch(name){case "wheat"->31+age;case "carrots"->39+age;case "potatoes"->47+age;case "beetroots"->55+age;case "sweet_berry_bush"->59+age;default->-1;};
    }
    public static boolean tallBottom(BlockState state){return state.hasProperty(DoublePlantBlock.HALF)&&state.getValue(DoublePlantBlock.HALF)==DoubleBlockHalf.LOWER&&(state.getBlock() instanceof ErodedTallVegetationBlock||TALL.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()));}
    public static boolean supported(BlockState state){return state.getBlock() instanceof ErodedVegetationBlock||tallBottom(state)||state.getBlock() instanceof InfectedPlantBlock||singleIndex(state)>=0;}
    public static BlockState initial(BlockState state){
        if(tallBottom(state)){int index=TALL.indexOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());return InfectedContent.PLANTS.get("eroded_tall_vegetation").get().defaultBlockState().setValue(ErodedTallVegetationBlock.SOURCE,Math.max(0,index));}
        int index=singleIndex(state);return index<0?null:InfectedContent.PLANTS.get("eroded_vegetation").get().defaultBlockState().setValue(ErodedVegetationBlock.SOURCE,index);
    }
    public static boolean grassTint(BlockState state){return state.getBlock() instanceof ErodedTallVegetationBlock?state.getValue(ErodedTallVegetationBlock.SOURCE)<2:state.getValue(ErodedVegetationBlock.SOURCE)<2;}
    public static String mature(BlockState state){
        String name=state.getBlock() instanceof ErodedTallVegetationBlock?TALL.get(state.getValue(ErodedTallVegetationBlock.SOURCE)):source(state.getValue(ErodedVegetationBlock.SOURCE));
        if(name.contains("mushroom")||name.contains("fungus"))return "infected_fungus";
        if(name.contains("grass")||name.contains("fern")||name.contains("roots")||name.equals("dead_bush")||name.equals("nether_sprouts"))return "infected_grass";
        return "infected_flower";
    }
}
