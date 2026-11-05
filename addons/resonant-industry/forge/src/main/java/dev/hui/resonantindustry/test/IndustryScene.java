package dev.hui.resonantindustry.test;

import dev.hui.resonantindustry.*;
import dev.hui.resonantindustry.api.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** Opt-in acceptance fixture. Always use an independent build/ world. */
public final class IndustryScene {
    public final ServerLevel level;public final BlockPos origin;public final Set<ChunkPos> chunks=new LinkedHashSet<>();
    public IndustryScene(ServerLevel level,BlockPos origin){this.level=level;this.origin=origin;for(int x=(origin.getX()-18)>>4;x<=(origin.getX()+4)>>4;x++)for(int z=(origin.getZ()-5)>>4;z<=(origin.getZ()+7)>>4;z++)chunks.add(new ChunkPos(x,z));}
    public void force(boolean on){for(var c:chunks){level.setChunkForced(c.x,c.z,on);if(on)level.getChunk(c.x,c.z);}}
    public boolean ready(){return chunks.stream().allMatch(c->level.getChunkSource().getChunkNow(c.x,c.z)!=null&&level.areEntitiesLoaded(c.toLong()));}
    public IndustryNode generator(){return IndustryNode.at(level,origin);}public IndustryNode processor(){return IndustryNode.at(level,origin.west(6));}public IndustryNode centrifuge(){return IndustryNode.at(level,origin.west(12));}
    public void build(){
        force(true);for(int x=-18;x<=4;x++)for(int z=-5;z<=7;z++)level.setBlock(origin.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),18);
        machine(IndustryApi.GENERATOR,0);machine(IndustryApi.PROCESSOR,-6);machine(IndustryApi.CENTRIFUGE,-12);
        for(int x=-2;x>=-14;x--)put(x,-1,-1,"cable");
        for(int x:new int[]{-2,-8,-14}){put(x,0,-1,"cable");for(int z=0;z<=1;z++)put(x,0,z,"cable");}
        for(int z=2;z<=4;z++)put(-8,0,z,"fluid_pipe");for(int x=-9;x>=-10;x--)put(x,0,4,"fluid_pipe");
    }
    private void put(int x,int y,int z,String role){level.setBlock(origin.offset(x,y,z),IndustryContent.block(role).defaultBlockState(),3);}
    private void machine(String id,int dx){
        put(dx,0,0,"controller");var n=IndustryNode.at(level,origin.offset(dx,0,0));if(!n.configure(id))throw new IllegalStateException("Scene programming failed");
        for(var e:n.definition().cells().entrySet()){
            if(e.getValue().equals("controller"))continue;var c=e.getKey();var state=e.getValue().equals("air")?Blocks.AIR.defaultBlockState():IndustryContent.block(e.getValue()).defaultBlockState();
            if(!e.getValue().equals("air"))state=state.setValue(IndustryBlock.FACING,c.x()<0?Direction.WEST:c.x()>0?Direction.EAST:c.z()==n.definition().length()-1?Direction.SOUTH:Direction.NORTH);
            level.setBlock(n.world(c),state,3);
        }
    }
}
