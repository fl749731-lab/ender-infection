package dev.hui.resonantindustry;

import com.mojang.authlib.GameProfile;
import dev.hui.resonantindustry.api.IndustryApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in dedicated server check. Each stage runs in a fresh process. */
@EventBusSubscriber(modid = IndustryApi.MOD_ID)
public final class WorkshopPersistenceCheck {
    private static final BlockPos PUMP = new BlockPos(1000200, 64, 1000200);
    private static final ChunkPos CHUNK = new ChunkPos(PUMP);
    private static final Path PROGRESS = Path.of("workshop-seed-progress.txt");
    private static int step, ticks, seedProgress;
    private static boolean done;

    private WorkshopPersistenceCheck() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        String stage = System.getProperty("resonant_industry.workshopPersistence", "");
        if (stage.isEmpty() || done) return;
        var server = event.getServer();
        try {
            advance(server, stage);
        } catch (Exception | AssertionError error) {
            done = true;
            System.err.println("WORKSHOP_PERSISTENCE_FAIL " + stage + ": " + error);
            error.printStackTrace();
            server.halt(false);
        }
    }

    private static void advance(MinecraftServer server, String stage) throws Exception {
        require(++ticks < 1200, "Workshop timeout at stage " + step);
        var level = server.overworld();
        var rig = new WorkshopGameTests.MixRig(level, PUMP);
        if (step == 0) {
            level.setChunkForced(CHUNK.x, CHUNK.z, true);
            level.getChunk(CHUNK.x, CHUNK.z);
            step = 1;
        }
        if (!level.areEntitiesLoaded(CHUNK.toLong())) return;
        if (step == 1) {
            switch (stage) {
                case "seed" -> {
                    var player = new Player(level, PUMP, 0, new GameProfile(UUID.randomUUID(), "WorkshopCheck")) {
                        @Override public boolean isSpectator() { return false; }
                        @Override public boolean isCreative() { return false; }
                    };
                    require(level.getBlockEntity(PUMP) == null, "Existing workshop world used as seed");
                    rig.place("metering_pump", player, 50, 50);
                    level.setBlock(PUMP.above(), IndustryContent.block("cable").defaultBlockState(), 3);
                    var battery = PUMP.above().south();
                    level.setBlock(battery, IndustryContent.block("battery").defaultBlockState(), 3);
                    require(IndustryNode.at(level, battery).energyAccess(null).receive(512, false) == 512, "Battery did not accept FE");
                    require(IndustryNode.at(level, battery).energyAccess(null).receive(512, false) == 512, "Battery did not accept second charge");
                    rig.signal(true);
                    step = 2;
                }
                case "resume" -> {
                    seedProgress = Integer.parseInt(Files.readString(PROGRESS));
                    require(rig.progress() == seedProgress && rig.bench().chemistry().metered()
                            && rig.acid().amount() == 25 && rig.alkali().amount() == 25 && rig.output().amount() == 0,
                            "Restart changed the charged batch, saved progress, or tank inventory");
                    rig.signal(true);
                    step = 4;
                }
                case "finished" -> {
                    requireFinished(rig);
                    if (ticks > 40) stop(server, stage);
                }
                default -> throw new IllegalArgumentException("Unknown workshop stage " + stage);
            }
        } else if (step == 2 && rig.progress() >= 20) {
            rig.signal(false);
            seedProgress = rig.progress();
            step = 3;
        } else if (step == 3) {
            require(rig.progress() == seedProgress && rig.output().amount() == 0, "Stopped pump changed the active batch");
            Files.writeString(PROGRESS, Integer.toString(seedProgress));
            stop(server, stage);
        } else if (step == 4 && rig.output().amount() >= 100) {
            requireFinished(rig);
            stop(server, stage);
        }
    }

    private static void requireFinished(WorkshopGameTests.MixRig rig) {
        require(rig.output().amount() == 100 && rig.output().fluid().equals("resonant_industry:neutral_solution")
                        && rig.acid().amount() == 0 && rig.alkali().amount() == 0
                        && !rig.bench().chemistry().metered() && LabFluids.read(rig.bench().getItem(0), rig.level()).isEmpty(),
                "Workshop lost or duplicated part of its two batches");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void stop(MinecraftServer server, String stage) {
        done = true;
        server.saveEverything(false, true, true);
        System.out.println("WORKSHOP_PERSISTENCE_PASS " + stage);
        server.halt(false);
    }
}
