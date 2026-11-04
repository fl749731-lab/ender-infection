package dev.hui.enderinfection.api.institute;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Conservative NBT placement: only loaded, empty/replaceable space, no entities or forced chunk loads. */
public final class TemplateRoomPlacementService implements RoomPlacementService {
    private record Prepared(StructureTemplate template, StructurePlaceSettings settings, PlacementResult preview) {}

    private Prepared prepare(ServerLevel level, String id, BlockPos origin, Rotation rotation) {
        if (!level.getServer().isSameThread()) return failed("server_thread_required");
        var room = InstituteApi.rooms().get(id);
        if (room == null) return failed("unknown_room: " + id);
        var template = level.getStructureManager().get(ResourceLocation.tryParse(room.templateId())).orElse(null);
        if (template == null) return failed("missing_template: " + room.templateId());
        var size = template.getSize();
        if (size.getX() != room.size().x() || size.getY() != room.size().y() || size.getZ() != room.size().z())
            return failed("template_size_mismatch: " + room.templateId());
        var settings = new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE)
                .setRotationPivot(BlockPos.ZERO).setIgnoreEntities(true);
        var box = template.getBoundingBox(settings, origin);
        if (box.minY() < level.getMinBuildHeight() || box.maxY() >= level.getMaxBuildHeight()) return failed("outside_build_height");
        if (!level.getWorldBorder().isWithinBounds(new BlockPos(box.minX(), box.minY(), box.minZ()))
                || !level.getWorldBorder().isWithinBounds(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) return failed("outside_world_border");
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++)
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return failed("unloaded_chunk");
        for (var pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            var state = level.getBlockState(pos);
            if (level.getBlockEntity(pos) != null || !state.getFluidState().isEmpty() || (!state.isAir() && !state.canBeReplaced()))
                return failed("occupied_site: " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }
        var connectors = room.connectors().stream().map(c -> new WorldConnector(c.name(),
                StructureTemplate.transform(new BlockPos(c.x(), c.y(), c.z()), Mirror.NONE, rotation, BlockPos.ZERO).offset(origin),
                rotation.rotate(c.facing()), c.socket())).toList();
        return new Prepared(template, settings, new PlacementResult(true, "ready", box, connectors));
    }
    private Prepared failed(String reason) { return new Prepared(null, null, PlacementResult.failure(reason)); }
    @Override public PlacementResult preview(ServerLevel level, String id, BlockPos origin, Rotation rotation) {
        return prepare(level, id, origin, rotation).preview();
    }
    @Override public PlacementResult place(ServerLevel level, String id, BlockPos origin, Rotation rotation) {
        var plan = prepare(level, id, origin, rotation);
        if (!plan.preview().success()) return plan.preview();
        boolean placed = plan.template().placeInWorld(level, origin, origin, plan.settings(), level.random, 3);
        return placed ? new PlacementResult(true, "placed", plan.preview().bounds(), plan.preview().connectors())
                : PlacementResult.failure("template_placement_failed");
    }
}
