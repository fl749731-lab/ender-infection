package dev.hui.resonantindustry;

import java.util.Set;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.level.block.Block;

/** The visible arms, selection outline and collision all use the same actual neighbor connections. */
public final class Connections {
    public static boolean connected(BlockGetter world,BlockPos pos,Direction side,boolean fluid){
        var at=pos.relative(side);if(world instanceof Level level&&!IndustryNode.loaded(level,at))return false;var block=world.getBlockState(at).getBlock();
        if(block instanceof IndustryBlock b)return (fluid?Set.of("fluid_pipe","fluid_input","fluid_output","tank","pump"):Set.of("cable","energy_port","battery","pump")).contains(b.role);
        if(world instanceof Level level&&IndustryNode.loaded(level,at))return fluid?Platform.fluid(level,at,side.getOpposite())!=null:Platform.energy(level,at,side.getOpposite())!=null;
        return false;
    }
    public static VoxelShape shape(BlockGetter world,BlockPos pos,boolean fluid){
        double r=fluid?2.5:1.5,lo=8-r,hi=8+r;var shape=Block.box(lo,lo,lo,hi,hi,hi);
        for(var d:Direction.values())if(connected(world,pos,d,fluid))shape=Shapes.or(shape,switch(d){
            case NORTH->Block.box(lo,lo,0,hi,hi,8);case SOUTH->Block.box(lo,lo,8,hi,hi,16);
            case WEST->Block.box(0,lo,lo,8,hi,hi);case EAST->Block.box(8,lo,lo,16,hi,hi);
            case DOWN->Block.box(lo,0,lo,hi,8,hi);case UP->Block.box(lo,8,lo,hi,16,hi);
        });return shape;
    }
    private Connections(){}
}
