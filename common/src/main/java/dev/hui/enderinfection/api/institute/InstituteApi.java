package dev.hui.enderinfection.api.institute;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Rotation;

/** Stable v1 extension entry point. Register once during your mod constructor, on either loader. */
public final class InstituteApi {
    public static final int API_VERSION = 1;
    private static final InstituteRegistry REGISTRY = new InstituteRegistry();
    private static RoomPlacementService placement;
    private InstituteApi() {}
    public static void registerAddon(String namespace, Consumer<InstituteRegistrar> registration) { REGISTRY.registerAddon(namespace, registration); }
    public static void freeze() { REGISTRY.freeze(); }
    public static boolean isFrozen() { return REGISTRY.isFrozen(); }
    public static Map<String, RoomDefinition> rooms() { return REGISTRY.rooms(); }
    public static Map<String, DeviceDefinition> devices() { return REGISTRY.devices(); }
    public static InteractionResult dispatchDevice(String blockId, DeviceContext context) { return REGISTRY.dispatchDevice(blockId, context); }
    /** Loader lifecycle hook; addons must not replace the service. */
    public static synchronized void installPlacementService(RoomPlacementService service) {
        if (placement != null) throw new IllegalStateException("Institute placement service already installed");
        placement = Objects.requireNonNull(service);
    }
    private static RoomPlacementService placement() {
        if (placement == null) throw new IllegalStateException("Institute common setup has not completed");
        return placement;
    }
    public static PlacementResult preview(ServerLevel level, String id, BlockPos origin, Rotation rotation) { return placement().preview(level, id, origin, rotation); }
    public static PlacementResult place(ServerLevel level, String id, BlockPos origin, Rotation rotation) { return placement().place(level, id, origin, rotation); }
}
