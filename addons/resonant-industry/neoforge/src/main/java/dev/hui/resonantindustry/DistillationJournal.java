package dev.hui.resonantindustry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/** Replays both chunk snapshots before Minecraft can load either half of an interrupted batch. */
@EventBusSubscriber(modid = "resonant_industry")
public final class DistillationJournal {
    private DistillationJournal() {}

    static void settle(LabBenchEntity heater, LabBenchEntity receiver, Runnable settle) {
        var sourcePos = new ChunkPos(heater.getBlockPos());
        var receiverPos = new ChunkPos(receiver.getBlockPos());
        if (sourcePos.equals(receiverPos)) {
            settle.run();
            return;
        }

        var level = (ServerLevel) heater.getLevel();
        var chunks = level.getChunkSource();
        var source = chunks.getChunkNow(sourcePos.x, sourcePos.z);
        var target = chunks.getChunkNow(receiverPos.x, receiverPos.z);
        if (source == null || target == null) {
            throw new IllegalStateException("Distillation settlement requires both chunks to be loaded");
        }

        var registries = level.registryAccess();
        var oldSource = heater.saveWithoutMetadata(registries);
        var oldReceiver = receiver.saveWithoutMetadata(registries);
        var server = level.getServer();
        var journal = path(server);
        boolean prepared = false;
        try {
            settle.run();
            var batch = new Batch(level.dimension(), snapshot(level, source), snapshot(level, target));
            batch.prepare(journal);
            prepared = true;
            batch.writeTo(chunks.chunkMap);
            Files.delete(journal);
        } catch (IOException | RuntimeException error) {
            if (!prepared) {
                heater.loadWithComponents(oldSource, registries);
                receiver.loadWithComponents(oldReceiver, registries);
            }
            // Stop before players or automation can change a chunk scheduled for replay.
            server.halt(false);
            throw new IllegalStateException("Could not checkpoint cross-chunk distillation", error);
        }
    }

    private static CompoundTag snapshot(ServerLevel level, LevelChunk chunk) {
        var tag = ChunkSerializer.write(level, chunk);
        NeoForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, level, tag));
        return tag;
    }

    private static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/resonant_industry_distillation.nbt");
    }

    @SubscribeEvent
    public static void recover(ServerAboutToStartEvent event) {
        var server = event.getServer();
        var journal = path(server);
        try {
            recover(server, server.getWorldPath(LevelResource.ROOT), journal);
        } catch (IOException error) {
            throw new UncheckedIOException("Could not replay cross-chunk distillation", error);
        }
    }

    static void recover(MinecraftServer server, Path world, Path journal) throws IOException {
        if (Files.notExists(journal)) return;
        var batch = Batch.read(journal);
        var region = DimensionType.getStorageFolder(batch.dimension(), world).resolve("region");
        var info = new RegionStorageInfo(world.getFileName().toString(), batch.dimension(), "chunk");
        try (var storage = new ChunkStorage(info, region, server.getFixerUpper(), true)) {
            batch.writeTo(storage);
        }
        Files.delete(journal);
    }

    record Batch(ResourceKey<Level> dimension, CompoundTag heater, CompoundTag receiver) {
        void prepare(Path journal) throws IOException {
            if (!Files.notExists(journal)) throw new IOException("A distillation journal is already pending");
            Files.createDirectories(journal.getParent());
            var tag = new CompoundTag();
            tag.putString("Dimension", dimension.location().toString());
            tag.put("Heater", heater);
            tag.put("Receiver", receiver);
            var temp = journal.resolveSibling(journal.getFileName() + ".tmp");
            // NbtIo writes with SYNC. Rename only after the complete record is on disk.
            NbtIo.writeCompressed(tag, temp);
            Files.move(temp, journal, StandardCopyOption.ATOMIC_MOVE);
        }

        static Batch read(Path journal) throws IOException {
            var tag = NbtIo.readCompressed(journal, NbtAccounter.unlimitedHeap());
            var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            var heater = tag.getCompound("Heater");
            var receiver = tag.getCompound("Receiver");
            if (dimension == null || !heater.contains("DataVersion") || !receiver.contains("DataVersion")
                    || position(heater).getChessboardDistance(position(receiver)) != 1) {
                throw new IOException("Invalid distillation journal: " + journal);
            }
            return new Batch(ResourceKey.create(Registries.DIMENSION, dimension), heater, receiver);
        }

        void writeTo(ChunkStorage storage) {
            storage.write(position(heater), heater).join();
            storage.write(position(receiver), receiver).join();
            storage.flushWorker();
        }
    }

    private static ChunkPos position(CompoundTag chunk) {
        return new ChunkPos(chunk.getInt("xPos"), chunk.getInt("zPos"));
    }
}
