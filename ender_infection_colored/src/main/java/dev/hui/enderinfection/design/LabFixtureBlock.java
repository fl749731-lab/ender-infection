package dev.hui.enderinfection.design;

import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Large props reserve their footprint with lightweight parts; one animated BE per prop. */
public final class LabFixtureBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN=BlockStateProperties.OPEN;
    public static final BooleanProperty SECURED=BooleanProperty.create("secured");
    public final String key;
    public LabFixtureBlock(String key,Properties p){super(p);this.key=key;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(OPEN,false).setValue(SECURED,false));}
    public String base(){return key.replace("_eroded","");}
    public static boolean isStorageKey(String key){return Set.of("lab_cabinet","lab_workbench","lab_glass_cabinet","lab_cryo_cabinet","lab_server_rack","lab_control_console").contains(key.replace("_eroded",""));}
    public boolean isStorage(){return isStorageKey(key);}
    public int height(){return base().equals("incubator")?3:Set.of("lab_door","lab_cabinet","surgery_table","lab_workbench","lab_window","lab_glass_cabinet","lab_cryo_cabinet","lab_server_rack","lab_control_console","lab_privacy_curtain").contains(base())?2:1;}
    public int halfWidth(){return Set.of("surgery_table","lab_workbench","lab_window","lab_control_console").contains(base())?1:0;}
    public static BlockPos cell(BlockPos anchor,Direction facing,int x,int y){return anchor.relative(facing.getClockWise(),x).above(y);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,OPEN,SECURED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        Direction f=c.getHorizontalDirection().getOpposite();
        for(int x=-halfWidth();x<=halfWidth();x++)for(int y=0;y<height();y++){
            BlockPos p=cell(c.getClickedPos(),f,x,y);if(!c.getLevel().isInWorldBounds(p)||!c.getLevel().hasChunkAt(p)||!c.getLevel().getBlockState(p).canBeReplaced(c)||!c.getLevel().getFluidState(p).isEmpty())return null;
        }
        return defaultBlockState().setValue(FACING,f);
    }
    public void fillParts(Level level,BlockPos pos,BlockState state){
        for(int x=-halfWidth();x<=halfWidth();x++)for(int y=0;y<height();y++)if(x!=0||y!=0){BlockPos p=cell(pos,state.getValue(FACING),x,y);
            level.setBlock(p,DesignContent.PART.get().defaultBlockState().setValue(LabPartBlock.FACING,state.getValue(FACING)).setValue(LabPartBlock.X,x+1).setValue(LabPartBlock.Y,y),3);
        }
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity who,ItemStack stack){super.setPlacedBy(level,pos,state,who,stack);if(!level.isClientSide)fillParts(level,pos,state);}
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!state.is(next.getBlock())&&!level.isClientSide){
            if(level.getBlockEntity(pos) instanceof LabFixtureEntity be&&!be.removing){be.removing=true;if(be.isStorage()){Containers.dropContents(level,pos,be);be.clearContent();}}
            for(int x=-halfWidth();x<=halfWidth();x++)for(int y=0;y<height();y++)if(x!=0||y!=0){BlockPos p=cell(pos,state.getValue(FACING),x,y);if(!level.hasChunkAt(p))continue;var s=level.getBlockState(p);if(s.getBlock() instanceof LabPartBlock&&LabPartBlock.anchor(p,s).equals(pos))level.removeBlock(p,false);}
        }
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public InteractionResult use(BlockState s,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(isStorage()){
            if(!level.isClientSide&&player instanceof net.minecraft.server.level.ServerPlayer server&&level.getBlockEntity(pos) instanceof LabFixtureEntity inventory)server.openMenu(inventory);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(!Set.of("lab_hatch","lab_door","lab_privacy_curtain").contains(base()))return InteractionResult.PASS;
        if(base().equals("lab_door")&&level instanceof net.minecraft.server.level.ServerLevel server&&!dev.hui.enderinfection.institute.InstituteOperations.mayUseDoor(server,pos,s,player))return InteractionResult.FAIL;
        if(!level.isClientSide){boolean open=!s.getValue(OPEN);level.setBlock(pos,s.setValue(OPEN,open),3);level.playSound(null,pos,open?net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN:net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,net.minecraft.sounds.SoundSource.BLOCKS,.7F,1);}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void tick(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random){
        if(level.getBlockEntity(pos) instanceof LabFixtureEntity inventory)inventory.recheckOpeners();
    }
    public VoxelShape localShape(BlockState state,int x,int y){
        String b=base();
        if(b.equals("lab_hatch")&&state.getValue(OPEN))return Shapes.empty();
        if(b.equals("lab_privacy_curtain"))return state.getValue(OPEN)?Shapes.or(Block.box(0,0,6,1,16,10),Block.box(15,0,6,16,16,10)):Block.box(0,0,7,16,16,9);
        // One-pixel frame leaves 1.875 blocks of clear height for a standing 1.8-block player.
        if(b.equals("lab_door")&&state.getValue(OPEN))return Shapes.or(Block.box(0,0,6,1,16,10),Block.box(15,0,6,16,16,10),y==0?Block.box(0,0,6,16,1,10):Block.box(0,15,6,16,16,10));
        if(b.equals("lab_vent")||b.equals("lab_hatch"))return Block.box(0,0,13.5,16,16,16);
        if(b.equals("surgery_table")&&y==0)return x==0?Block.box(0,0,3,16,14,13):Block.box(x<0?8:0,10,3,x<0?16:8,14,13);
        if(b.equals("surgery_table")&&y==1)return x==0?Block.box(2,0,8,10,15,13):Shapes.empty();
        if(b.equals("lab_window")||b.equals("lab_door")||b.equals("lab_hatch")||b.equals("lab_vent"))return Block.box(0,0,6,16,16,10);
        if(b.equals("lab_workbench")||b.equals("lab_control_console"))return y==0?Block.box(0,0,0,16,14,16):Block.box(0,0,0,16,11,5);
        return Block.box(0,0,2,16,y==height()-1?(b.equals("incubator")?5:b.equals("lab_cabinet")?14:16):16,14);
    }
    public static VoxelShape rotate(VoxelShape shape,Direction facing){
        if(facing==Direction.NORTH)return shape;var result=new VoxelShape[]{Shapes.empty()};
        shape.forAllBoxes((x,y,z,X,Y,Z)->{var box=switch(facing){case SOUTH->Shapes.box(1-X,y,1-Z,1-x,Y,1-z);case EAST->Shapes.box(1-Z,y,x,1-z,Y,X);case WEST->Shapes.box(z,y,1-X,Z,Y,1-x);default->Shapes.box(x,y,z,X,Y,Z);};result[0]=Shapes.or(result[0],box);});return result[0];
    }
    @Override public VoxelShape getShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext c){return rotate(localShape(s,0,0),s.getValue(FACING));}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.ENTITYBLOCK_ANIMATED;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState s){return new LabFixtureEntity(pos,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState s,BlockEntityType<T> type){return createTickerHelper(type,DesignContent.FIXTURE_ENTITY.get(),LabFixtureEntity::tick);}
    @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override public BlockState mirror(BlockState s,Mirror m){return rotate(s,m.getRotation(s.getValue(FACING)));}
}
