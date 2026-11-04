package dev.hui.enderinfection.test;

import dev.hui.enderinfection.api.institute.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.NoteBlock;

/** Registered only in the isolated -PinstituteChecks test process. */
public final class InstituteTestFixtures {
    public static final AtomicInteger DEVICE_CALLS = new AtomicInteger();
    private InstituteTestFixtures() {}
    public static void register() {
        InstituteApi.registerAddon("ender_institute_checks", r -> {
            r.room(new RoomDefinition("ender_institute_checks:missing", "ender_institute_checks:no_such_template",
                    new RoomSize(9, 7, 9), List.of(), Set.of()));
            r.room(new RoomDefinition("ender_institute_checks:wrong_size", "ender_infection:institute/api_demo_room",
                    new RoomSize(10, 7, 9), List.of(), Set.of()));
            r.device(new DeviceDefinition("ender_institute_checks:note_console", Set.of("minecraft:note_block"), c -> {
                DEVICE_CALLS.incrementAndGet();
                return c.state().getValue(NoteBlock.POWERED) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }));
        });
    }
}
