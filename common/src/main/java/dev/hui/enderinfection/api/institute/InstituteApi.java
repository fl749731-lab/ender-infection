package dev.hui.enderinfection.api.institute;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Rotation;

/** Stable v1 extension entry point. Register once during your mod constructor, on either loader. */
public final class InstituteApi {
    public static final int API_VERSION = 1;
    private static final InstituteRegistry REGISTRY = new InstituteRegistry();
    private static RoomPlacementService placement;
    private static InstituteOperationsService operations;
    private static final CopyOnWriteArrayList<Consumer<FacilityEvent>> FACILITY_LISTENERS = new CopyOnWriteArrayList<>();
    private InstituteApi() {}
    public static void registerAddon(String namespace, Consumer<InstituteRegistrar> registration) { REGISTRY.registerAddon(namespace, registration); }
    public static void freeze() { REGISTRY.freeze(); }
    public static boolean isFrozen() { return REGISTRY.isFrozen(); }
    public static Map<String, RoomDefinition> rooms() { return REGISTRY.rooms(); }
    public static Map<String, DeviceDefinition> devices() { return REGISTRY.devices(); }
    public static Map<String, ProcessingRecipe> processingRecipes() { return REGISTRY.processingRecipes(); }
    /** Loader lifecycle hook, installed once by the core mod. */
    public static synchronized void installOperationsService(InstituteOperationsService service) {
        if (operations != null) throw new IllegalStateException("Institute operations service already installed");
        operations = Objects.requireNonNull(service);
    }
    public static Optional<FacilityStatus> facility(ServerLevel level, BlockPos pos) {
        requireServerThread(level);
        return operations == null ? Optional.empty() : operations.status(level, pos);
    }
    public static boolean isPowered(ServerLevel level, BlockPos pos) {
        requireServerThread(level);
        return operations != null && operations.powered(level, pos);
    }
    public static void recordProcess(ServerLevel level, BlockPos pos, String recipeId) {
        requireServerThread(level); InstituteRegistry.checkId(recipeId);
        if (operations != null) operations.completeProcess(level, pos, recipeId);
    }
    /** Subscribe without replacing the core service. Closing removes the listener. */
    public static AutoCloseable onFacilityChange(Consumer<FacilityEvent> listener) {
        FACILITY_LISTENERS.add(Objects.requireNonNull(listener));
        return () -> FACILITY_LISTENERS.remove(listener);
    }
    public static void fireFacilityEvent(FacilityEvent event) {
        Objects.requireNonNull(event); requireServerThread(event.level());
        for (var listener : FACILITY_LISTENERS) {
            try { listener.accept(event); }
            catch (RuntimeException failure) {
                com.mojang.logging.LogUtils.getLogger().error("Institute facility listener failed", failure);
            }
        }
    }
    private static void requireServerThread(ServerLevel level) {
        Objects.requireNonNull(level);
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Institute operations require the server thread");
    }
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
