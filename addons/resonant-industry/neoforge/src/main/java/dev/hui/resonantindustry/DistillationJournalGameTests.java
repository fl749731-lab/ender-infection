package dev.hui.resonantindustry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("resonant_industry")
@PrefixGameTestTemplate(false)
public final class DistillationJournalGameTests {
    private static final ChunkPos HEATER = new ChunkPos(32, 0);
    private static final ChunkPos RECEIVER = new ChunkPos(31, 0);

    @GameTest(template = "empty_test", batch = "distillation_journal")
    public static void replaysEitherPartialRegionWrite(GameTestHelper helper) throws IOException {
        var server = helper.getLevel().getServer();
        var before = new DistillationJournal.Batch(Level.OVERWORLD, snapshot(HEATER, 50), snapshot(RECEIVER, 0));
        var after = new DistillationJournal.Batch(Level.OVERWORLD, snapshot(HEATER, 10), snapshot(RECEIVER, 40));

        for (var written : new ChunkPos[]{HEATER, RECEIVER}) {
            var world = Files.createTempDirectory(server.getWorldPath(LevelResource.ROOT), "distillation-replay-");
            var journal = world.resolve("data/journal.nbt");
            try (var storage = storage(server, world)) {
                before.writeTo(storage);
                after.prepare(journal);
                storage.write(written, written.equals(HEATER) ? after.heater() : after.receiver()).join();
                storage.flushWorker();
            }

            DistillationJournal.recover(server, world, journal);
            DistillationJournal.recover(server, world, journal);
            helper.assertTrue(Files.notExists(journal), "Completed replay left a pending journal");
            try (var storage = storage(server, world)) {
                helper.assertTrue(storage.read(HEATER).join().orElseThrow().equals(after.heater())
                                && storage.read(RECEIVER).join().orElseThrow().equals(after.receiver()),
                        "Replaying a partial save lost or duplicated liquid");
            }
        }
        helper.succeed();
    }

    private static ChunkStorage storage(MinecraftServer server, Path world) {
        var info = new RegionStorageInfo(world.getFileName().toString(), Level.OVERWORLD, "chunk");
        return new ChunkStorage(info, world.resolve("region"), server.getFixerUpper(), true);
    }

    private static CompoundTag snapshot(ChunkPos pos, int volume) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", 3955);
        tag.putInt("xPos", pos.x);
        tag.putInt("zPos", pos.z);
        tag.putInt("Volume", volume);
        return tag;
    }
}
