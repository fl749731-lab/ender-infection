package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.IndustryApi;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Opt-in, isolated client check of the actual registered menu and native button packets. */
@EventBusSubscriber(modid = IndustryApi.MOD_ID, value = Dist.CLIENT)
public final class MeteringPanelVisualCheck {
    private static final String WORLD = "MeteringPanel-" + System.currentTimeMillis();
    private static final BlockPos PUMP = new BlockPos(8, 64, 8);
    private static volatile boolean prepared;
    private static volatile String failure;
    private static boolean launched, finished;
    private static long started;
    private static int step, age;

    @SubscribeEvent public static void prepare(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)
                || !WORLD.equals(player.server.getWorldData().getLevelName())) return;
        try {
            var level = player.serverLevel();
            var data = level.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                    () -> dev.hui.enderinfection.entity.WorldIntroduction.Data.forWorldAge(200),
                    (tag, registries) -> dev.hui.enderinfection.entity.WorldIntroduction.Data.load(tag), null),
                    "ender_infection_world_introduction");
            data.complete();
            for (var pos : BlockPos.betweenClosed(PUMP.offset(-4, -1, -4), PUMP.offset(4, -1, 4))) {
                level.setBlockAndUpdate(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            var rig = new WorkshopGameTests.MixRig(level, PUMP);
            rig.place("precision_metering_pump", player, 9600, 10400);
            rig.output().fill("resonant_industry:neutral_solution", 6400, false);
            var pump = rig.pump();
            var registries = level.registryAccess();
            var pumpTag = pump.saveWithoutMetadata(registries);
            pumpTag.putInt("OxidationAge", 1800);
            pumpTag.putInt("Energy", 768);
            pump.loadWithComponents(pumpTag, registries);
            var reaction = rig.bench().chemistry();
            var mix = Chemistry.Experiment.NEUTRALIZATION;
            var sample = rig.bench().getItem(0).copy();
            var reagent = rig.bench().getItem(1).copy();
            LabFluids.write(sample, mix.sample.stack(), level);
            LabFluids.write(reagent, mix.reagent.stack(), level);
            if (!reaction.beginMeteredMix(sample, reagent)) throw new IllegalStateException("Could not seed active batch");
            var benchTag = rig.bench().saveWithoutMetadata(registries);
            benchTag.getCompound("Chemistry").putInt("Progress", 62);
            rig.bench().loadWithComponents(benchTag, registries);
            rig.signal(true);
            player.getInventory().setItem(1, new ItemStack(IndustryContent.item("vulcanized_seal")));
            player.getInventory().setItem(2, new ItemStack(IndustryContent.item("precision_pump_head")));
            player.getInventory().setItem(3, new ItemStack(IndustryContent.item("sulfur")));
            player.getInventory().selected = 0;
            player.teleportTo(level, 6.5, 64, 6.5, -42, 12);
            prepared = true;
        } catch (RuntimeException error) {
            failure = error.toString();
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!enabled() || finished) return;
        var mc = Minecraft.getInstance();
        if (started == 0) started = System.currentTimeMillis();
        try {
            if (!launched && mc.getOverlay() == null && mc.screen != null
                    && (mc.screen instanceof TitleScreen || mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))) {
                launched = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(3);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(7312026L, false, false),
                        lookup -> lookup.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), null);
                return;
            }
            if (failure != null || System.currentTimeMillis() - started > 180000) {
                throw new IllegalStateException(failure == null ? "Timed out at step " + step : failure);
            }
            if (!prepared || mc.player == null || mc.level == null || mc.getOverlay() != null) return;
            mc.getToasts().clear();
            if (++age < 12) return;
            if (step == 0 && mc.screen == null && mc.level.getBlockState(PUMP).is(IndustryContent.block("precision_metering_pump"))) {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(PUMP), Direction.WEST, PUMP, false));
                next();
                return;
            }
            if (!(mc.screen instanceof MeteringPumpScreen screen) || !(mc.player.containerMenu instanceof MeteringPumpMenu menu)) return;
            switch (step) {
                case 1 -> {
                    if (!menu.automatic() || menu.oxidation() != 3 || menu.progress() != 62
                            || menu.gauge(MeteringPumpMenu.OUTPUT).amount() != 6400) return;
                    capture(mc, "metering_process");
                    press(screen, "maintenance");
                    next();
                }
                case 2 -> {
                    if (menu.getSlot(0).isActive()) return;
                    capture(mc, "metering_maintenance");
                    press(screen, "install_seal");
                    next();
                }
                case 3 -> {
                    if (!menu.sealed() || menu.hasPart("vulcanized_seal") || menu.oxidation() != 3) return;
                    press(screen, "replace_head");
                    next();
                }
                case 4 -> {
                    if (menu.oxidation() != 0 || !menu.sealed() || menu.hasPart("precision_pump_head")
                            || menu.gauge(MeteringPumpMenu.OUTPUT).amount() < 6450) return;
                    press(screen, "maintenance");
                    next();
                }
                case 5 -> {
                    capture(mc, "metering_resumed");
                    finished = true;
                    System.out.println("METERING_PANEL_VISUAL_PASS shots=3");
                    mc.stop();
                }
                default -> { }
            }
        } catch (Exception error) {
            finished = true;
            System.err.println("METERING_PANEL_VISUAL_FAIL step=" + step + " " + error);
            error.printStackTrace();
            mc.stop();
        }
    }

    private static void press(MeteringPumpScreen screen, String key) {
        var label = Component.translatable("workshop.resonant_industry.gui." + key).getString();
        for (var widget : screen.children()) {
            if (widget instanceof Button button && button.visible && button.active && button.getMessage().getString().equals(label)) {
                button.onPress();
                return;
            }
        }
        throw new IllegalStateException("Unavailable panel button " + key);
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        var path = mc.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(path);
        try (var frame = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { frame.writeToFile(path.resolve(name + ".png")); }
    }

    private static boolean enabled() { return Boolean.getBoolean("resonant_industry.meteringPanelVisualCheck"); }
    private static void next() { step++; age = 0; }
}
