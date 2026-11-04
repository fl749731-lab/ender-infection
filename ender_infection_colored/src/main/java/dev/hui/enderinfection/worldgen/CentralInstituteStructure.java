package dev.hui.enderinfection.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Rare fixed-layout institute: eligibility uses only generator height/biome data, never world chunks. */
public final class CentralInstituteStructure extends Structure {
    public static final int MAX_TERRAIN_RELIEF = 8;
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, "ender_infection");
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, "ender_infection");
    public static final RegistryObject<StructureType<CentralInstituteStructure>> TYPE = TYPES.register("central_institute", () -> () -> CentralInstituteStructure.CODEC);
    public static final RegistryObject<StructurePieceType> PIECE = PIECES.register("central_institute_tile",
            () -> (StructurePieceType.StructureTemplateType) CentralInstitutePiece::new);
    public static final Codec<CentralInstituteStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance)).apply(instance, CentralInstituteStructure::new));
    public CentralInstituteStructure(StructureSettings settings) { super(settings); }

    /** Shared with isolated natural-world validation. The returned corner is the normalized NBT origin. */
    public static Optional<BlockPos> terrainOrigin(ChunkGenerator generator, RandomState random,
            LevelHeightAccessor heights, ChunkPos startChunk) {
        int minX = startChunk.getMiddleBlockX() - CentralInstitute.WIDTH / 2;
        int minZ = startChunk.getMiddleBlockZ() - CentralInstitute.LENGTH / 2;
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int x : sampleAxis(CentralInstitute.WIDTH)) for (int z : sampleAxis(CentralInstitute.LENGTH)) {
            int floor = generator.getBaseHeight(minX + x, minZ + z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
            int surface = generator.getBaseHeight(minX + x, minZ + z, Heightmap.Types.WORLD_SURFACE_WG, heights, random);
            if (surface > floor || floor <= generator.getSeaLevel()) return Optional.empty();
            low = Math.min(low, floor); high = Math.max(high, floor);
            if (high - low > MAX_TERRAIN_RELIEF) return Optional.empty();
        }
        int bottom = high - 1 - CentralInstitute.GROUND_OFFSET;
        if (bottom < heights.getMinBuildHeight() + 1 || bottom + CentralInstitute.HEIGHT > heights.getMaxBuildHeight()) return Optional.empty();
        return Optional.of(new BlockPos(minX, bottom, minZ));
    }
    private static int[] sampleAxis(int length) {
        int count = (length - 1) / 16 + 2;
        int[] samples = new int[count];
        for (int i = 0; i < count - 1; i++) samples[i] = i * 16;
        samples[count - 1] = length - 1;
        return samples;
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var selected = terrainOrigin(context.chunkGenerator(), context.randomState(), context.heightAccessor(), context.chunkPos());
        if (selected.isEmpty()) return Optional.empty();
        BlockPos origin = selected.get();
        int surfaceY = origin.getY() + CentralInstitute.GROUND_OFFSET;
        for (int x : sampleAxis(CentralInstitute.WIDTH)) for (int z : sampleAxis(CentralInstitute.LENGTH)) {
            var biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(origin.getX() + x), QuartPos.fromBlock(surfaceY),
                    QuartPos.fromBlock(origin.getZ() + z), context.randomState().sampler());
            if (!context.validBiome().test(biome)) return Optional.empty();
        }
        // Validate every resource before returning a start, so incomplete data packs create no partial building.
        for (var tile : CentralInstitute.tiles()) {
            var template = context.structureTemplateManager().get(tile.template());
            if (template.isEmpty()) return Optional.empty();
            var size = template.get().getSize();
            if (size.getX() != tile.width() || size.getY() != tile.height() || size.getZ() != tile.length()) return Optional.empty();
        }
        return Optional.of(new GenerationStub(origin.above(CentralInstitute.GROUND_OFFSET), builder -> {
            for (var tile : CentralInstitute.tiles()) builder.addPiece(new CentralInstitutePiece(context.structureTemplateManager(),
                    tile.template(), origin.offset(tile.x(), tile.y(), tile.z())));
        }));
    }
    @Override public StructureType<?> type() { return TYPE.get(); }
}
