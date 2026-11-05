package dev.hui.resonantindustry;

import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class IndustryBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE=BooleanProperty.create("active");
    public static final BooleanProperty ASSEMBLED=BooleanProperty.create("assembled");
    public static final IntegerProperty FILL=IntegerProperty.create("fill",0,4);
    public final String role;
    public IndustryBlock(String role){
        super(Properties.of().strength(3.5F).sound(SoundType.METAL).noOcclusion().lightLevel(s->s.getValue(ACTIVE)?7:0));
        this.role=role;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ACTIVE,false).setValue(FILL,0).setValue(ASSEMBLED,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,ACTIVE,FILL,ASSEMBLED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override public BlockState mirror(BlockState s,Mirror m){return rotate(s,m.getRotation(s.getValue(FACING)));}
    @Override public RenderShape getRenderShape(BlockState s){return role.equals("cable")||role.equals("fluid_pipe")||s.getValue(ASSEMBLED)?RenderShape.INVISIBLE:RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new IndustryNode(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState s,BlockEntityType<T> type){
        if(level.isClientSide||!java.util.Set.of("controller","cable","fluid_pipe","battery","tank","pump").contains(role))return null;
        return createTickerHelper(type,IndustryContent.NODE,IndustryNode::tick);
    }
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(l.isClientSide)return InteractionResult.SUCCESS;
        return l.getBlockEntity(p) instanceof IndustryNode node&&player instanceof ServerPlayer user?node.interact(user):InteractionResult.PASS;
    }
    @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){
        if(!s.is(next.getBlock())&&!l.isClientSide&&l.getBlockEntity(p) instanceof IndustryNode node)node.broken();
        super.onRemove(s,l,p,next,moving);
    }
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        if(role.equals("cable"))return Connections.shape(l,p,false);
        if(role.equals("fluid_pipe"))return Connections.shape(l,p,true);
        return Shapes.block();
    }
}
