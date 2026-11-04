package dev.hui.enderinfection.test;
import dev.hui.enderinfection.design.*;
import dev.hui.enderinfection.worldgen.ResearchLabPlacement;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;

@GameTestHolder("ender_lab_checks")
@PrefixGameTestTemplate(false)
public final class LabLayoutGameTests {
    private static void clearFixtures(GameTestHelper h,BlockPos p){
        for(var at:BlockPos.betweenClosed(p,p.offset(43,20,39)))if(h.getLevel().getBlockState(at).getBlock() instanceof LabFixtureBlock)h.getLevel().removeBlock(at,false);
    }
    @GameTest(template="empty64",templateNamespace="ender_lab_checks",timeoutTicks=300,batch="lab_rooms")
    public static void sixBiomeFacilitiesHaveDistinctSizesAndThemedSupplies(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(2,1,2));var plans=new java.util.HashSet<String>();var biomes=new java.util.HashSet<String>();int total=0;
        for(var spec:ResearchLabPlacement.SPECS){
            clearFixtures(h,p);h.assertTrue(ResearchLabPlacement.place(level,p,spec),"Failed to place "+spec.key());
            var template=level.getStructureManager().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ender_infection",spec.key())).orElseThrow();
            h.assertTrue(template.getSize().equals(new Vec3i(spec.width(),spec.height(),spec.length())),"Wrong template dimensions");
            plans.add(template.save(new net.minecraft.nbt.CompoundTag()).toString());biomes.add(spec.biome());int chests=0,lifts=0,openBays=0;
            for(var at:BlockPos.betweenClosed(p,p.offset(spec.width()-1,spec.height()-1,spec.length()-1))){
                var state=level.getBlockState(at);h.assertTrue(!state.is(Blocks.LADDER),"Wood ladder survived in "+spec.key());
                if(state.getBlock() instanceof LabLiftBlock)lifts++;
                if(level.getBlockEntity(at) instanceof ChestBlockEntity chest){chests++;h.assertTrue(chest.saveWithFullMetadata(level.registryAccess()).getString("LootTable").startsWith("ender_infection:chests/lab_"),"Missing themed loot");}
            }
            h.assertTrue(chests==spec.rooms(),"Wrong room/supply count for "+spec.key()+": "+chests);h.assertTrue(lifts==spec.floors()+1,"Missing connected lift landing");total+=chests;
            h.assertTrue(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ender_infection",spec.key())),"Missing natural structure");
        }
        h.assertTrue(plans.size()==6&&biomes.size()==6&&total==56,"Biome facilities still reuse a single plan");h.succeed();
    }
    @GameTest(template="empty64",templateNamespace="ender_lab_checks",timeoutTicks=100,batch="lab_entrance")
    public static void surfaceEntranceHasMetalServiceAccessAndLift(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(2,1,2));var spec=ResearchLabPlacement.SPECS.get(0);h.assertTrue(ResearchLabPlacement.place(level,p,spec),"Lab placement failed");
        var door=level.getBlockState(p.offset(spec.entrance()));h.assertTrue(door.getBlock() instanceof LabFixtureBlock&&!door.getValue(LabFixtureBlock.OPEN),"Missing closed entrance door");
        var ladder=p.offset(spec.width()/2+2,2,5);h.assertTrue(level.getBlockState(ladder).getBlock() instanceof LabAccessLadderBlock&&level.getBlockState(ladder).is(net.minecraft.tags.BlockTags.CLIMBABLE),"Metal service ladder is not climbable");
        for(int floor=0;floor<=spec.floors();floor++)h.assertTrue(level.getBlockState(p.offset(spec.lift(floor))).getBlock() instanceof LabLiftBlock,"No working landing");h.succeed();
    }
    @GameTest(template="empty64",templateNamespace="ender_lab_checks",timeoutTicks=100,batch="lab_lift")
    public static void liftMovesPlayersBothWaysAndRejectsObstructedLandings(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(2,1,2));var spec=ResearchLabPlacement.SPECS.get(0);h.assertTrue(ResearchLabPlacement.place(level,p,spec),"Lab placement failed");
        var lower=p.offset(spec.lift(0));var upper=p.offset(spec.lift(1));var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(lower.getX()+.5,lower.getY()+1,lower.getZ()+.5);
        var block=(LabLiftBlock)level.getBlockState(lower).getBlock();var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(lower),Direction.UP,lower,false);
        level.setBlock(upper.above(),Blocks.STONE.defaultBlockState(),3);block.use(level.getBlockState(lower),level,lower,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);h.assertTrue(Math.abs(player.getY()-lower.getY()-1)<.01,"Lift teleported into obstruction");
        level.setBlock(upper.above(),Blocks.AIR.defaultBlockState(),3);block.use(level.getBlockState(lower),level,lower,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);h.assertTrue(Math.abs(player.getY()-upper.getY()-1)<.01,"Lift failed to ascend");
        player.setShiftKeyDown(true);block.use(level.getBlockState(upper),level,upper,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);h.assertTrue(Math.abs(player.getY()-lower.getY()-1)<.01,"Lift failed to descend");player.discard();h.succeed();
    }
    private static double walkStandingPlayer(GameTestHelper h,net.minecraft.world.entity.player.Player player,net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.Vec3 direction,int steps){
        player.getAbilities().flying=false;player.setPos(start.x,start.y,start.z);player.setOnGround(true);
        for(int i=0;i<steps;i++){
            player.move(net.minecraft.world.entity.MoverType.SELF,direction.scale(.1).add(0,-.08,0));
            player.setOnGround(true);
        }
        return player.position().subtract(start).dot(direction);
    }
    @GameTest(template="empty64",templateNamespace="ender_lab_checks",timeoutTicks=100,batch="lab_player_doors")
    public static void standingPlayerCanWalkThroughOpenDoorsInEveryFacing(GameTestHelper h){
        var level=h.getLevel();for(int x=1;x<38;x++)for(int z=1;z<24;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);int i=0;
        for(String key:new String[]{"lab_door","lab_door_eroded"})for(Direction face:Direction.Plane.HORIZONTAL){
            var block=(LabFixtureBlock)DesignContent.BLOCKS_BY_KEY.get(key).get();var p=h.absolutePos(new BlockPos(5+(i%4)*8,1,5+(i/4)*10));i++;
            var closed=block.defaultBlockState().setValue(LabFixtureBlock.FACING,face);level.setBlock(p,closed,3);block.fillParts(level,p,closed);
            var forward=new net.minecraft.world.phys.Vec3(-face.getStepX(),0,-face.getStepZ());var start=net.minecraft.world.phys.Vec3.atBottomCenterOf(p).subtract(forward.scale(1.2));
            double stopped=walkStandingPlayer(h,player,start,forward,30);h.assertTrue(stopped<1,"Closed door failed to stop player: "+key+"/"+face);
            level.setBlock(p,closed.setValue(LabFixtureBlock.OPEN,true),3);
            double crossed=walkStandingPlayer(h,player,start,forward,30);h.assertTrue(crossed>2.5,"Open door blocks standing player: "+key+"/"+face+", travelled="+crossed+", height="+player.getBbHeight());
        }
        player.discard();h.succeed();
    }
    @GameTest(template="empty64",templateNamespace="ender_lab_checks",timeoutTicks=300,batch="lab_player_entrance")
    public static void standingPlayerCanEnterEveryBiomeFacility(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(2,1,2));var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        for(var spec:dev.hui.enderinfection.worldgen.ResearchLabPlacement.SPECS){
            clearFixtures(h,p);h.assertTrue(dev.hui.enderinfection.worldgen.ResearchLabPlacement.place(level,p,spec),"Lab placement failed");
            var door=p.offset(spec.entrance());level.setBlock(door,level.getBlockState(door).setValue(LabFixtureBlock.OPEN,true),3);
            var start=new net.minecraft.world.phys.Vec3(door.getX()+.5,door.getY(),door.getZ()-1.5);
            double crossed=walkStandingPlayer(h,player,start,new net.minecraft.world.phys.Vec3(0,0,1),30);
            h.assertTrue(crossed>2.8,"Standing player cannot enter "+spec.key()+": travelled="+crossed);
        }player.discard();h.succeed();
    }
    @GameTest(template="empty64",templateNamespace="ender_lab_checks",timeoutTicks=40,batch="lab_lighting")
    public static void footprintLightingNeverReadsAnAdjacentChunk(GameTestHelper h){
        net.minecraft.world.level.BlockGetter strict=new net.minecraft.world.level.BlockGetter(){
            public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos p){throw new AssertionError("Lighting read adjacent block entity");}
            public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos p){throw new AssertionError("Lighting requested adjacent block/chunk");}
            public net.minecraft.world.level.material.FluidState getFluidState(BlockPos p){throw new AssertionError("Lighting requested adjacent fluid/chunk");}
            public int getHeight(){return 384;}public int getMinBuildHeight(){return -64;}
        };
        var pos=new BlockPos(16,65,16);
        for(Direction face:Direction.Plane.HORIZONTAL)for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            var state=DesignContent.PART.get().defaultBlockState().setValue(LabPartBlock.FACING,face).setValue(LabPartBlock.X,x).setValue(LabPartBlock.Y,y);
            h.assertTrue(state.getLightBlock(strict,pos)==0&&state.propagatesSkylightDown(strict,pos)&&state.getOcclusionShape(strict,pos).isEmpty(),"Invisible footprint should not obstruct lighting");
        }h.succeed();
    }
}
