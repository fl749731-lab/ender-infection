package dev.hui.enderinfection.design;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class LabPartBlock extends Block {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X=IntegerProperty.create("part_x",0,2),Y=IntegerProperty.create("part_y",0,2);
    public LabPartBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(X,1).setValue(Y,1));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,X,Y);}
    public static BlockPos anchor(BlockPos p,BlockState s){return LabFixtureBlock.cell(p,s.getValue(FACING),1-s.getValue(X),-s.getValue(Y));}
    // Invisible footprint cells must not query their root from the lighting worker.
    // A root across the chunk border can still be waiting for this very light task.
    @Override public boolean propagatesSkylightDown(BlockState s,BlockGetter level,BlockPos pos){return true;}
    @Override public int getLightBlock(BlockState s,BlockGetter level,BlockPos pos){return 0;}
    @Override public VoxelShape getOcclusionShape(BlockState s,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
    @Override public VoxelShape getShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext c){var root=anchor(pos,s);if(level instanceof Level world&&!world.hasChunkAt(root))return Shapes.empty();var a=level.getBlockState(root);return a.getBlock() instanceof LabFixtureBlock b?LabFixtureBlock.rotate(b.localShape(a,s.getValue(X)-1,s.getValue(Y),LabFixtureBlock.doorPairSide(level,root,a)),s.getValue(FACING)):Shapes.empty();}
    @Override public InteractionResult use(BlockState s,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){BlockPos a=anchor(pos,s);if(!level.hasChunkAt(a))return InteractionResult.PASS;var root=level.getBlockState(a);return root.getBlock() instanceof LabFixtureBlock fixture?fixture.use(root,level,a,player,hand,new BlockHitResult(hit.getLocation(),hit.getDirection(),a,hit.isInside())):InteractionResult.PASS;}
    @Override public void onRemove(BlockState s,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!s.is(next.getBlock())&&!level.isClientSide){BlockPos a=anchor(pos,s);if(level.hasChunkAt(a)&&level.getBlockEntity(a) instanceof LabFixtureEntity be&&!be.removing)level.destroyBlock(a,true);}
        super.onRemove(s,level,pos,next,moving);
    }
    @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override public BlockState mirror(BlockState s,Mirror m){return m==Mirror.NONE?s:rotate(s,m.getRotation(s.getValue(FACING))).setValue(X,2-s.getValue(X));}
}
