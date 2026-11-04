package dev.hui.enderinfection.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** A serialized horizontal tile. It only touches the current engine-provided chunk clip. */
public final class CentralInstitutePiece extends TemplateStructurePiece {
    public CentralInstitutePiece(StructureTemplateManager manager, ResourceLocation template, BlockPos origin) {
        super(CentralInstituteStructure.PIECE.get(), 0, manager, template, template.toString(), CentralInstitute.settings(null), origin);
    }
    public CentralInstitutePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(CentralInstituteStructure.PIECE.get(), tag, manager, ignored -> CentralInstitute.settings(null));
    }
    public BlockPos origin() { return templatePosition; }
    @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
        // No shape propagation, liquid search, marker processing, entities, or adjacent-chunk processors.
        placeSettings.setBoundingBox(clip);
        if (!template.placeInWorld(level, templatePosition, templatePosition, placeSettings, random, CentralInstitute.UPDATE_FLAGS))
            throw new IllegalStateException("Missing or empty central institute tile " + templateName);
    }
    @Override protected void handleDataMarker(String marker, BlockPos pos, net.minecraft.world.level.ServerLevelAccessor level,
            RandomSource random, BoundingBox clip) { /* This layout has no runtime markers. */ }
}
