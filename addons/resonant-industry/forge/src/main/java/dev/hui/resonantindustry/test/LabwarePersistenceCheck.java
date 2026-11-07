package dev.hui.resonantindustry.test;

import dev.hui.resonantindustry.*;
import dev.hui.resonantindustry.api.IndustryApi;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in only: three real server processes and a genuine chunk unload/reload. */
@Mod.EventBusSubscriber(modid=IndustryApi.MOD_ID)
public final class LabwarePersistenceCheck {
    private static final BlockPos POS=new BlockPos(1000000,64,1000000);
    private static final BlockPos HEATER = POS.east(2);
    private static final BlockPos STILL = POS.east(16).south(3);
    private static final BlockPos CONDENSER = STILL.west();
    private static final ChunkPos CHUNK=new ChunkPos(POS);
    private static final ChunkPos STILL_CHUNK = new ChunkPos(STILL);
    private static int ticks,step,paused;private static boolean done;private static LabBenchEntity old;
    private static LabBenchEntity oldHeater, oldStill, oldReceiver;
    private static CompoundTag pausedExperiment, pausedDistillation, pausedReceiver;
    private static void require(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        String stage=System.getProperty("resonant_industry.labwarePersistence","");if(stage.isEmpty()||done||event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();try{advance(server,stage);}catch(Exception|AssertionError error){error.printStackTrace();System.err.println("LABWARE_PERSISTENCE_FAIL "+stage+" "+error);done=true;server.halt(false);}
    }
    private static LabBenchEntity bench(MinecraftServer server){return (LabBenchEntity)server.overworld().getBlockEntity(POS);}
    private static LabBenchEntity heater(MinecraftServer server) { return (LabBenchEntity) server.overworld().getBlockEntity(HEATER); }
    private static LabBenchEntity still(MinecraftServer server) { return (LabBenchEntity) server.overworld().getBlockEntity(STILL); }
    private static LabBenchEntity receiver(MinecraftServer server) { return (LabBenchEntity) server.overworld().getBlockEntity(CONDENSER); }
    private static CompoundTag save(LabBenchEntity n) { return n.saveWithoutMetadata(); }

    private static void force(ServerLevel level, boolean forced) {
        level.setChunkForced(CHUNK.x, CHUNK.z, forced);
        level.setChunkForced(STILL_CHUNK.x, STILL_CHUNK.z, forced);
    }

    private static boolean ready(ServerLevel level) {
        return level.areEntitiesLoaded(CHUNK.toLong()) && level.areEntitiesLoaded(STILL_CHUNK.toLong());
    }

    private static boolean loaded(ServerLevel level, ChunkPos chunk) {
        return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null || level.areEntitiesLoaded(chunk.toLong());
    }

    private static Player testPlayer(LabBenchEntity station) {
        var position = station.getBlockPos();
        var player = new Player(station.getLevel(), position, 0, new GameProfile(UUID.randomUUID(), "LabPersistence")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        player.setPos(Vec3.atCenterOf(position));
        return player;
    }

    private static void seedExperiment(MinecraftServer server, BlockPos position, int action) {
        var level = server.overworld();
        level.setBlock(position, IndustryContent.block("hot_plate").defaultBlockState(), 3);
        var station = (LabBenchEntity) level.getBlockEntity(position);
        var tube = new ItemStack(IndustryContent.item("test_tube"));
        var neutral = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(IndustryContent.id("neutral_solution"));
        LabFluids.write(tube, new FluidStack(neutral, 50), level);
        station.setItem(0, tube);
        var player = testPlayer(station);
        var menu = (LabBenchMenu) station.createMenu(1, player.getInventory(), player);
        menu.getSlot(43).set(new ItemStack(Items.COAL));
        require(menu.clickMenuButton(player, action), "Heater start failed");
    }

    private static void verifyActive(LabBenchEntity station, Chemistry.Experiment experiment, int minimumProgress) {
        require(station != null && station.chemistry() != null, "Saved heater missing");
        var state = save(station).getCompound("Chemistry");
        require(state.getBoolean("Running") && state.getString("Experiment").equals(experiment.id())
                && state.getInt("Progress") >= minimumProgress && state.getInt("Progress") < experiment.duration
                && state.getInt("Temperature") >= 100 && state.getInt("FuelTicks") > 0
                && LabFluids.read(station.getItem(0), station.getLevel()).getAmount() == 50,
                "Active heater state or charged sample was lost: " + state);
    }

    private static void verifyDistillate(MinecraftServer server) {
        var station = still(server);
        var player = testPlayer(station);
        var menu = (LabBenchMenu) station.createMenu(1, player.getInventory(), player);
        require(menu.chemistry(1) == ChemistryStation.Status.COMPLETE.ordinal() && menu.chemistry(8) == 0
                && menu.chemistry(9) == 40 && menu.getSlot(44).getItem().isEmpty(), "Wrong recovered batch after restart");
        require(ChemistryGameTests.isFluid(station.getItem(0), "neutral_concentrate", 10)
                && ChemistryGameTests.isFluid(receiver(server).getItem(0), "distilled_water", 40),
                "Cross-chunk distillation lost or duplicated liquid on reload");
    }

    private static void verifyCrystals(LabBenchEntity station) {
        var player = testPlayer(station);
        var menu = (LabBenchMenu) station.createMenu(1, player.getInventory(), player);
        require(menu.chemistry(1) == ChemistryStation.Status.COMPLETE.ordinal() && menu.chemistry(8) == 50,
                "Crystallization result not persisted");
        var product = menu.getSlot(44).getItem();
        require(product.is(IndustryContent.item("neutral_salt")) && product.getCount() == 1
                && menu.getSlot(43).getItem().isEmpty() && LabFluids.read(station.getItem(0), station.getLevel()).isEmpty(),
                "Reloaded heater duplicated crystals or fuel, or lost the vessel");
    }
    private static void advance(MinecraftServer server,String stage)throws Exception{
        require(++ticks<1800,"Persistence timeout "+step);var l=server.overworld();
        if (step == 0) {
            force(l, true);
            l.getChunk(CHUNK.x, CHUNK.z);
            l.getChunk(STILL_CHUNK.x, STILL_CHUNK.z);
            step = 1;
        }
        if(step==1){if(!ready(l))return;
            if(stage.equals("seed")){
                require(l.getBlockEntity(POS)==null,"Seed directory already used");l.setBlock(POS,IndustryContent.block("lab_bench").defaultBlockState(),3);var b=bench(server);
                for(int i=0;i<6;i++){var item=new ItemStack(IndustryContent.item(i==0?"beaker":i==1?"pipette":i==2?"dropper_bottle":i==3?"test_tube":"reagent_bottle"));b.setItem(i,item);}
                LabFluids.write(b.getItem(0),new FluidStack(Fluids.WATER,975),l);LabFluids.write(b.getItem(1),new FluidStack(Fluids.WATER,20),l);LabFluids.write(b.getItem(2),new FluidStack(Fluids.WATER,5),l);LabVesselItem.cycle(b.getItem(1));
                b.data.set(0,1);b.data.set(1,2);b.data.set(2,1);b.setChanged();
                seedExperiment(server, HEATER, 14);
                l.setBlock(CONDENSER, IndustryContent.block("condenser").defaultBlockState(), 3);
                receiver(server).setItem(0, new ItemStack(IndustryContent.item("test_tube")));
                seedExperiment(server, STILL, 16);
                step = 5;
                return;
            }
            require(stage.equals("resume")||stage.equals("finished"),"Unknown persistence stage");verify(bench(server),stage.equals("finished"));
            if (stage.equals("finished")) {
                verifyCrystals(heater(server));
                verifyDistillate(server);
                stop(server, stage);
                return;
            }
            old=bench(server);
            oldHeater = heater(server);
            oldStill = still(server);
            oldReceiver = receiver(server);
            verifyActive(oldHeater, Chemistry.Experiment.CRYSTALLIZATION, 20);
            verifyActive(oldStill, Chemistry.Experiment.NEUTRAL_DISTILLATION, 20);
            force(l, false);
            step = 2;
            return;
        }
        if (step == 2) {
            if (loaded(l, CHUNK) || loaded(l, STILL_CHUNK)) return;
            paused = ticks;
            pausedExperiment = save(oldHeater).getCompound("Chemistry");
            pausedDistillation = save(oldStill).getCompound("Chemistry");
            pausedReceiver = save(oldReceiver);
            System.out.println("LABWARE_CHUNKS_UNLOADED_PASS");
            step = 3;
            return;
        }
        if (step == 3) {
            verify(old, false);
            require(pausedExperiment.equals(save(oldHeater).getCompound("Chemistry")), "Unloaded heater advanced");
            require(pausedDistillation.equals(save(oldStill).getCompound("Chemistry")) && pausedReceiver.equals(save(oldReceiver)),
                    "Unloaded condenser or still changed");
            if (ticks - paused < 40) return;
            force(l, true);
            step = 4;
            return;
        }
        if (step == 4) {
            if (!ready(l)) return;
            var b = bench(server);
            require(b != old && heater(server) != oldHeater && still(server) != oldStill && receiver(server) != oldReceiver,
                    "Chunk entity did not reload");
            verify(b, false);
            verifyActive(heater(server), Chemistry.Experiment.CRYSTALLIZATION, pausedExperiment.getInt("Progress"));
            verifyActive(still(server), Chemistry.Experiment.NEUTRAL_DISTILLATION, pausedDistillation.getInt("Progress"));
            require(LabFluids.transfer(LabFluids.view(b.getItem(1), l, b::setChanged),
                    LabFluids.view(b.getItem(2), l, b::setChanged), b.dose()) == 5, "Saved dose transfer failed");
            verify(b, true);
            System.out.println("LABWARE_CHUNKS_RELOADED_PASS");
            step = 6;
            return;
        }
        if (step == 5 && save(heater(server)).getCompound("Chemistry").getInt("Progress") >= 20
                && save(still(server)).getCompound("Chemistry").getInt("Progress") >= 20) {
            verifyActive(heater(server), Chemistry.Experiment.CRYSTALLIZATION, 20);
            verifyActive(still(server), Chemistry.Experiment.NEUTRAL_DISTILLATION, 20);
            Files.writeString(Path.of("labware-persistence.txt"), "seed\n");
            stop(server, stage);
        }
        if (step == 6 && save(heater(server)).getCompound("Chemistry").getString("Status").equals("COMPLETE")
                && save(still(server)).getCompound("Chemistry").getString("Status").equals("COMPLETE")) {
            verifyCrystals(heater(server));
            verifyDistillate(server);
            Files.writeString(Path.of("labware-persistence.txt"), "seed\nresume\n");
            stop(server, stage);
        }
    }
    private static void verify(LabBenchEntity b,boolean after){
        require(b!=null&&b.getContainerSize()==6,"Bench disappeared");for(int i=0;i<6;i++)require(LabVesselItem.vessel(b.getItem(i)),"Vessel missing "+i);
        require(LabFluids.read(b.getItem(0),b.getLevel()).getAmount()==975&&LabFluids.read(b.getItem(1),b.getLevel()).getAmount()==(after?15:20)&&LabFluids.read(b.getItem(2),b.getLevel()).getAmount()==(after?10:5),"Liquid changed across save");
        require(b.data.get(0)==1&&b.data.get(1)==2&&b.dose()==5&&LabVesselItem.dose(b.getItem(1))==10,"Selection/dose not preserved");
    }
    private static void stop(MinecraftServer server,String stage){done=true;server.saveEverything(false,true,true);System.out.println("LABWARE_PERSISTENCE_PASS "+stage);System.out.println("CHEMISTRY_PERSISTENCE_PASS " + stage);System.out.println("SEPARATION_PERSISTENCE_PASS " + stage);server.halt(false);}
}
