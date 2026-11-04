package dev.hui.enderinfection.test;

import com.mojang.authlib.GameProfile;
import dev.hui.enderinfection.api.institute.*;
import dev.hui.enderinfection.institute.InstitutePlatform;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ender_institute_checks")
@PrefixGameTestTemplate(false)
public final class InstituteGameTests {
    private static final String DEMO = "ender_infection:api_demo_room";
    @GameTest(template = "empty64", templateNamespace = "ender_institute_checks", timeoutTicks = 60, batch = "institute_registry")
    public static void registrationRejectsDuplicatesAndRollsBackWholeAddon(GameTestHelper h) {
        var registry = new InstituteRegistry();
        registry.registerAddon("sample", r -> r.room(room("sample:first")));
        try {
            registry.registerAddon("sample", r -> { r.room(room("sample:second")); r.room(room("sample:first")); });
            h.fail("Duplicate room accepted");
        } catch (IllegalArgumentException expected) { }
        h.assertTrue(registry.rooms().size() == 1 && !registry.rooms().containsKey("sample:second"), "Failed addon leaked partial registration");
        try { registry.registerAddon("other", r -> r.room(room("sample:foreign"))); h.fail("Foreign namespace accepted"); }
        catch (IllegalArgumentException expected) { }
        h.succeed();
    }
    @GameTest(template = "empty64", templateNamespace = "ender_institute_checks", timeoutTicks = 60, batch = "institute_registry")
    public static void snapshotsAreImmutableAndFreezeClosesRegistration(GameTestHelper h) {
        var registry = new InstituteRegistry();
        registry.registerAddon("sample", r -> r.room(room("sample:first")));
        var snapshot = registry.rooms();
        registry.registerAddon("sample", r -> r.room(room("sample:second")));
        h.assertTrue(snapshot.size() == 1 && registry.rooms().size() == 2, "Snapshot changed after later registration");
        try { snapshot.clear(); h.fail("Snapshot was mutable"); } catch (UnsupportedOperationException expected) { }
        registry.freeze();
        try { registry.registerAddon("sample", r -> r.room(room("sample:late"))); h.fail("Late registration accepted"); }
        catch (IllegalStateException expected) { }
        h.assertTrue(InstituteApi.isFrozen(), "Real loader did not freeze registry at common setup");
        h.succeed();
    }
    @GameTest(template = "empty64", templateNamespace = "ender_institute_checks", timeoutTicks = 60, batch = "institute_registry")
    public static void badDimensionsConnectorsAndDuplicateDeviceBindingsAreRejected(GameTestHelper h) {
        try { new RoomSize(0, 7, 9); h.fail("Zero dimension accepted"); } catch (IllegalArgumentException expected) { }
        try { new RoomDefinition("sample:bad", "sample:room", new RoomSize(9, 7, 9),
                List.of(new RoomConnector("bad", 9, 1, 0, Direction.NORTH, "door")), Set.of()); h.fail("Outside connector accepted"); }
        catch (IllegalArgumentException expected) { }
        var registry = new InstituteRegistry();
        registry.registerAddon("sample", r -> r.device(new DeviceDefinition("sample:a", Set.of("minecraft:stone"), c -> InteractionResult.PASS)));
        try { registry.registerAddon("sample", r -> r.device(new DeviceDefinition("sample:b", Set.of("minecraft:stone"), c -> InteractionResult.SUCCESS))); h.fail("Ambiguous block binding accepted"); }
        catch (IllegalArgumentException expected) { }
        h.assertTrue(registry.devices().size() == 1, "Failed device leaked");
        h.succeed();
    }
    @GameTest(template = "empty64", templateNamespace = "ender_institute_checks", timeoutTicks = 60, batch = "institute_place")
    public static void previewHasNoWritesAndRotatesBoundsAndConnector(GameTestHelper h) {
        var origin = h.absolutePos(new BlockPos(20, 1, 20));
        var result = InstituteApi.preview(h.getLevel(), DEMO, origin, Rotation.CLOCKWISE_90);
        h.assertTrue(result.success(), result.message());
        h.assertTrue(h.getLevel().getBlockState(origin).isAir(), "Preview wrote a floor block");
        h.assertTrue(result.bounds().minX() == origin.getX() - 8 && result.bounds().maxZ() == origin.getZ() + 8, "Wrong rotated bounds");
        var connector = result.connectors().get(0);
        h.assertTrue(connector.pos().equals(origin.offset(0, 1, 4)) && connector.facing() == Direction.EAST, "Connector did not rotate with template");
        h.succeed();
    }
    @GameTest(template = "empty64", templateNamespace = "ender_institute_checks", timeoutTicks = 60, batch = "institute_place")
    public static void realPlacementCreatesTemplateFloorAndRotatedFurniture(GameTestHelper h) {
        var origin = h.absolutePos(new BlockPos(20, 1, 20));
        var result = InstituteApi.place(h.getLevel(), DEMO, origin, Rotation.CLOCKWISE_90);
        h.assertTrue(result.success(), result.message());
        h.assertTrue(h.getLevel().getBlockState(origin).is(dev.hui.enderinfection.InfectedContent.LAB_TILE_LIGHT.get()), "Template floor absent");
        var monitor = h.getLevel().getBlockState(origin.offset(-6, 2, 2));
        h.assertTrue(monitor.is(dev.hui.enderinfection.design.DesignContent.BLOCKS_BY_KEY.get("lab_monitor").get())
                && monitor.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) == Direction.EAST, "Template furniture rotation lost");
        h.succeed();
    }
    @GameTest(template = "empty64", templateNamespace = "ender_institute_checks", timeoutTicks = 60, batch = "institute_place")
    public static void occupiedBlockEntityRejectsPlacementBeforeAnyWrites(GameTestHelper h) {
        var origin = h.absolutePos(new BlockPos(16, 1, 16));
        var chest = origin.offset(4, 1, 4);
        h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        var blockEntity = h.getLevel().getBlockEntity(chest);
        var result = InstituteApi.place(h.getLevel(), DEMO, origin, Rotation.NONE);
        h.assertTrue(!result.success() && result.message().startsWith("occupied_site"), "Occupied site accepted");
        h.assertTrue(h.getLevel().getBlockEntity(chest) == blockEntity && h.getLevel().getBlockState(origin).isAir(), "Failure modified the site");
        h.succeed();
    }
    @GameTest(template = "empty64", templateNamespace = "ender_institute_checks", timeoutTicks = 60, batch = "institute_place")
    public static void missingAndWrongSizeTemplatesFailWithoutWrites(GameTestHelper h) {
        var origin = h.absolutePos(new BlockPos(16, 1, 16));
        var missing = InstituteApi.place(h.getLevel(), "ender_institute_checks:missing", origin, Rotation.NONE);
        var wrong = InstituteApi.place(h.getLevel(), "ender_institute_checks:wrong_size", origin, Rotation.NONE);
        h.assertTrue(!missing.success() && missing.message().startsWith("missing_template"), missing.message());
        h.assertTrue(!wrong.success() && wrong.message().startsWith("template_size_mismatch"), wrong.message());
        h.assertTrue(h.getLevel().getBlockState(origin).isAir(), "Invalid template wrote to site");
        h.succeed();
    }
    @GameTest(template = "empty64", templateNamespace = "ender_institute_checks", timeoutTicks = 60, batch = "institute_place")
    public static void unloadedChunkIsRejectedWithoutLoadingIt(GameTestHelper h) {
        var origin = new BlockPos(29_000_000, 100, 29_000_000);
        int x = origin.getX() >> 4, z = origin.getZ() >> 4;
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(x, z) == null, "Fixture chunk already loaded");
        var result = InstituteApi.place(h.getLevel(), DEMO, origin, Rotation.NONE);
        h.assertTrue(!result.success() && result.message().equals("unloaded_chunk"), result.message());
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(x, z) == null, "API loaded a chunk");
        h.succeed();
    }
    @GameTest(template = "empty64", templateNamespace = "ender_institute_checks", timeoutTicks = 60, batch = "institute_device")
    public static void registeredDeviceSuccessTravelsThroughRealForgeEvent(GameTestHelper h) {
        var event = interaction(h, true);
        int before = InstituteTestFixtures.DEVICE_CALLS.get();
        MinecraftForge.EVENT_BUS.post(event);
        h.assertTrue(event.isCanceled() && event.getCancellationResult() == InteractionResult.SUCCESS, "Device result did not consume event");
        h.assertTrue(InstituteTestFixtures.DEVICE_CALLS.get() == before + 1, "Device not invoked exactly once");
        h.succeed();
    }
    @GameTest(template = "empty64", templateNamespace = "ender_institute_checks", timeoutTicks = 60, batch = "institute_device")
    public static void devicePassKeepsVanillaInteractionAvailable(GameTestHelper h) {
        var event = interaction(h, false);
        int before = InstituteTestFixtures.DEVICE_CALLS.get();
        MinecraftForge.EVENT_BUS.post(event);
        h.assertTrue(!event.isCanceled(), "PASS swallowed ordinary interaction");
        h.assertTrue(InstituteTestFixtures.DEVICE_CALLS.get() == before + 1, "PASS handler not dispatched");
        h.succeed();
    }
    @GameTest(template = "empty64", templateNamespace = "ender_institute_checks", timeoutTicks = 60, batch = "institute_device")
    public static void canceledProtectionEventNeverDispatchesDevice(GameTestHelper h) {
        var event = interaction(h, true);
        event.setCanceled(true);
        int before = InstituteTestFixtures.DEVICE_CALLS.get();
        MinecraftForge.EVENT_BUS.post(event);
        h.assertTrue(InstituteTestFixtures.DEVICE_CALLS.get() == before, "Canceled interaction reached device");
        h.succeed();
    }
    @GameTest(template = "empty64", templateNamespace = "ender_institute_checks", timeoutTicks = 60, batch = "institute_device")
    public static void deniedBlockUseNeverDispatchesDevice(GameTestHelper h) {
        var event = interaction(h, true);
        event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        int before = InstituteTestFixtures.DEVICE_CALLS.get();
        MinecraftForge.EVENT_BUS.post(event);
        h.assertTrue(InstituteTestFixtures.DEVICE_CALLS.get() == before, "Denied block interaction reached device");
        h.succeed();
    }
    private static RoomDefinition room(String id) { return new RoomDefinition(id, "sample:room", new RoomSize(9, 7, 9), List.of(), Set.of()); }
    private static PlayerInteractEvent.RightClickBlock interaction(GameTestHelper h, boolean consume) {
        var pos = h.absolutePos(new BlockPos(4, 1, 4));
        h.getLevel().setBlock(pos, Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.POWERED, consume), 3);
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "InstituteTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 0, 0);
        return new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
}
