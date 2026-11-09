package dev.hui.resonantindustry.client;

import dev.hui.resonantindustry.api.IndustryApi;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Two separate client launches exercise real screen state without creating a world. */
@Mod.EventBusSubscriber(modid=IndustryApi.MOD_ID,value=Dist.CLIENT)
public final class ManualBookmarkVisualCheck {
    private static final String STAGE = System.getProperty("resonant_industry.manualBookmarkCheck", "");
    private static final ManualBookmark STRUCTURE = new ManualBookmark("processor", 1, true, 0, 169, 34, 1.2f);
    private static final ManualBookmark READING = new ManualBookmark("distillation", -1, false, 2, 145, 25, 1);
    private static boolean launched, finished;
    private static int step, age;
    private static long began;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (STAGE.isEmpty() || finished || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (began == 0) began = System.currentTimeMillis();
        if (System.currentTimeMillis() - began > 180_000) { finish(mc, "timeout"); return; }
        try {
            require(STAGE.equals("seed") || STAGE.equals("resume"), "Unknown bookmark check stage");
            if (!launched) {
                if (mc.getOverlay() != null || mc.screen == null || !(mc.screen instanceof TitleScreen
                        || mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))) return;
                launched = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                if (STAGE.equals("seed")) checkInvalidFile(mc.gameDirectory.toPath());
                var manual = new IndustryManualScreen();
                mc.setScreen(manual);
                require(manual.width >= 390 && manual.height >= 222, "Bookmark check needs its normal-size fixture window");
                if (STAGE.equals("seed")) {
                    chapter(manual, "processor");
                    button(manual, "reset_view").onPress();
                    button(manual, "all_layers").onPress();
                    button(manual, "structure_layer", 1, 4).onPress();
                    button(manual, "instructions").onPress();
                    var layer = button(manual, "structure_layer", 2, 4);
                    double x = layer.getX() + 51, y = layer.getY() + 76;
                    require(manual.mouseDragged(x, y, 0, 16, 9), "Structure drag was rejected");
                    require(scroll(manual, x, y, 2), "Structure zoom was rejected");
                    manual.mouseReleased(x, y, 0);
                }
                age = 0;
                return;
            }
            if (mc.getOverlay() != null || ++age < 20) return;
            require(mc.screen instanceof IndustryManualScreen, "The manual screen disappeared");
            var manual = (IndustryManualScreen) mc.screen;
            if (step == 0) {
                require(manual.bookmark().equals(STRUCTURE), "Structure bookmark mismatch: " + manual.bookmark());
                shot(mc, "manual-bookmark-structure-" + STAGE);
                mc.setScreen(new TitleScreen());
                require(ManualBookmark.load(mc.gameDirectory.toPath()).equals(STRUCTURE), "Structure bookmark was not written to disk");
                manual = new IndustryManualScreen(readingDirectory(mc));
                mc.setScreen(manual);
                if (STAGE.equals("seed")) {
                    chapter(manual, "processor");
                    button(manual, "reset_view").onPress();
                    chapter(manual, "distillation");
                }
                step = 1;
                age = 0;
            } else if (step == 1 && STAGE.equals("seed")) {
                shot(mc, "manual-distillation-layout");
                require(scroll(manual, manual.width / 2.0, manual.height / 2.0, -1), "Reading page was not scrollable");
                step = 2;
                age = 0;
            } else {
                require(manual.bookmark().equals(READING), "Reading bookmark mismatch: " + manual.bookmark());
                shot(mc, "manual-bookmark-reading-" + STAGE);
                mc.setScreen(new TitleScreen());
                require(ManualBookmark.load(readingDirectory(mc)).equals(READING), "Reading bookmark was not written to disk");
                finish(mc, null);
            }
        } catch (Exception error) { finish(mc, error.toString()); }
    }

    private static Path readingDirectory(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("reading-bookmark-fixture");
    }

    private static void chapter(IndustryManualScreen manual, String chapter) {
        button(manual, "contents").onPress();
        button(manual, chapter + ".title").onPress();
    }

    private static Button button(IndustryManualScreen manual, String key, Object... arguments) {
        String label = Component.translatable("guide.resonant_industry." + key, arguments).getString();
        for (var child : manual.children())
            if (child instanceof Button button && button.visible && button.active && button.getMessage().getString().equals(label)) return button;
        throw new IllegalStateException("Manual control missing: " + label);
    }

    private static boolean scroll(IndustryManualScreen manual, double x, double y, double amount) {
        return manual.mouseScrolled(x, y, amount);
    }

    private static void checkInvalidFile(Path gameDirectory) throws Exception {
        Path invalid = gameDirectory.resolve("invalid-bookmark-fixture");
        Files.createDirectories(ManualBookmark.file(invalid).getParent());
        Files.writeString(ManualBookmark.file(invalid), "version=1\nchapter=" + '\\' + "uZZZZ\n", StandardCharsets.UTF_8);
        require(ManualBookmark.load(invalid).equals(ManualBookmark.DEFAULT), "Malformed bookmark did not fall back safely");
        Path unknown = gameDirectory.resolve("unknown-bookmark-fixture");
        require(new ManualBookmark("removed_chapter", 99, true, 99999, 200, 80, 1.5f).save(unknown), "Could not create unknown-chapter fixture");
        require(new IndustryManualScreen(unknown).bookmark().equals(ManualBookmark.DEFAULT), "Unknown chapter did not reset its state");
        System.out.println("MANUAL_BOOKMARK_INVALID_FILE_PASS");
    }

    private static void shot(Minecraft mc, String name) throws Exception {
        Path directory = mc.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(directory);
        try (var frame = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { frame.writeToFile(directory.resolve(name + ".png")); }
        System.out.println("MANUAL_BOOKMARK_CAPTURE " + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void finish(Minecraft mc, String failure) {
        finished = true;
        String result = (failure == null ? "MANUAL_BOOKMARK_PASS" : "MANUAL_BOOKMARK_FAIL") + " stage=" + STAGE
                + " structure=processor:2 reading=distillation:2" + (failure == null ? "" : " " + failure);
        System.out.println(result);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("manual-bookmark-result.txt"), result + "\n", StandardCharsets.UTF_8); }
        catch (Exception error) { throw new RuntimeException(error); }
        mc.stop();
    }
}
