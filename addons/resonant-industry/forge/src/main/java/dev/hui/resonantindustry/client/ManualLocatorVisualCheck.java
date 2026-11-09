package dev.hui.resonantindustry.client;

import dev.hui.resonantindustry.api.IndustryApi;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Opt-in mouse-driven handbook checks, isolated from player worlds and bookmarks. */
@Mod.EventBusSubscriber(modid=IndustryApi.MOD_ID,value=Dist.CLIENT)
public final class ManualLocatorVisualCheck {
    private static final boolean ENABLED = Boolean.getBoolean("resonant_industry.manualLocatorCheck");
    private static final Set<String> SHOTS = new LinkedHashSet<>();
    private static boolean launched, finished, resized, originalFullscreen;
    private static int step, age, originalWidth, originalHeight, originalGuiScale;
    private static long began;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (began == 0) began = System.currentTimeMillis();
        if (System.currentTimeMillis() - began > 180_000) { finish(mc, "timeout"); return; }
        try {
            if (!launched) {
                if (mc.getOverlay() != null || mc.screen == null || !(mc.screen instanceof TitleScreen
                        || mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))) return;
                launched = true;
                mc.options.pauseOnLostFocus = false;
                var window = mc.getWindow();
                originalWidth = window.getScreenWidth();
                originalHeight = window.getScreenHeight();
                originalGuiScale = mc.options.guiScale().get();
                originalFullscreen = window.isFullscreen();
                resized = true;
                if (originalFullscreen) window.toggleFullScreen();
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                var manual = new IndustryManualScreen(mc.gameDirectory.toPath().resolve("locator-fixture"));
                mc.setScreen(manual);
                require(manual.width >= 390 && manual.height >= 222, "Normal fixture window is too small");
                chapter(manual, "processor");
                press(manual, "reset_view");
                counts(manual, "controller", 1, 1);
                counts(manual, "item_output", 1, 1);
                material(manual, "item_output");
                require("item_output".equals(manual.selectedMaterial()), "Material click did not select the output port");
                age = 0;
                return;
            }
            if (mc.getOverlay() != null || ++age < 20) return;
            require(mc.screen instanceof IndustryManualScreen, "The manual screen disappeared");
            var manual = (IndustryManualScreen) mc.screen;
            switch (step) {
                case 0 -> {
                    shot(mc, "manual-locator-full");
                    material(manual, "item_output");
                    require(manual.selectedMaterial() == null, "Second click did not clear the selection");
                    material(manual, "controller");
                    press(manual, "all_layers");
                    require("controller".equals(manual.selectedMaterial()), "Changing layers cleared the selection");
                    counts(manual, "controller", 0, 1);
                    counts(manual, "item_output", 1, 1);
                    step = 1;
                }
                case 1 -> {
                    shot(mc, "manual-locator-absent-layer");
                    press(manual, "structure_layer", 1, 4);
                    counts(manual, "controller", 1, 1);
                    counts(manual, "item_output", 0, 1);
                    float before = manual.bookmark().yaw();
                    double x = manual.screenX(91), y = manual.screenY(110);
                    manual.mouseClicked(x, y, 0);
                    require(manual.mouseDragged(x, y, 0, 30, 5), "Structure rejected native mouse drag");
                    manual.mouseReleased(x, y, 0);
                    require(manual.bookmark().yaw() != before, "Dragging did not rotate the structure");
                    step = 2;
                }
                case 2 -> {
                    shot(mc, "manual-locator-rotated-front");
                    press(manual, "instructions");
                    manual.mouseClicked(manual.screenX(210), manual.screenY(74), 0);
                    manual.mouseReleased(manual.screenX(210), manual.screenY(74), 0);
                    require("controller".equals(manual.selectedMaterial()), "Instructions unexpectedly acted as material rows");
                    press(manual, "materials");
                    press(manual, "contents");
                    // This gap is inside the materials' hit area but between the contents buttons.
                    manual.mouseClicked(manual.screenX(195), manual.screenY(74), 0);
                    manual.mouseReleased(manual.screenX(195), manual.screenY(74), 0);
                    require("controller".equals(manual.selectedMaterial()), "Contents unexpectedly acted as material rows");
                    press(manual, "intro.title");
                    require(manual.selectedMaterial() == null, "Changing chapters did not clear the selection");
                    manual.mouseClicked(manual.screenX(210), manual.screenY(74), 0);
                    manual.mouseReleased(manual.screenX(210), manual.screenY(74), 0);
                    require(manual.selectedMaterial() == null, "Reading page unexpectedly selected a material");
                    chapter(manual, "processor");
                    press(manual, "reset_view");
                    material(manual, "controller");
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 640, 480);
                    mc.resizeDisplay();
                    step = 3;
                }
                case 3 -> {
                    require(manual.width == 320 && manual.height == 240,
                            "Narrow fixture is " + manual.width + "x" + manual.height);
                    require(manual.screenX(1) - manual.screenX(0) < 1, "Narrow manual did not scale");
                    require("controller".equals(manual.selectedMaterial()), "Resizing cleared the current selection");
                    material(manual, "item_output");
                    require("item_output".equals(manual.selectedMaterial()), "Scaled row click selected the wrong material");
                    press(manual, "all_layers");
                    counts(manual, "item_output", 1, 1);
                    step = 4;
                }
                case 4 -> {
                    shot(mc, "manual-locator-narrow");
                    material(manual, "item_output");
                    require(manual.selectedMaterial() == null, "Scaled second click did not clear the selection");
                    material(manual, "controller");
                    chapter(manual, "generator");
                    require(manual.selectedMaterial() == null, "Scaled chapter navigation retained the old selection");
                    require(SHOTS.size() == 4, "Wrong locator screenshot count");
                    finish(mc, null);
                }
                default -> throw new IllegalStateException("Unknown locator step " + step);
            }
            age = 0;
        } catch (Exception error) { finish(mc, error.toString()); }
    }

    private static void counts(IndustryManualScreen manual, String role, int visible, int total) {
        var count = manual.materialCounts().get(role);
        require(count != null && count.visible() == visible && count.total() == total,
                role + " count should be " + visible + "/" + total + ", got " + count);
    }

    private static void material(IndustryManualScreen manual, String role) {
        int row = new ArrayList<>(manual.materialCounts().keySet()).indexOf(role);
        require(row >= 0, "Missing material row " + role);
        click(manual, manual.screenX(210), manual.screenY(68 + row * 14 + 6));
    }

    private static void chapter(IndustryManualScreen manual, String chapter) {
        press(manual, "contents");
        press(manual, chapter + ".title");
    }

    private static void press(IndustryManualScreen manual, String key, Object... arguments) {
        String label = Component.translatable("guide.resonant_industry." + key, arguments).getString();
        double scale = manual.screenX(1) - manual.screenX(0);
        for (var child : manual.children()) {
            if (child instanceof Button button && button.visible && button.active && button.getMessage().getString().equals(label)) {
                click(manual, (button.getX() + button.getWidth() / 2.0) * scale,
                        (button.getY() + button.getHeight() / 2.0) * scale);
                return;
            }
        }
        throw new IllegalStateException("Manual control missing: " + label);
    }

    private static void click(IndustryManualScreen manual, double x, double y) {
        require(x >= 0 && y >= 0 && x < manual.width && y < manual.height, "Click was outside the actual viewport");
        manual.mouseMoved(x, y);
        require(manual.mouseClicked(x, y, 0), "Manual rejected click at " + x + "," + y);
        manual.mouseReleased(x, y, 0);
        // Keep the system cursor away from rows so tooltips do not cover the captures.
        GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(), 2, 2);
    }

    private static void shot(Minecraft mc, String name) throws Exception {
        require(SHOTS.add(name), "Duplicate screenshot " + name);
        var directory = mc.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(directory);
        try (var frame = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { frame.writeToFile(directory.resolve(name + ".png")); }
        System.out.println("MANUAL_LOCATOR_CAPTURE " + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void finish(Minecraft mc, String failure) {
        finished = true;
        String result = (failure == null ? "MANUAL_LOCATOR_PASS" : "MANUAL_LOCATOR_FAIL")
                + " shots=" + SHOTS.size() + " gui=320x240" + (failure == null ? "" : " " + failure);
        System.out.println(result);
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("manual-locator-result.txt"),
                    result + "\n" + String.join("\n", SHOTS), StandardCharsets.UTF_8);
        } catch (Exception error) { throw new RuntimeException(error); }
        finally {
            if (resized) {
                mc.options.guiScale().set(originalGuiScale);
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), originalWidth, originalHeight);
                if (originalFullscreen) mc.getWindow().toggleFullScreen();
                mc.resizeDisplay();
            }
            mc.stop();
        }
    }
}
