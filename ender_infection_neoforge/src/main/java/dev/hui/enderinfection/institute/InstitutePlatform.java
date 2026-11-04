package dev.hui.enderinfection.institute;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.api.institute.*;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.common.EventBusSubscriber;

/** NeoForge lifecycle, commands and protection-aware server interaction adapter. */
@EventBusSubscriber(modid = EnderInfection.MOD_ID)
public final class InstitutePlatform {
    private InstitutePlatform() {}
    public static void register(IEventBus bus) {
        InstituteApi.registerAddon(EnderInfection.MOD_ID, r -> r.room(new RoomDefinition(
                "ender_infection:api_demo_room", "ender_infection:institute/api_demo_room", new RoomSize(9, 7, 9),
                List.of(new RoomConnector("entrance", 4, 1, 0, Direction.NORTH, "corridor_3x3")),
                Set.of("ender_infection:research", "ender_infection:api_example"))));
        bus.addListener(InstitutePlatform::setup);
        if (Boolean.getBoolean("ender_infection.instituteChecks")) dev.hui.enderinfection.test.InstituteTestFixtures.register();
    }
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            InstituteApi.installPlacementService(new TemplateRoomPlacementService());
            // All addon constructors have run before common setup work is executed.
            InstituteApi.freeze();
        });
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getUseBlock() == net.neoforged.neoforge.common.util.TriState.FALSE || !(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || event.getFace() == null) return;
        if (!level.mayInteract(player, event.getPos()) || !player.mayUseItemAt(event.getPos(), event.getFace(), player.getItemInHand(event.getHand()))) return;
        var state = level.getBlockState(event.getPos());
        var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) return;
        try {
            var result = InstituteApi.dispatchDevice(blockId.toString(), new DeviceContext(level, player, event.getPos(), state, event.getHand(), event.getFace()));
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Institute device interaction failed at {}", event.getPos(), failure);
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        dev.hui.enderinfection.worldgen.CentralInstituteCommands.register(event.getDispatcher());
        event.getDispatcher().register(Commands.literal("enderinstitute").requires(s -> s.hasPermission(2))
            .then(Commands.literal("rooms").executes(c -> {
                c.getSource().sendSuccess(() -> Component.literal(String.join(", ", InstituteApi.rooms().keySet())), false);
                return InstituteApi.rooms().size();
            }))
            .then(Commands.literal("devices").executes(c -> {
                c.getSource().sendSuccess(() -> Component.literal(String.join(", ", InstituteApi.devices().keySet())), false);
                return InstituteApi.devices().size();
            }))
            .then(Commands.literal("preview").then(roomArgument(false)))
            .then(Commands.literal("place").then(roomArgument(true))));
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<net.minecraft.commands.CommandSourceStack, net.minecraft.resources.ResourceLocation> roomArgument(boolean place) {
        return Commands.argument("room", ResourceLocationArgument.id())
                .suggests((c, builder) -> SharedSuggestionProvider.suggest(InstituteApi.rooms().keySet(), builder))
                .then(Commands.argument("origin", BlockPosArgument.blockPos())
                    .executes(c -> execute(c, place, Rotation.NONE))
                    .then(Commands.argument("rotation", StringArgumentType.word())
                        .suggests((c, builder) -> SharedSuggestionProvider.suggest(List.of("none", "cw90", "cw180", "ccw90"), builder))
                        .executes(c -> {
                            var value = StringArgumentType.getString(c, "rotation");
                            var rotation = switch (value) {
                                case "none" -> Rotation.NONE; case "cw90" -> Rotation.CLOCKWISE_90;
                                case "cw180" -> Rotation.CLOCKWISE_180; case "ccw90" -> Rotation.COUNTERCLOCKWISE_90;
                                default -> null;
                            };
                            if (rotation == null) { c.getSource().sendFailure(Component.literal("rotation: none | cw90 | cw180 | ccw90")); return 0; }
                            return execute(c, place, rotation);
                        })));
    }
    private static int execute(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c, boolean place, Rotation rotation) {
        var origin = BlockPosArgument.getBlockPos(c, "origin");
        var id = ResourceLocationArgument.getId(c, "room").toString();
        var result = place ? InstituteApi.place(c.getSource().getLevel(), id, origin, rotation)
                : InstituteApi.preview(c.getSource().getLevel(), id, origin, rotation);
        if (!result.success()) { c.getSource().sendFailure(Component.literal(result.message())); return 0; }
        c.getSource().sendSuccess(() -> Component.literal(result.message() + " " + id + " " + result.bounds() + " connectors=" + result.connectors()), false);
        return 1;
    }
}
