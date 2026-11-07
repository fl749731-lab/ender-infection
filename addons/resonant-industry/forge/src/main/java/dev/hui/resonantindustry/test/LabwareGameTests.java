package dev.hui.resonantindustry.test;

import dev.hui.resonantindustry.*;
import dev.hui.resonantindustry.api.IndustryApi;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(IndustryApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LabwareGameTests {
    private static ItemStack vessel(String name){return new ItemStack(IndustryContent.item(name));}
    private static LabBenchEntity bench(IndustryGameTests.Fixture f){f.put(f.origin,"lab_bench");return (LabBenchEntity)f.level.getBlockEntity(f.origin);}
    private static CompoundTag save(LabBenchEntity n){return n.saveWithoutMetadata();}
    private static void load(LabBenchEntity n,CompoundTag tag){n.load(tag);}
    @GameTest(template="empty_test",timeoutTicks=120,batch="lab_vessels")
    public static void allSixSurvivalRecipesLoadWithLegalOutputStacks(GameTestHelper h){
        for(String name:List.of("reagent_bottle","dropper_bottle","pipette","test_tube","beaker","lab_bench")){var result=h.getLevel().getRecipeManager().byKey(IndustryContent.id(name)).orElseThrow().getResultItem(h.getLevel().registryAccess());h.assertTrue(result.is(IndustryContent.item(name))&&result.getCount()==1,"Survival recipe missing or illegal: "+name);}h.succeed();
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="lab_vessels")
    public static void nativeItemFluidCapabilitiesExposePersistentContents(GameTestHelper h){
        for(String name:List.of("reagent_bottle","dropper_bottle","pipette","test_tube","beaker")){var s=vessel(name);var cap=LabPlatform.capability(s);h.assertTrue(cap!=null&&cap.getContainer()==s,"Native item fluid capability missing: "+name);int volume=LabVesselItem.capacity(s);h.assertTrue(cap.fill(new FluidStack(Fluids.WATER,1000),SIMULATE)==volume&&LabFluids.read(s,h.getLevel()).isEmpty(),"Simulation modified vessel");h.assertTrue(cap.fill(new FluidStack(Fluids.WATER,1000),EXECUTE)==volume&&LabFluids.read(s,h.getLevel()).getAmount()==volume,"Native capability did not store liquid");var copied=s.copy();h.assertTrue(LabPlatform.capability(copied).drain(1,EXECUTE).getAmount()==1&&LabFluids.read(s,h.getLevel()).getAmount()==volume,"Copied item shared mutable contents");}h.succeed();
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="lab_vessels")
    public static void offhandDispensingAndAirDoseSelectionUseTheHeldVessels(GameTestHelper h){
        var p=h.makeMockSurvivalPlayer();var dropper=vessel("dropper_bottle");var tube=vessel("test_tube");p.setItemInHand(InteractionHand.MAIN_HAND,dropper);p.setItemInHand(InteractionHand.OFF_HAND,tube);LabFluids.write(dropper,new FluidStack(Fluids.WATER,10),h.getLevel());var item=(LabVesselItem)dropper.getItem();
        item.use(h.getLevel(),p,InteractionHand.MAIN_HAND);h.assertTrue(LabFluids.read(dropper,h.getLevel()).getAmount()==9&&LabFluids.read(tube,h.getLevel()).getAmount()==1,"Default dropper did not dispense exactly 1 mB");p.setShiftKeyDown(true);item.use(h.getLevel(),p,InteractionHand.MAIN_HAND);h.assertTrue(LabVesselItem.dose(dropper)==5&&LabFluids.read(dropper,h.getLevel()).getAmount()==9,"Air dose selection transferred fluid");p.setShiftKeyDown(false);item.use(h.getLevel(),p,InteractionHand.MAIN_HAND);h.assertTrue(LabFluids.read(dropper,h.getLevel()).getAmount()==4&&LabFluids.read(tube,h.getLevel()).getAmount()==6,"Selected dropper dose not used");h.succeed();
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="lab_vessels")
    public static void fiveCapacitiesAndDoseSettingsAreIndependent(GameTestHelper h){
        var names=List.of("reagent_bottle","dropper_bottle","pipette","test_tube","beaker");int[] caps={250,100,25,50,1000};
        for(int i=0;i<5;i++){var s=vessel(names.get(i));h.assertTrue(s.getMaxStackSize()==1&&LabVesselItem.capacity(s)==caps[i],"Wrong vessel capacity");int before=LabVesselItem.dose(s);var copy=s.copy();LabVesselItem.cycle(s);h.assertTrue(LabVesselItem.dose(s)!=before&&LabVesselItem.dose(copy)==before,"Dose mutated a different physical item");}
        var corrupt=vessel("pipette");corrupt.setCount(2);h.assertTrue(LabVesselItem.capacity(corrupt)==0,"Illegal stacked vessels accepted liquid");h.succeed();
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="lab_vessels")
    public static void doseTransfersConserveLiquidAndRejectPartialAndMixedSamples(GameTestHelper h){
        var l=h.getLevel();var beaker=vessel("beaker");var pipette=vessel("pipette");var bottle=vessel("dropper_bottle");LabFluids.write(beaker,new FluidStack(Fluids.WATER,1000),l);
        h.assertTrue(LabFluids.interact(pipette,LabFluids.view(beaker,l,()->{}),false,l)==25,"Empty pipette did not take full sample");
        var target=LabFluids.view(bottle,l,()->{});for(int i=0;i<5;i++)h.assertTrue(LabFluids.interact(pipette,target,false,l)==5,"Pipette dose changed");
        h.assertTrue(LabFluids.read(beaker,l).getAmount()==975&&LabFluids.read(bottle,l).getAmount()==25&&LabFluids.read(pipette,l).isEmpty(),"Liquid was lost or copied");
        LabFluids.write(pipette,new FluidStack(Fluids.WATER,4),l);h.assertTrue(LabFluids.interact(pipette,target,false,l)==0&&LabFluids.read(pipette,l).getAmount()==4,"Insufficient source consumed partial dose");
        LabFluids.write(pipette,new FluidStack(Fluids.LAVA,25),l);h.assertTrue(LabFluids.interact(pipette,target,false,l)==0&&LabFluids.read(bottle,l).getAmount()==25,"Incompatible samples mixed");
        LabFluids.write(pipette,new FluidStack(Fluids.WATER,25),l);LabFluids.write(bottle,new FluidStack(Fluids.WATER,98),l);h.assertTrue(LabFluids.interact(pipette,target,false,l)==0&&LabFluids.read(bottle,l).getAmount()==98&&LabFluids.read(pipette,l).getAmount()==25,"Full destination accepted partial dose");h.succeed();
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="lab_vessels")
    public static void sneakSamplingIsExactlyOneDoseAndDoesNotSpillAtCapacity(GameTestHelper h){
        var l=h.getLevel();var source=vessel("beaker");var pipette=vessel("pipette");LabFluids.write(source,new FluidStack(Fluids.WATER,1000),l);var handler=LabFluids.view(source,l,()->{});
        for(int i=0;i<5;i++)h.assertTrue(LabFluids.interact(pipette,handler,true,l)==5,"Sneak sample dose wrong");h.assertTrue(LabFluids.interact(pipette,handler,true,l)==0&&LabFluids.read(source,l).getAmount()==975,"Full pipette drained source");h.succeed();
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="lab_components")
    public static void fluidComponentsAndItemMetadataSurviveTransferAndBenchSave(GameTestHelper h){
        try(var f=new IndustryGameTests.Fixture(h)){var b=bench(f);var sample=new FluidStack(Fluids.WATER,25);var marker=new CompoundTag();marker.putString("ChemicalIdentity","test_batch_42");sample.setTag(marker);var s=vessel("pipette");LabFluids.write(s,sample,f.level);var data=LabPlatform.data(s);data.putString("OtherModMetadata","preserved");LabPlatform.data(s,data);LabVesselItem.cycle(s);b.setItem(0,s);b.setItem(1,vessel("test_tube"));
            var copy=save(b);load(b,copy);h.assertTrue(LabPlatform.data(b.getItem(0)).getString("OtherModMetadata").equals("preserved")&&LabVesselItem.dose(b.getItem(0))==10,"Item metadata or dose lost");
            h.assertTrue(LabFluids.transfer(LabFluids.view(b.getItem(0),f.level,b::setChanged),LabFluids.view(b.getItem(1),f.level,b::setChanged),10)==10,"Component-bearing sample transfer failed");var actual=LabFluids.read(b.getItem(1),f.level);h.assertTrue(actual.isFluidEqual(sample)&&actual.getAmount()==10,"Chemical components lost");
            var plain=LabFluids.view(vessel("test_tube"),f.level,()->{});plain.fill(new FluidStack(Fluids.WATER,1),EXECUTE);h.assertTrue(plain.fill(actual,SIMULATE)==0,"Same base fluid with different components merged");h.succeed();}
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="lab_ports")
    public static void nativeTankAndSelectedBenchVesselUseRealCapabilities(GameTestHelper h){
        try(var f=new IndustryGameTests.Fixture(h)){var b=bench(f);f.put(f.origin.east(2),"tank");var tank=LabPlatform.handler(f.level,f.origin.east(2),Direction.UP);h.assertTrue(tank!=null&&tank.fill(new FluidStack(Fluids.WATER,1000),EXECUTE)==1000,"Tank fixture failed");var p=vessel("pipette");h.assertTrue(LabFluids.interact(p,tank,false,f.level)==25&&tank.getFluidInTank(0).getAmount()==975,"Real tank sampling failed");b.setItem(0,vessel("dropper_bottle"));var view=LabPlatform.handler(f.level,f.origin,Direction.UP);h.assertTrue(view!=null&&LabFluids.interact(p,view,false,f.level)==5&&view.getFluidInTank(0).getAmount()==5,"Real bench fluid capability failed");var old=b.removeItemNoUpdate(0);h.assertTrue(view.fill(new FluidStack(Fluids.WATER,2),EXECUTE)==0&&LabFluids.read(old,f.level).getAmount()==5,"Stale capability mutated removed vessel");h.succeed();}
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="lab_ports")
    public static void stringOnlyMachineTanksRejectComponentBearingChemicals(GameTestHelper h){
        try(var f=new IndustryGameTests.Fixture(h)){f.put(f.origin,"tank");var view=LabPlatform.handler(f.level,f.origin,Direction.UP);var chemical=new FluidStack(Fluids.WATER,25);var tag=new CompoundTag();tag.putString("ChemicalIdentity","tagged");chemical.setTag(tag);h.assertTrue(!view.isFluidValid(0,chemical)&&view.fill(chemical,EXECUTE)==0,"Machine silently stripped fluid components");h.assertTrue(view.fill(new FluidStack(Fluids.WATER,25),EXECUTE)==25,"Plain water compatibility regressed");h.succeed();}
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="lab_bench")
    public static void menuTransferValidatesDoseAliasesDistanceAndButtonIds(GameTestHelper h){
        try(var f=new IndustryGameTests.Fixture(h)){var b=bench(f);var p=h.makeMockSurvivalPlayer();p.setPos(Vec3.atCenterOf(f.origin));b.setItem(0,vessel("beaker"));b.setItem(1,vessel("test_tube"));LabFluids.write(b.getItem(0),new FluidStack(Fluids.WATER,1000),f.level);var menu=new LabBenchMenu(1,p.getInventory(),b,b.data);
            h.assertTrue(menu.clickMenuButton(p,13)&&b.data.get(3)==25&&LabFluids.read(b.getItem(1),f.level).getAmount()==25,"Menu did not transfer exact dose");menu.clickMenuButton(p,6);h.assertTrue(menu.clickMenuButton(p,13)&&b.data.get(3)==-1&&LabFluids.read(b.getItem(0),f.level).getAmount()==975,"Self-transfer duplicated contents");h.assertTrue(!menu.clickMenuButton(p,14)&&!menu.clickMenuButton(p,-1),"Invalid button accepted");p.setPos(Vec3.atCenterOf(f.origin.east(20)));h.assertTrue(!menu.clickMenuButton(p,13)&&!menu.stillValid(p),"Remote menu could manipulate bench");h.succeed();}
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="lab_bench")
    public static void bucketOperationsAreWholeAndRespectSmallVesselCapacity(GameTestHelper h){
        try(var f=new IndustryGameTests.Fixture(h)){var b=bench(f);for(var mode:List.of(GameType.SURVIVAL,GameType.CREATIVE)){var p=(mode==GameType.CREATIVE?h.makeMockPlayer():h.makeMockSurvivalPlayer());p.getAbilities().instabuild=mode==GameType.CREATIVE;b.setItem(0,vessel("test_tube"));p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));b.bucket(p,0);h.assertTrue(p.getMainHandItem().is(Items.WATER_BUCKET)&&LabFluids.read(b.getItem(0),f.level).isEmpty(),"Bucket lost liquid into small vessel");b.setItem(0,vessel("beaker"));b.bucket(p,0);h.assertTrue(p.getMainHandItem().is(Items.BUCKET)&&LabFluids.read(b.getItem(0),f.level).getAmount()==1000,"Bucket did not fill beaker");b.bucket(p,0);h.assertTrue(p.getMainHandItem().is(Items.WATER_BUCKET)&&LabFluids.read(b.getItem(0),f.level).isEmpty(),"Bucket extraction lost contents");}h.succeed();}
    }
    @GameTest(template="empty_test",timeoutTicks=120,batch="lab_bench")
    public static void allSixHitPositionsMatchRenderedSlotsInEveryFacing(GameTestHelper h){
        try(var f=new IndustryGameTests.Fixture(h)){for(var facing:Direction.Plane.HORIZONTAL){f.put(f.origin,IndustryContent.block("lab_bench").defaultBlockState().setValue(LabBenchBlock.FACING,facing));var b=(LabBenchEntity)f.level.getBlockEntity(f.origin);for(int i=0;i<6;i++)h.assertTrue(b.hitSlot(b.worldCenter(i))==i,"Bench facing hit mismatch "+facing+" slot "+i);}h.succeed();}
    }
    @GameTest(template="empty_test",timeoutTicks=1200,batch="lab_break")
    public static void breakingBenchDropsEachFilledVesselOnce(GameTestHelper h){
        var f=new IndustryGameTests.Fixture(h);var b=bench(f);for(int i=0;i<6;i++){var s=vessel("pipette");LabFluids.write(s,new FluidStack(Fluids.WATER,i+1),f.level);b.setItem(i,s);}
        f.whenReady(()->{f.level.destroyBlock(f.origin,true);f.after(5,()->{var drops=f.level.getEntitiesOfClass(ItemEntity.class,new AABB(f.origin).inflate(3));int count=0,liquid=0,benches=0;for(var e:drops){var s=e.getItem();if(LabVesselItem.vessel(s)){count++;liquid+=LabFluids.read(s,f.level).getAmount();}if(s.is(IndustryContent.item("lab_bench")))benches+=s.getCount();}h.assertTrue(count==6&&liquid==21&&benches==1,"Break duplicated/lost drops "+count+" / "+liquid+" / "+benches);f.success();});});
    }
}
