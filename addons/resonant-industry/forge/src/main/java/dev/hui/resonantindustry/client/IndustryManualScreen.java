package dev.hui.resonantindustry.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.hui.resonantindustry.IndustryContent;
import dev.hui.resonantindustry.api.IndustryApi;
import dev.hui.resonantindustry.api.MachineDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public final class IndustryManualScreen extends Screen {
    private static final int PANEL_WIDTH = 390, PANEL_HEIGHT = 222;
    private static final float DEFAULT_YAW = 145;
    private static final String[] PAGES={"intro","generator","processor","centrifuge","transport","assembly","processes","labware","chemistry","experiments","filtration","distillation"};
    private static int rememberedPage;
    private static int rememberedLayer = -1;
    private int page = rememberedPage, layer = rememberedLayer, scroll, maxScroll;
    private float yaw = DEFAULT_YAW, pitch = 25, zoom = 1, uiScale = 1;
    private boolean contents, instructions;
    private Button layerButton, previousLayer, resetView, details;
    private final List<Button> chapters = new ArrayList<>();
    private int left, top;

    public IndustryManualScreen() { super(Component.translatable("item.resonant_industry.manual")); }
    @Override public boolean isPauseScreen() { return false; }
    private MachineDefinition machine() { return IndustryApi.machines().get(IndustryApi.MOD_ID + ":" + PAGES[page]); }

    @Override protected void init() {
        uiScale = Math.min(1, Math.min((width - 4f) / PANEL_WIDTH, (height - 4f) / PANEL_HEIGHT));
        uiScale = Math.max(.1f, uiScale);
        left = Mth.floor((width / uiScale - PANEL_WIDTH) / 2);
        top = Mth.floor((height / uiScale - PANEL_HEIGHT) / 2);
        button(8, 197, 28, Component.literal("<"), () -> showPage(Math.floorMod(page - 1, PAGES.length)));
        button(354, 197, 28, Component.literal(">"), () -> showPage((page + 1) % PAGES.length));
        button(294, 6, 84, label("contents"), () -> { contents = !contents; syncButtons(); });
        button(205, 197, 85, Component.translatable("gui.done"), this::onClose);
        previousLayer = button(12, 34, 24, Component.literal("<"), () -> changeLayer(-1));
        layerButton = button(40, 34, 126, label("all_layers"), () -> changeLayer(1));
        resetView = button(12, 169, 59, label("reset_view"), () -> { yaw = DEFAULT_YAW; pitch = 25; zoom = 1; });
        details = button(182, 33, 196, label("instructions"), () -> { instructions = !instructions; scroll = 0; syncButtons(); });
        chapters.clear();
        for (int index = 0; index < PAGES.length; index++) {
            final int target = index;
            var title = Component.translatable("guide.resonant_industry." + PAGES[index] + ".title");
            chapters.add(button(12 + (index / 8) * 186, 35 + (index % 8) * 19, 180, title, () -> showPage(target)));
        }
        syncButtons();
    }

    private Button button(int x, int y, int w, Component text, Runnable action) {
        return addRenderableWidget(new Button(left + x, top + y, w, 17, text, b -> action.run(), narration -> narration.get()) {
            @Override public void renderString(GuiGraphics g, Font buttonFont, int color) {
                // Vanilla scrolling labels use an unscaled scissor; keep labels inside the button instead.
                String visibleText = buttonFont.plainSubstrByWidth(getMessage().getString(), getWidth() - 4);
                g.drawCenteredString(buttonFont, visibleText, getX() + getWidth() / 2,
                        getY() + (getHeight() - 9) / 2 + 1, color);
            }
        });
    }

    private void showPage(int target) {
        page = target;
        layer = -1;
        scroll = 0;
        contents = false;
        instructions = false;
        syncButtons();
    }

    private void changeLayer(int direction) {
        var machine = machine();
        if (machine != null) layer = Math.floorMod(layer + 1 + direction, machine.height() + 1) - 1;
        syncButtons();
    }

    private void syncButtons() {
        var machine = machine();
        boolean structure = !contents && machine != null;
        layerButton.visible = previousLayer.visible = resetView.visible = details.visible = structure;
        if (machine != null) {
            layer = Mth.clamp(layer, -1, machine.height() - 1);
            layerButton.setMessage(layer < 0 ? label("all_layers") : label("structure_layer", layer + 1, machine.height()));
        }
        details.setMessage(label(instructions ? "materials" : "instructions"));
        chapters.forEach(button -> button.visible = contents);
    }

    @Override public void removed() {
        rememberedPage = page;
        rememberedLayer = layer;
        super.removed();
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        int logicalMouseX = Mth.floor(mx / uiScale), logicalMouseY = Mth.floor(my / uiScale);
        Component materialTooltip = null;
        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, uiScale);
        try {
            g.fill(left - 1, top - 1, left + 391, top + 223, 0xff9270ae);
            g.fill(left, top, left + 390, top + 222, 0xff17252e);
            Component title = contents ? label("contents") : Component.translatable("guide.resonant_industry." + PAGES[page] + ".title");
            g.drawString(font, font.plainSubstrByWidth(title.getString(), 274), left + 12, top + 10, 0xffe1cdf2, false);
            maxScroll = 0;
            if (!contents) {
                var machine = machine();
                if (machine != null) {
                    renderStructure(g, machine);
                    if (instructions) renderText(g, 182, 58, 196, 128);
                    else materialTooltip = renderMaterials(g, machine, logicalMouseX, logicalMouseY);
                } else if (List.of("labware", "chemistry", "experiments", "filtration", "distillation").contains(PAGES[page])) {
                    renderBenchMap(g);
                    renderText(g, 12, 103, 366, 82);
                } else renderText(g, 12, 34, 366, 151);
            }
            g.drawString(font, (page + 1) + " / " + PAGES.length, left + 48, top + 202, 0xffa5becb, false);
            if (maxScroll > 0) g.drawString(font, label("scroll"), left + 122, top + 202, 0xffa5becb, false);
            // Screen.render draws the background again on 1.21.1; render only the widgets here.
            for (var renderable : renderables) renderable.render(g, logicalMouseX, logicalMouseY, partial);
            for (var child : children()) {
                if (child instanceof Button button && button.visible && button.isHovered()
                        && font.width(button.getMessage()) > button.getWidth() - 4) materialTooltip = button.getMessage();
            }
        } finally {
            g.pose().popPose();
        }
        if (materialTooltip != null) g.renderTooltip(font, materialTooltip, mx, my);
    }

    private void renderText(GuiGraphics g, int x, int y, int w, int h) {
        var lines = font.split(Component.translatable("guide.resonant_industry." + PAGES[page] + ".body"), w - 8);
        int visible = h / 12;
        maxScroll = Math.max(0, lines.size() - visible);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++)
            g.drawString(font, lines.get(i), left + x, top + y + (i - scroll) * 12, 0xffc4d8df, false);
        if (maxScroll > 0) {
            g.fill(left + x + w - 3, top + y, left + x + w - 1, top + y + h, 0xff354653);
            int thumbY = top + y + scroll * (h - 14) / maxScroll;
            g.fill(left + x + w - 3, thumbY, left + x + w - 1, thumbY + 14, 0xffb796d4);
        }
    }

    private void renderStructure(GuiGraphics g, MachineDefinition machine) {
        g.fill(left + 8, top + 31, left + 174, top + 190, 0xff101d26);
        g.flush();
        // GuiGraphics scissor coordinates do not inherit the pose scale.
        g.enableScissor(Mth.floor((left + 10) * uiScale), Mth.floor((top + 54) * uiScale),
                Mth.ceil((left + 172) * uiScale), Mth.ceil((top + 165) * uiScale));
        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();
        var pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(left + 91, top + 110, 150);
            float scale = Math.min(27, 106f / (machine.height() + machine.length() * .55f)) * zoom;
            // Match vanilla item rendering: the GUI projection already reverses screen Y.
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(-.5, -(machine.height() - 2) / 2.0, -machine.length() / 2.0);
            var buffers = minecraft.renderBuffers().bufferSource();
            for (var entry : machine.cells().entrySet()) {
                var cell = entry.getKey();
                if (entry.getValue().equals("air") || layer >= 0 && cell.y() != layer - 1) continue;
                pose.pushPose();
                pose.translate(cell.x(), cell.y(), cell.z());
                minecraft.getBlockRenderer().renderSingleBlock(IndustryContent.block(entry.getValue()).defaultBlockState(),
                        pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            buffers.endBatch();
        } finally {
            pose.popPose();
            g.disableScissor();
            Lighting.setupFor3DItems();
        }
        g.drawString(font, label("rotate_zoom"), left + 76, top + 174, 0xffa5becb, false);
    }

    private Component renderMaterials(GuiGraphics g, MachineDefinition machine, int mx, int my) {
        var counts = new TreeMap<String, Integer>();
        machine.cells().values().stream().filter(role -> !role.equals("air")).forEach(role -> counts.merge(role, 1, Integer::sum));
        int row = 0;
        Component hovered = null;
        for (var entry : counts.entrySet()) {
            int y = top + 57 + row++ * 14;
            var stack = new ItemStack(IndustryContent.block(entry.getKey()));
            g.pose().pushPose();
            g.pose().translate(left + 183, y - 1, 0);
            g.pose().scale(.75f, .75f, .75f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
            var text = Component.translatable("block.resonant_industry." + entry.getKey()).append(" ×" + entry.getValue());
            g.drawString(font, font.plainSubstrByWidth(text.getString(), 177), left + 200, y + 1, 0xffc4d8df, false);
            if (mx >= left + 182 && mx < left + 378 && my >= y && my < y + 14) hovered = text;
        }
        return hovered;
    }

    private void renderBenchMap(GuiGraphics g) {
        boolean filtration = PAGES[page].equals("filtration");
        for (int position = 0; position < 6; position++) {
            int slot = 5 - position;
            int x = left + 13 + (position % 3) * 70, y = top + 35 + (position / 3) * 21;
            int color = slot == 0 ? 0xff826b9d : slot == 1 ? 0xff477f87 : 0xff354653;
            g.fill(x, y, x + 66, y + 18, color);
            String role = slot == 0 ? "sample" : slot == 1 ? (filtration ? "receiver" : "reagent") : "spare";
            g.drawString(font, label("bench." + role, slot + 1), x + 3, y + 5, 0xffedf4f6, false);
        }
        g.drawString(font, label("bench.front"), left + 19, top + 82, 0xffa5becb, false);
        int y = top + 36;
        for (var line : font.split(label("bench.help"), 147)) {
            g.drawString(font, line, left + 231, y, 0xffc4d8df, false);
            y += 12;
        }
    }

    @Override public void mouseMoved(double mx, double my) {
        super.mouseMoved(mx / uiScale, my / uiScale);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(mx / uiScale, my / uiScale, button);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        return super.mouseReleased(mx / uiScale, my / uiScale, button);
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        mx /= uiScale;
        my /= uiScale;
        dx /= uiScale;
        dy /= uiScale;
        if (button == 0 && overStructure(mx, my)) {
            yaw += (float) dx * 1.5f;
            pitch = Mth.clamp(pitch + (float) dy, 5, 80);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        mx /= uiScale;
        my /= uiScale;
        if (overStructure(mx, my)) {
            zoom = Mth.clamp(zoom + (float) delta * .1f, .6f, 1.5f);
            return true;
        }
        if (!contents && maxScroll > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta) * 2, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private boolean overStructure(double mx, double my) {
        return !contents && machine() != null && mx >= left + 10 && mx < left + 172 && my >= top + 54 && my < top + 165;
    }

    private static Component label(String key, Object... args) { return Component.translatable("guide.resonant_industry." + key, args); }
    public static int color(String role) {
        return switch (role) {
            case "air" -> 0xff21323b; case "controller" -> 0xff9569c2; case "rotor" -> 0xffc38248;
            case "window" -> 0xff4d9cba; case "energy_port" -> 0xffc9a247; case "fluid_input" -> 0xff357ab0;
            case "fluid_output" -> 0xff8557aa; case "item_input" -> 0xff569566; case "item_output" -> 0xffb46852;
            default -> 0xff657580;
        };
    }
    public static String symbol(String role) {
        return switch (role) {
            case "controller" -> "C"; case "air" -> "·"; case "rotor" -> "R"; case "energy_port" -> "E";
            case "fluid_input" -> "W"; case "fluid_output" -> "F"; case "item_input" -> "I";
            case "item_output" -> "O"; case "window" -> "G"; default -> "#";
        };
    }
}
