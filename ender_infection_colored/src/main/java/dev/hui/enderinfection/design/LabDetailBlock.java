package dev.hui.enderinfection.design;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.*;

/** Baked, directional single-block room dressing; no per-tick block entity cost. */
public final class LabDetailBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
    private final String key;
    public boolean isLamp(){return key.equals("lab_light_panel")||key.equals("lab_task_lamp");}
    public LabDetailBlock(String key,Properties p){super(p.lightLevel(s->(key.equals("lab_light_panel")||key.equals("lab_task_lamp"))&&s.getValue(POWERED)?14:0));this.key=key;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(POWERED,true));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,POWERED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override public BlockState mirror(BlockState s,Mirror m){return rotate(s,m.getRotation(s.getValue(FACING)));}
    @Override public VoxelShape getShape(BlockState s,BlockGetter level,BlockPos p,CollisionContext c){
        VoxelShape shape=switch(key){
            case "lab_microscope"->Shapes.or(box(2,0,2,14,2,14),box(8,2,6,12,12,11),box(3,8,3,10,13,9));
            case "lab_centrifuge"->box(1,0,2,15,10,14);
            case "lab_cooling_pump"->box(1,0,1,15,14,15);
            case "lab_task_lamp"->Shapes.or(box(5,0,5,11,2,11),box(7,2,7,9,13,9),box(3,12,3,13,16,13));
            case "lab_monitor"->box(1,4,8,15,15,15);
            case "lab_sample_rack"->box(2,1,4,14,9,12);
            case "lab_pipe"->box(0,8,4,16,16,12);
            case "lab_light_panel"->box(0,13.5,0,16,16,16);
            case "lab_chair"->box(3,0,3,13,16,13);
            case "lab_sink"->box(0,0,1,16,16,15);
            case "lab_floor_grate"->box(0,13,0,16,16,16);
            default->box(1,0,1,15,16,15);
        };return LabFixtureBlock.rotate(shape,s.getValue(FACING));
    }
}
