package dev.hui.enderinfection.block;

import dev.hui.enderinfection.InfectedContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import java.util.List;

public final class InfectedCanopyBlock extends Block {
    public static final List<String> SPECIES=List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","azalea","flowering_azalea");
    public static final IntegerProperty WOOD=IntegerProperty.create("wood",0,9);
    public static final BooleanProperty KNOWN=BooleanProperty.create("source_known");
    public InfectedCanopyBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(WOOD,0).setValue(KNOWN,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(WOOD,KNOWN);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(KNOWN,true);}
    public static boolean known(BlockState state){return state.getValue(KNOWN)||state.getValue(WOOD)!=0;}
    public static BlockState from(BlockState source,boolean mature){
        BlockState result=InfectedContent.TERRAIN.get(mature?"infected_leaves":"eroded_leaves").get().defaultBlockState();
        if(source.getBlock() instanceof InfectedCanopyBlock)return result.setValue(WOOD,source.getValue(WOOD)).setValue(KNOWN,known(source));
        String name=BuiltInRegistries.BLOCK.getKey(source.getBlock()).getPath();int index=SPECIES.indexOf(name.replace("_leaves",""));return result.setValue(WOOD,Math.max(0,index)).setValue(KNOWN,index>=0);
    }
    private record Step(BlockPos pos,int distance){}
    /** Infer only an unknown legacy leaf, through its connected crown, from the nearest reliable trunk. */
    public static BlockState recoverLegacy(ServerLevel level,BlockPos pos){
        BlockState original=level.getBlockState(pos);
        if(!(original.getBlock() instanceof InfectedCanopyBlock)||known(original))return null;
        var queue=new java.util.ArrayDeque<Step>();var visited=new java.util.HashSet<BlockPos>();
        queue.add(new Step(pos,0));visited.add(pos);int best=7,species=-1,examined=0;boolean ambiguous=false;
        while(!queue.isEmpty()){
            Step step=queue.removeFirst();if(step.distance()+1>best)break;
            if(++examined>256)return null;
            for(Direction direction:Direction.values()){
                BlockPos next=step.pos().relative(direction);if(!level.hasChunkAt(next))continue;
                BlockState state=level.getBlockState(next);int tree=-1;
                if(state.getBlock() instanceof InfectedTimberBlock){if(state.getValue(InfectedTimberBlock.KNOWN))tree=state.getValue(InfectedTimberBlock.WOOD);}
                else if(state.is(BlockTags.LOGS))tree=InfectedTimberBlock.from(state,false).getValue(InfectedTimberBlock.WOOD);
                if(tree>=0){
                    int leaf=SPECIES.indexOf(InfectedTimberBlock.SPECIES.get(tree));if(leaf<0)continue;
                    int distance=step.distance()+1;
                    if(distance<best){best=distance;species=leaf;ambiguous=false;}
                    else if(distance==best&&species!=leaf)ambiguous=true;
                }else if(step.distance()<5&&(state.getBlock() instanceof InfectedCanopyBlock||state.is(BlockTags.LEAVES)&&(!state.hasProperty(LeavesBlock.PERSISTENT)||!state.getValue(LeavesBlock.PERSISTENT)))&&visited.add(next))queue.addLast(new Step(next,step.distance()+1));
            }
        }
        return species<0||ambiguous?null:original.setValue(WOOD,species).setValue(KNOWN,true);
    }
}
