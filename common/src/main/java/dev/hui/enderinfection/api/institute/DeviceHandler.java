package dev.hui.enderinfection.api.institute;

import net.minecraft.world.InteractionResult;

@FunctionalInterface
public interface DeviceHandler {
    /** Return PASS to retain ordinary block/item interaction. Called only on the server thread. */
    InteractionResult use(DeviceContext context);
}
