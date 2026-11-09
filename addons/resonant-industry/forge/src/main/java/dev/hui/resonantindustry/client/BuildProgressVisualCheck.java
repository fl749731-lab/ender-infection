package dev.hui.resonantindustry.client;

import dev.hui.resonantindustry.IndustryBlock;
import dev.hui.resonantindustry.IndustryContent;
import dev.hui.resonantindustry.IndustryNode;
import dev.hui.resonantindustry.api.IndustryApi;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Real client/server block updates and chunk-forget packets in an isolated build/ world. */
@Mod.EventBusSubscriber(modid=IndustryApi.MOD_ID,value=Dist.CLIENT)
public final class BuildProgressVisualCheck {
    private static final boolean ENABLED = Boolean.getBoolean("resonant_industry.buildProgressCheck");
    private static final String WORLD = "BuildProgress-" + System.currentTimeMillis();
    private static final BlockPos CONTROLLER = new BlockPos(15, 64, 0);
    private static final BlockPos MISSING = CONTROLLER.offset(-1, -1, 0);
    private static final BlockPos WRONG = CONTROLLER.offset(0, -1, 2);
    private static final BlockPos BLOCKED = CONTROLLER.offset(0, 0, 1);
    private static final Set<String> SHOTS = new LinkedHashSet<>();
    private static volatile boolean built, busy;
    private static volatile String failure;
    private static boolean launched, finished, tearingDown, savedWindow, originalFullscreen;
    private static int step, age, firstLayer, originalWidth, originalHeight, originalGuiScale;
    private static long began;
    private static Vec3 eye = new Vec3(15.5, 64, -3);

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer player)
                || !player.server.getWorldData().getLevelName().equals(WORLD)) return;
        try {
            var level = player.serverLevel();
            level.getDataStorage().computeIfAbsent(dev.hui.enderinfection.entity.WorldIntroduction.Data::load,()->dev.hui.enderinfection.entity.WorldIntroduction.Data.forWorldAge(200),"ender_infection_world_introduction").complete();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
            level.setDayTime(6000);
            for (int x = 0; x <= 1; x++) for (int z = -1; z <= 0; z++) {
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
            }
            for (int x = 12; x <= 18; x++) for (int z = -4; z <= 5; z++)
                level.setBlock(new BlockPos(x, 62, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            level.setBlock(CONTROLLER, IndustryContent.block("controller").defaultBlockState(), 3);
            var node = IndustryNode.at(level, CONTROLLER);
            require(node != null && node.configure(IndustryApi.PROCESSOR), "Could not configure processor fixture");
            for (var entry : node.definition().cells().entrySet()) {
                if (entry.getValue().equals("controller")) continue;
                var state = entry.getValue().equals("air") ? Blocks.AIR.defaultBlockState()
                        : IndustryContent.block(entry.getValue()).defaultBlockState().setValue(IndustryBlock.FACING, Direction.NORTH);
                level.setBlock(node.world(entry.getKey()), state, 3);
            }
            level.setBlock(MISSING, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(WRONG, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(BLOCKED, Blocks.STONE.defaultBlockState(), 3);
            player.setGameMode(GameType.ADVENTURE);
            player.setNoGravity(true);
            player.getAbilities().invulnerable = true;
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(IndustryContent.item("wrench")));
            player.getInventory().setItem(1, new ItemStack(IndustryContent.block("casing"), 32));
            position(player);
            built = true;
        } catch (Exception error) { failure = error.toString(); }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || tearingDown || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (began == 0) began = System.currentTimeMillis();
        try {
            if (failure != null || System.currentTimeMillis() - began > 300_000)
                throw new IllegalStateException(failure == null ? "Timeout at step " + step : failure);
            if (!launched) {
                if (mc.getOverlay() != null || mc.screen == null || !(mc.screen instanceof TitleScreen
                        || mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))) return;
                launched = true;
                var window = mc.getWindow();
                originalWidth = window.getScreenWidth();
                originalHeight = window.getScreenHeight();
                originalGuiScale = mc.options.guiScale().get();
                originalFullscreen = window.isFullscreen();
                savedWindow = true;
                if (originalFullscreen) window.toggleFullScreen();
                mc.options.guiScale().set(2);
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(4);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.resizeDisplay();
                mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
            }
            if (step == 9) {
                if (mc.level == null && mc.getOverlay() == null) {
                    require(BuildGuide.current() == null, "Disconnected world retained a guide");
                    System.out.println("BUILD_PROGRESS_LIFECYCLE_PASS distance=true disconnect=true");
                    finish(mc, null);
                }
                return;
            }
            if (!built || busy || mc.level == null || mc.player == null || mc.getOverlay() != null) return;
            camera(mc);
            if (++age < 20) return;
            var view = BuildGuide.current();
            switch (step) {
                case 0 -> {
                    if (view == null || view.all().unknown() != 0) return;
                    expectIncomplete(view.all());
                    require(CONTROLLER.equals(view.controller()) && !view.formed(), "Pinned the wrong controller");
                    shot(mc, "build-progress-missing");
                    mc.player.getInventory().selected = 1;
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 640, 480);
                    mc.resizeDisplay();
                    advance(1);
                }
                case 1 -> {
                    require(mc.getWindow().getGuiScaledWidth() == 320 && mc.getWindow().getGuiScaledHeight() == 240,
                            "Narrow fixture did not reach 320x240 GUI");
                    require(view != null, "Switching to building material lost the guide");
                    expectIncomplete(view.all());
                    shot(mc, "build-progress-narrow");
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), originalWidth, originalHeight);
                    mc.resizeDisplay();
                    mc.options.keyShift.setDown(true);
                    advance(2);
                }
                case 2 -> {
                    require(view != null && view.layer() >= 0 && view.layer() < 4, "Sneaking did not select a structure layer");
                    expectLayer(view);
                    firstLayer = view.layer();
                    shot(mc, "build-progress-layer");
                    advance(3);
                }
                case 3 -> {
                    require(view != null, "Layer preview disappeared");
                    expectLayer(view);
                    if (view.layer() == firstLayer) return;
                    mc.options.keyShift.setDown(false);
                    call(mc, player -> {
                        var level = player.serverLevel();
                        level.setBlock(MISSING, IndustryContent.block("casing").defaultBlockState(), 3);
                        level.setBlock(WRONG, IndustryContent.block("item_output").defaultBlockState(), 3);
                        level.setBlock(BLOCKED, Blocks.AIR.defaultBlockState(), 3);
                    });
                    advance(4);
                }
                case 4 -> {
                    if (view == null || view.all().placed() != 34 || view.all().blockedAir() != 0 || view.layer() != -1) return;
                    require(view.all().total() == 34 && view.all().missing().isEmpty() && view.all().wrongBlocks() == 0
                            && view.all().unknown() == 0 && !view.formed(), "Ready progress incorrectly claimed assembly");
                    shot(mc, "build-progress-ready");
                    mc.player.getInventory().selected = 0;
                    advance(5);
                }
                case 5 -> {
                    var from = mc.player.getEyePosition(1);
                    var aim = focus().subtract(from).normalize();
                    var hit = mc.level.clip(new ClipContext(from, from.add(aim.scale(4.8)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
                    require(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(CONTROLLER), "Wrench ray missed controller");
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                    advance(6);
                }
                case 6 -> {
                    if (view == null || !view.formed()) return;
                    require(view.all().placed() == 34 && view.all().missing().isEmpty(), "Assembly changed the counted structure");
                    mc.getConnection().handleForgetLevelChunk(new ClientboundForgetLevelChunkPacket(1, 0));
                    require(mc.level.getChunkSource().getChunk(1, 0, false) == null, "Native chunk-forget packet did not unload the chunk");
                    var node = (IndustryNode) mc.level.getBlockEntity(CONTROLLER);
                    expectUnloaded(BuildProgress.scan(node));
                    System.out.println("BUILD_PROGRESS_UNLOADED_PASS unknown=12 placed=22 total=34 missing=0");
                    advance(7);
                }
                case 7 -> {
                    require(view != null, "Losing a neighbor chunk lost the loaded controller guide");
                    expectUnloaded(view.all());
                    shot(mc, "build-progress-unloaded");
                    eye = new Vec3(39.5, 64, -3);
                    call(mc, BuildProgressVisualCheck::position);
                    advance(8);
                }
                case 8 -> {
                    require(view == null, "Walking beyond 16 blocks retained the guide");
                    advance(9);
                    tearingDown = true;
                    try {
                        mc.level.disconnect();
                        mc.clearLevel(new TitleScreen());
                    } finally { tearingDown = false; }
                }
                default -> throw new IllegalStateException("Unknown step " + step);
            }
        } catch (Exception error) { finish(mc, error.toString()); }
    }

    private static void expectIncomplete(BuildProgress progress) {
        require(progress.placed() == 32 && progress.total() == 34 && progress.unknown() == 0
                        && progress.wrongBlocks() == 1 && progress.blockedAir() == 1
                        && progress.missing().equals(Map.of("casing", 1, "item_output", 1)),
                "Expected 32/34 with one missing casing, one wrong outlet and one blocked air cell: " + progress);
    }

    private static void expectLayer(BuildGuide.View view) {
        var visible = view.visible();
        int layer = view.layer();
        require(layer >= 0 && layer < 4 && visible.cells().size() == 9
                        && visible.cells().stream().allMatch(cell -> cell.layer() == layer), "HUD and preview layer cells diverged");
        require(visible.total() == (layer == 1 || layer == 2 ? 8 : 9)
                        && visible.placed() == (layer == 0 ? 7 : visible.total())
                        && visible.blockedAir() == (layer == 1 ? 1 : 0)
                        && visible.wrongBlocks() == (layer == 0 ? 1 : 0)
                        && visible.missing().equals(layer == 0 ? Map.of("casing", 1, "item_output", 1) : Map.of()),
                "Layer progress disagreed with the physical fixture");
    }

    private static void expectUnloaded(BuildProgress progress) {
        require(progress.unknown() == 12 && progress.placed() == 22 && progress.total() == 34
                        && progress.missing().isEmpty() && progress.blockedAir() == 0 && progress.wrongBlocks() == 0,
                "Unloaded cells were incorrectly counted as missing or correct: " + progress);
    }

    private static Vec3 focus() { return Vec3.atCenterOf(CONTROLLER).add(0, .15, 0); }
    private static float yaw() { var delta = focus().subtract(eye.add(0, 1.62, 0)); return (float) Math.toDegrees(Math.atan2(-delta.x, delta.z)); }
    private static float pitch() { var delta = focus().subtract(eye.add(0, 1.62, 0)); return (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))); }
    private static void position(ServerPlayer player) { player.teleportTo(player.serverLevel(), eye.x, eye.y, eye.z, yaw(), pitch()); }
    private static void camera(Minecraft mc) {
        mc.player.setPos(eye);
        mc.player.setYRot(yaw());
        mc.player.setXRot(pitch());
        mc.player.setDeltaMovement(Vec3.ZERO);
    }
    private static void advance(int next) { step = next; age = 0; }
    private static void call(Minecraft mc, Consumer<ServerPlayer> work) {
        busy = true;
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> {
            try { work.accept(server.getPlayerList().getPlayer(id)); }
            catch (Exception error) { failure = error.toString(); }
            finally { busy = false; }
        });
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void shot(Minecraft mc, String name) throws Exception {
        require(SHOTS.add(name), "Duplicate screenshot " + name);
        var directory = mc.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(directory);
        try (var frame = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { frame.writeToFile(directory.resolve(name + ".png")); }
        System.out.println("BUILD_PROGRESS_CAPTURE " + name);
    }
    private static void finish(Minecraft mc, String error) {
        finished = true;
        if (error == null && SHOTS.size() != 5) error = "Wrong screenshot count";
        String result = (error == null ? "BUILD_PROGRESS_PASS" : "BUILD_PROGRESS_FAIL")
                + " shots=" + SHOTS.size() + " step=" + step + (error == null ? "" : " " + error);
        System.out.println(result);
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("build-progress-result.txt"),
                    result + "\n" + String.join("\n", SHOTS), StandardCharsets.UTF_8);
        } catch (Exception exception) { throw new RuntimeException(exception); }
        finally {
            mc.options.keyShift.setDown(false);
            if (savedWindow) {
                mc.options.guiScale().set(originalGuiScale);
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), originalWidth, originalHeight);
                if (originalFullscreen) mc.getWindow().toggleFullScreen();
                mc.resizeDisplay();
            }
            mc.stop();
        }
    }
}
