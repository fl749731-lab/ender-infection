package dev.hui.instituteaddon;

import dev.hui.enderinfection.api.institute.DeviceContext;
import dev.hui.enderinfection.api.institute.DeviceDefinition;
import dev.hui.enderinfection.api.institute.InstituteApi;
import dev.hui.enderinfection.api.institute.ProcessingRecipe;
import dev.hui.enderinfection.api.institute.RoomConnector;
import dev.hui.enderinfection.api.institute.RoomDefinition;
import dev.hui.enderinfection.api.institute.RoomSize;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/** Loader-independent addon registration, compiled against API v1 on both versions. */
public final class AddonRegistration {
    public static final String MOD_ID = "institute_addon";
    public static final String CONSOLE_ID = MOD_ID + ":research_console";
    public static final String ROOM_ID = MOD_ID + ":analysis_room";
    public static final String RECIPE_ID = MOD_ID + ":archive_copy";

    private AddonRegistration() {}

    public static void register() {
        InstituteApi.registerAddon(MOD_ID, registrar -> {
            registrar.room(new RoomDefinition(ROOM_ID, ROOM_ID, new RoomSize(9, 6, 9),
                    List.of(new RoomConnector("north_entry", 4, 1, 0, Direction.NORTH, "corridor_3x3"),
                            new RoomConnector("south_entry", 4, 1, 8, Direction.SOUTH, "corridor_3x3")),
                    Set.of("institute:laboratory", "institute_addon:example", "minecraft:overworld")));
            registrar.device(new DeviceDefinition(CONSOLE_ID, Set.of(CONSOLE_ID), AddonRegistration::useConsole));
            registrar.processing(new ProcessingRecipe(RECIPE_ID, Set.of("ender_infection:lab_microscope"),
                    "minecraft:paper", 2, "minecraft:redstone", 1, "minecraft:book", 1, 80));
        });
    }

    public static InteractionResult useConsole(DeviceContext context) {
        if (context.hand() != InteractionHand.MAIN_HAND
                || !context.state().hasProperty(ResearchConsoleBlock.POWERED)) {
            return InteractionResult.PASS;
        }
        boolean powered = !context.state().getValue(ResearchConsoleBlock.POWERED);
        if (!context.level().setBlock(context.pos(), context.state().setValue(ResearchConsoleBlock.POWERED, powered), 3)) {
            return InteractionResult.FAIL;
        }
        var message = Component.translatable(powered
                ? "message.institute_addon.console_enabled" : "message.institute_addon.console_disabled");
        // Querying the containing institute does not bind this demo console to
        // its grid. Its local toggle and light still work outside an institute.
        InstituteApi.facility(context.level(), context.pos()).ifPresent(status -> message.append(
                Component.translatable("message.institute_addon.facility_power",
                        Component.translatable(status.servicePowered()
                                ? "message.institute_addon.power_on" : "message.institute_addon.power_off"),
                        Component.translatable(status.containmentPowered()
                                ? "message.institute_addon.power_on" : "message.institute_addon.power_off"))));
        context.player().displayClientMessage(message, true);
        return InteractionResult.CONSUME;
    }
}
