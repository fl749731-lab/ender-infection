package dev.hui.enderinfection.test;
import dev.hui.enderinfection.design.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("ender_lab_checks")
@PrefixGameTestTemplate(false)
public final class LabLayoutGameTests {
    @GameTest(template="empty64",templateNamespace="ender_lab_checks",timeoutTicks=100,batch="lab_rooms")
    public static void allRoomsHaveDifferentActualLayoutsAndThemedLoot(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(2,1,2));h.assertTrue(dev.hui.enderinfection.worldgen.ResearchLabPlacement.place(level,p),"Lab placement failed");
        var signatures=new java.util.HashSet<String>();int totalChests=0;
        for(int floor=0;floor<4;floor++)for(int row=0;row<3;row++)for(int side=0;side<2;side++){
            StringBuilder signature=new StringBuilder();int chests=0,details=0;
            for(int u=0;u<12;u++)for(int v=0;v<8;v++)for(int dy=1;dy<=4;dy++){
                var at=p.offset(side==0?1+u:30-u,floor*5+dy,1+row*9+v);var state=level.getBlockState(at);var id=ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString().replace("_eroded","");
                if(!state.isAir())signature.append(u).append(',').append(dy).append(',').append(v).append(':').append(id).append(';');
                if(state.getBlock() instanceof LabDetailBlock)details++;
                if(level.getBlockEntity(at) instanceof ChestBlockEntity chest){chests++;var saved=chest.saveWithFullMetadata();h.assertTrue(saved.getString("LootTable").startsWith("ender_infection:chests/lab_"),"Unthemed loot chest");}
            }
            h.assertTrue(chests==1,"Each room should have one supply chest: "+floor+"/"+row+"/"+side+" -> "+chests);h.assertTrue(details>=10,"Missing vertical/functional room dressing");signatures.add(signature.toString());totalChests+=chests;
        }
        h.assertTrue(signatures.size()==24&&totalChests==24,"Room layouts still repeat");h.succeed();
    }
    @GameTest(template="empty64",templateNamespace="ender_lab_checks",timeoutTicks=100,batch="lab_entrance")
    public static void entranceDoorHasSidelightsLintelAndNoLadderAboveLanding(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(2,1,2));h.assertTrue(dev.hui.enderinfection.worldgen.ResearchLabPlacement.place(level,p),"Lab placement failed");
        var door=level.getBlockState(p.offset(16,21,23));h.assertTrue(door.getBlock() instanceof LabFixtureBlock&&door.getValue(LabFixtureBlock.FACING)==Direction.NORTH&&!door.getValue(LabFixtureBlock.OPEN),"Bad closed entrance door pose");
        for(int x:new int[]{15,17})for(int y=21;y<=24;y++)h.assertTrue(!level.getBlockState(p.offset(x,y,23)).isAir(),"Door sides left as open holes");
        for(int y=23;y<=24;y++)h.assertTrue(!level.getBlockState(p.offset(16,y,23)).isAir(),"Missing lintel");
        for(int y=21;y<=24;y++)h.assertTrue(!level.getBlockState(p.offset(16,y,25)).is(Blocks.LADDER),"Ladder extends pointlessly above landing");
        for(int x:new int[]{15,17})h.assertTrue(level.getBlockState(p.offset(x,24,24)).getBlock() instanceof LabDetailBlock,"Ceiling lights should be flush panels");h.succeed();
    }
    @GameTest(template="empty64",templateNamespace="ender_lab_checks",timeoutTicks=100,batch="lab_stairs")
    public static void allThreeFlightsHaveRealHeadroomAndReachableLandings(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(2,1,2));h.assertTrue(dev.hui.enderinfection.worldgen.ResearchLabPlacement.place(level,p),"Lab placement failed");
        for(int floor=0;floor<3;floor++){
            for(int step=0;step<5;step++)for(int x:new int[]{2,3}){
                var at=p.offset(x,floor*5+1+step,25-step);h.assertTrue(level.getBlockState(at).getBlock() instanceof StairBlock,"Missing stair");
                h.assertTrue(level.getBlockState(at.above()).getCollisionShape(level,at.above()).isEmpty()&&level.getBlockState(at.above(2)).getCollisionShape(level,at.above(2)).isEmpty(),"Upper floor blocks stair headroom");
            }
            var mob=EntityType.ZOMBIE.create(level);mob.setNoAi(true);mob.moveTo(p.getX()+2.5,p.getY()+floor*5+1,p.getZ()+26.5,0,0);level.addFreshEntity(mob);mob.setOnGround(true);
            var path=mob.getNavigation().createPath(p.offset(2,floor*5+6,20),0);h.assertTrue(path!=null&&path.canReach(),"Stair landing has no walkable route for a normal mob on floor "+floor+"; path="+(path==null?"null":path.getNodeCount()+" nodes, reaches="+path.canReach()));mob.discard();
        }h.succeed();
    }
}
