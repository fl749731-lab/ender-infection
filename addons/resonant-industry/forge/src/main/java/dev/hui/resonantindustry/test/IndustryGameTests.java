package dev.hui.resonantindustry.test;

import dev.hui.resonantindustry.*;
import dev.hui.resonantindustry.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;

@GameTestHolder(IndustryApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IndustryGameTests {
    private static final AtomicInteger NEXT=new AtomicInteger();
    private static CompoundTag save(IndustryNode n){return n.saveWithoutMetadata();}
    private static void load(IndustryNode n,CompoundTag tag){n.load(tag);}
    static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final BlockPos origin;
        final Set<BlockPos> placed=new LinkedHashSet<>();final Set<ChunkPos> chunks=new LinkedHashSet<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;level=h.getLevel();origin=new BlockPos(100000+NEXT.getAndIncrement()*128,64,100000);}
        void put(BlockPos p,String role){put(p,role.equals("air")?Blocks.AIR.defaultBlockState():IndustryContent.block(role).defaultBlockState());}
        void put(BlockPos p,net.minecraft.world.level.block.state.BlockState s){var cp=new ChunkPos(p);if(!level.getForcedChunks().contains(cp.toLong())){level.setChunkForced(cp.x,cp.z,true);chunks.add(cp);}level.getChunk(cp.x,cp.z);placed.add(p.immutable());if(!level.getBlockState(p).equals(s))level.setBlock(p,s,3);}
        IndustryNode machine(String id,BlockPos at,Direction facing){
            put(at,IndustryContent.block("controller").defaultBlockState().setValue(IndustryBlock.FACING,facing));var n=IndustryNode.at(level,at);h.assertTrue(n.configure(id),"Configure failed");
            for(var e:n.definition().cells().entrySet())if(!e.getValue().equals("controller"))put(n.world(e.getKey()),e.getValue());return n;
        }
        IndustryNode machine(String id,int x){return machine(id,origin.offset(x,0,0),Direction.NORTH);}
        IndustryNode port(IndustryNode n,String role){var cell=n.definition().cells().entrySet().stream().filter(e->e.getValue().equals(role)).findFirst().orElseThrow();return IndustryNode.at(level,n.world(cell.getKey()));}
        void form(IndustryNode n){var result=n.form();h.assertTrue(result.valid(),"Formation failed: "+result);}
        void charge(IndustryNode n,int amount){var p=port(n,"energy_port");var e=Platform.energy(level,p.getBlockPos(),Direction.WEST);int remaining=amount;while(remaining>0){int got=e.receive(remaining,false);h.assertTrue(got>0,"Fixture energy input rejected");remaining-=got;}}
        void water(IndustryNode n,String fluid,int amount){var p=port(n,"fluid_input");h.assertTrue(Platform.fluid(level,p.getBlockPos(),Direction.EAST).fill(fluid,amount,false)==amount,"Fluid input rejected");}
        void after(int delay,Runnable action){h.runAfterDelay(delay,()->{if(closed)return;try{action.run();}catch(RuntimeException|Error error){close();throw error;}});}
        void each(Consumer<Fixture> action){each(1100,action);}
        void each(int timeout,Consumer<Fixture> action){after(timeout,()->{close();h.fail("Industry fixture timed out");});h.onEachTick(()->{if(closed)return;try{action.accept(this);}catch(RuntimeException|Error error){close();throw error;}});}
        boolean ready(){return chunks.stream().allMatch(cp->level.areEntitiesLoaded(cp.toLong()));}
        void whenReady(Runnable action){var started=new boolean[1];each(f->{if(!started[0]&&ready()){started[0]=true;action.run();}});}
        void success(){close();h.succeed();}
        @Override public void close(){if(closed)return;closed=true;for(var p:placed)if(IndustryNode.loaded(level,p)&&level.getBlockEntity(p) instanceof IndustryNode n&&n.role().equals("controller"))n.clearContent();for(var p:placed)if(IndustryNode.loaded(level,p))level.setBlock(p,Blocks.AIR.defaultBlockState(),3);for(var cp:chunks)level.setChunkForced(cp.x,cp.z,false);}
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="industry_layouts")
    public static void threeRealLayoutsFormInAllFourDirections(GameTestHelper h){
        try(var f=new Fixture(h)){int x=0;for(String id:List.of(IndustryApi.GENERATOR,IndustryApi.PROCESSOR,IndustryApi.CENTRIFUGE))for(var direction:Direction.Plane.HORIZONTAL){var n=f.machine(id,f.origin.offset(x,0,0),direction);f.form(n);h.assertTrue(n.operational(),"Rotated machine did not validate");x+=12;}h.succeed();}
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="industry_missing")
    public static void missingBlockAndBlockedInteriorDoNotClaimParts(GameTestHelper h){
        try(var f=new Fixture(h)){var n=f.machine(IndustryApi.PROCESSOR,0);var cell=n.definition().cells().entrySet().stream().filter(e->e.getValue().equals("casing")).findFirst().orElseThrow();f.put(n.world(cell.getKey()),"air");h.assertTrue(!n.form().valid()&&!n.formed(),"Incomplete machine formed");f.put(n.world(cell.getKey()),"casing");var air=n.definition().cells().entrySet().stream().filter(e->e.getValue().equals("air")).findFirst().orElseThrow();f.put(n.world(air.getKey()),Blocks.STONE.defaultBlockState());h.assertTrue(!n.form().valid(),"Blocked chamber formed");f.put(n.world(air.getKey()),"air");f.form(n);h.succeed();}
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="industry_overlap")
    public static void adjacentMachinesCannotShareClaimedCasing(GameTestHelper h){
        try(var f=new Fixture(h)){var a=f.machine(IndustryApi.GENERATOR,0);f.form(a);var b=f.machine(IndustryApi.GENERATOR,f.origin.offset(2,0,2),Direction.NORTH);h.assertTrue(a.operational(),"Second fixture unexpectedly broke first structure");var check=b.form();h.assertTrue(!check.valid()&&check.expected().equals("claim"),"Shared casing was claimed twice: "+check);h.succeed();}
    }
    @GameTest(template="empty_test",timeoutTicks=160,batch="industry_fuel")
    public static void coalGeneratesRealEnergyAndFullBufferPausesFuel(GameTestHelper h){
        var f=new Fixture(h);var n=f.machine(IndustryApi.GENERATOR,0);f.form(n);n.setItem(0,new ItemStack(Items.COAL,2));
        f.whenReady(()->f.after(20,()->{h.assertTrue(n.energy()>0&&n.burn()>0&&n.getItem(0).getCount()==1,"Generator did not burn one coal: "+save(n));var tag=save(n);tag.putInt("Energy",n.energyCapacity());load(n,tag);int burn=n.burn();f.after(20,()->{h.assertTrue(n.energy()==n.energyCapacity()&&n.burn()==burn&&n.getItem(0).getCount()==1,"Full buffer destroyed energy or reserved fuel");f.success();});}));
    }
    @GameTest(template="empty_test",timeoutTicks=1250,batch="industry_chain")
    public static void coalCablesAndPipesCompleteTheBiomassProductionChain(GameTestHelper h){
        var f=new Fixture(h);var g=f.machine(IndustryApi.GENERATOR,0);var p=f.machine(IndustryApi.PROCESSOR,-6);var c=f.machine(IndustryApi.CENTRIFUGE,-12);f.form(g);f.form(p);f.form(c);
        for(int x=-2;x>=-14;x--)f.put(f.origin.offset(x,0,-1),"cable");
        for(int x:new int[]{-2,-8,-14})for(int z=0;z<=1;z++)f.put(f.origin.offset(x,0,z),"cable");
        for(int z=2;z<=4;z++)f.put(f.origin.offset(-8,0,z),"fluid_pipe");for(int x=-9;x>=-10;x--)f.put(f.origin.offset(x,0,4),"fluid_pipe");
        g.setItem(0,new ItemStack(Items.COAL));p.setItem(0,new ItemStack(IndustryContent.item("ender_infection:mycelium_bundle")));f.water(p,"minecraft:water",1000);c.setItem(0,new ItemStack(Items.GLASS_BOTTLE));
        f.each(x->{if(h.getTick()%100==0)System.out.println("INDUSTRY_CHAIN tick="+h.getTick()+" ready="+f.ready()+" g="+save(g)+" p="+save(p)+" c="+save(c));if(c.getItem(2).is(IndustryContent.item("ender_infection:separated_reagent"))){
            h.assertTrue(c.getItem(2).getCount()==1&&c.getItem(3).is(IndustryContent.item("fuel_residue"))&&c.getItem(3).getCount()==1,"Wrong chain output");
            h.assertTrue(p.getItem(0).isEmpty()&&c.getItem(0).isEmpty()&&g.getItem(0).isEmpty(),"Chain did not consume its original materials");
            h.assertTrue(p.energy()>0&&c.energy()>0,"Machines bypassed the real cable network");x.success();}});
    }
    @GameTest(template="empty_test",timeoutTicks=160,batch="industry_pause")
    public static void outputFullPausesWithoutConsumingMaterialsOrEnergy(GameTestHelper h){
        var f=new Fixture(h);var n=f.machine(IndustryApi.CENTRIFUGE,0);f.form(n);f.charge(n,10000);f.water(n,IndustryApi.SLURRY,1000);n.setItem(0,new ItemStack(Items.GLASS_BOTTLE));n.setItem(2,new ItemStack(IndustryContent.item("ender_infection:separated_reagent"),64));int energy=n.energy();
        f.after(30,()->{h.assertTrue(n.energy()==energy&&n.progress()==0&&n.getItem(0).getCount()==1&&save(n).getInt("InputAmount")==1000,"Full output consumed materials, fluid or power");f.success();});
    }
    @GameTest(template="empty_test",timeoutTicks=400,batch="industry_saved")
    public static void savedProgressAndFluidsResumeWithoutRepeatingOutput(GameTestHelper h){
        var f=new Fixture(h);var n=f.machine(IndustryApi.CENTRIFUGE,0);f.form(n);f.charge(n,10000);f.water(n,IndustryApi.SLURRY,1000);n.setItem(0,new ItemStack(Items.GLASS_BOTTLE));
        f.after(70,()->{var before=save(n);h.assertTrue(n.progress()>0&&n.progress()<200,"Recipe did not advance before save");load(n,before);h.assertTrue(save(n).equals(before),"Save/load changed reserved inputs or progress");f.after(180,()->{h.assertTrue(n.getItem(2).getCount()==1&&n.getItem(3).getCount()==1&&save(n).getInt("InputAmount")==0,"Reload lost or repeated a batch");f.success();});});
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="industry_fluids")
    public static void simulatedTransfersRejectMixedFluidsAndNeverMutateStorage(GameTestHelper h){
        try(var f=new Fixture(h)){var at=f.origin;f.put(at,"tank");var n=IndustryNode.at(f.level,at);var view=Platform.fluid(f.level,at,Direction.UP);
            h.assertTrue(view.fill("minecraft:water",1000,true)==1000&&view.amount()==0,"Simulated fill mutated tank");view.fill("minecraft:water",1000,false);
            h.assertTrue(view.fill(IndustryApi.SLURRY,1000,false)==0&&view.amount()==1000,"Different fluids mixed");h.assertTrue(view.drain(400,true)==400&&view.amount()==1000,"Simulated drain mutated tank");
            h.assertTrue(view.drain(-1,false)==0&&view.fill("minecraft:water",-1,false)==0,"Negative transfer accepted");h.assertTrue(view.drain(2000,false)==1000&&view.amount()==0,"Tank overdraw duplicated fluid");h.succeed();}
    }
    @GameTest(template="empty_test",timeoutTicks=160,batch="industry_break")
    public static void brokenPortKeepsInventoryAndStaleCapabilitiesStopWorking(GameTestHelper h){
        var f=new Fixture(h);try{var n=f.machine(IndustryApi.CENTRIFUGE,0);f.form(n);n.setItem(0,new ItemStack(Items.GLASS_BOTTLE,5));f.charge(n,2048);var port=f.port(n,"energy_port");var cached=Platform.energy(f.level,port.getBlockPos(),Direction.WEST);
            f.level.setBlock(port.getBlockPos(),Blocks.AIR.defaultBlockState(),3);h.assertTrue(!n.operational()&&n.getItem(0).getCount()==5,"Breaking a port lost controller inventory");h.assertTrue(cached.receive(100,false)==0,"Stale port still supplied a disassembled machine");
            f.put(port.getBlockPos(),"energy_port");f.form(n);h.assertTrue(Platform.energy(f.level,port.getBlockPos(),Direction.WEST).receive(100,false)==100,"Rebuilt port was not usable");
            f.level.setBlock(n.getBlockPos(),Blocks.AIR.defaultBlockState(),3);f.whenReady(()->f.after(3,()->{int count=f.level.getEntitiesOfClass(ItemEntity.class,new AABB(n.getBlockPos()).inflate(3)).stream().filter(e->e.getItem().is(Items.GLASS_BOTTLE)).mapToInt(e->e.getItem().getCount()).sum();h.assertTrue(count==5,"Controller removal duplicated or lost inventory drops: "+count);f.success();}));}catch(RuntimeException|Error error){f.close();throw error;}
    }
    @GameTest(template="empty_test",timeoutTicks=160,batch="industry_pump")
    public static void poweredPumpSuppliesRenewableWaterWithoutRemovingSources(GameTestHelper h){
        var f=new Fixture(h);var at=f.origin;for(int dx=-2;dx<=2;dx++)for(int dz=-1;dz<=1;dz++)f.put(at.offset(dx,-2,dz),Blocks.STONE.defaultBlockState());f.put(at,"pump");f.put(at.below(),Blocks.WATER.defaultBlockState());f.put(at.below().west(),Blocks.WATER.defaultBlockState());f.put(at.below().east(),Blocks.WATER.defaultBlockState());
        var energy=Platform.energy(f.level,at,Direction.WEST);f.whenReady(()->{energy.receive(200,false);f.after(40,()->{var fluid=Platform.fluid(f.level,at,Direction.UP);h.assertTrue(fluid.amount()==2000&&energy.stored()==0,"Pump bypassed energy budget or extraction rate");h.assertTrue(f.level.getFluidState(at.below()).isSource(),"Renewable source was removed");f.success();});});
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="industry_api")
    public static void extensionRegistryFreezesAndCoreRemainsAvailable(GameTestHelper h){
        h.assertTrue(dev.hui.enderinfection.api.institute.InstituteApi.isFrozen(),"Core API failed to initialize alongside addon");
        h.assertTrue(IndustryApi.machines().size()>=3&&IndustryApi.recipes().size()>=5,"First production line not registered");
        boolean rejected=false;try{IndustryApi.registerFuel("minecraft:stone",1);}catch(IllegalStateException expected){rejected=true;}h.assertTrue(rejected,"Late registry mutation accepted");
        var path=System.getProperty("resonant_industry.blueprintOutput","");if(!path.isEmpty())try{IndustryBlueprints.write(java.nio.file.Path.of(path));}catch(java.io.IOException error){h.fail("Blueprint export failed: "+error);}
        h.succeed();
    }
    @GameTest(template="empty_test",timeoutTicks=2350,batch="industry_finite_energy")
    public static void oneCoalProducesExactly102400FeAndThenStops(GameTestHelper h){
        var f=new Fixture(h);var n=f.machine(IndustryApi.GENERATOR,0);f.form(n);n.setItem(0,new ItemStack(Items.COAL));
        var source=Platform.energy(f.level,f.port(n,"energy_port").getBlockPos(),Direction.WEST);var total=new long[1];var done=new boolean[1];
        f.each(2250,x->{total[0]+=source.extract(512,false);h.assertTrue(total[0]<=102400,"One coal generated more than its finite budget: "+total[0]);
            if(!done[0]&&total[0]>0&&n.burn()==0&&n.getItem(0).isEmpty()&&n.energy()==0){done[0]=true;h.assertTrue(total[0]==102400,"Coal energy was lost: "+total[0]);
                f.after(200,()->{h.assertTrue(total[0]==102400&&n.energy()==0&&source.extract(512,false)==0,"Generator continued after fuel expired");h.assertTrue(n.data.get(15)==0&&n.data.get(10)==0,"Finished generator UI still reports generation or fuel");System.out.println("INDUSTRY_ONE_COAL_FINITE_PASS produced="+total[0]+" post_fuel_ticks=200");f.success();});}
        });
    }
}
