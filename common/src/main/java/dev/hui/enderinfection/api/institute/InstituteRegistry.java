package dev.hui.enderinfection.api.institute;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.world.InteractionResult;

/** Transactional registration and immutable discovery, shared by both loaders. */
public final class InstituteRegistry {
    private final Map<String, RoomDefinition> rooms = new LinkedHashMap<>();
    private final Map<String, DeviceDefinition> devices = new LinkedHashMap<>();
    private final Map<String, DeviceDefinition> blocks = new LinkedHashMap<>();
    private boolean frozen;

    public static void checkId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Expected a fully qualified resource ID: " + id);
    }

    public synchronized void registerAddon(String namespace, Consumer<InstituteRegistrar> registration) {
        if (frozen) throw new IllegalStateException("Institute registration is closed; register during mod construction");
        if (namespace == null || !namespace.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Invalid addon namespace");
        Objects.requireNonNull(registration, "registration");
        var newRooms = new LinkedHashMap<String, RoomDefinition>();
        var newDevices = new LinkedHashMap<String, DeviceDefinition>();
        var newBlocks = new LinkedHashMap<String, DeviceDefinition>();
        registration.accept(new InstituteRegistrar() {
            private void owns(String id) {
                if (!id.startsWith(namespace + ":")) throw new IllegalArgumentException("Addon may only register its own IDs: " + id);
            }
            @Override public void room(RoomDefinition room) {
                Objects.requireNonNull(room); owns(room.id());
                if (rooms.containsKey(room.id()) || newRooms.putIfAbsent(room.id(), room) != null)
                    throw new IllegalArgumentException("Duplicate room ID: " + room.id());
            }
            @Override public void device(DeviceDefinition device) {
                Objects.requireNonNull(device); owns(device.id());
                if (devices.containsKey(device.id()) || newDevices.containsKey(device.id()))
                    throw new IllegalArgumentException("Duplicate device ID: " + device.id());
                for (var block : device.blockIds()) {
                    if (blocks.containsKey(block) || newBlocks.containsKey(block))
                        throw new IllegalArgumentException("Block already has an institute device handler: " + block);
                }
                newDevices.put(device.id(), device);
                device.blockIds().forEach(block -> newBlocks.put(block, device));
            }
        });
        rooms.putAll(newRooms); devices.putAll(newDevices); blocks.putAll(newBlocks);
    }

    public synchronized void freeze() { frozen = true; }
    public synchronized boolean isFrozen() { return frozen; }
    public synchronized Map<String, RoomDefinition> rooms() { return Collections.unmodifiableMap(new LinkedHashMap<>(rooms)); }
    public synchronized Map<String, DeviceDefinition> devices() { return Collections.unmodifiableMap(new LinkedHashMap<>(devices)); }
    public InteractionResult dispatchDevice(String blockId, DeviceContext context) {
        DeviceDefinition device;
        synchronized (this) { device = blocks.get(blockId); }
        return device == null ? InteractionResult.PASS : Objects.requireNonNull(device.handler().use(context), "Device returned null");
    }
}
