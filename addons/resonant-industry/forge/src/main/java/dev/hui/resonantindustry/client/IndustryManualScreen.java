package dev.hui.resonantindustry.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.hui.resonantindustry.IndustryContent;
import dev.hui.resonantindustry.api.IndustryApi;
import dev.hui.resonantindustry.api.MachineDefinition;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

public final class IndustryManualScreen extends Screen {
    private static final int PANEL_WIDTH = 390, PANEL_HEIGHT = 222;
    private static final float DEFAULT_YAW = 145;
    private static final String[] PAGES={"intro","generator","processor","centrifuge","transport","assembly","processes","labware","chemistry","experiments","filtration","distillation"};
    private static final Map<Path, ManualBookmark> BOOKMARKS = new HashMap<>();
    private static final Set<Path> UNSAVED_BOOKMARKS = new HashSet<>();
    private final Path bookmarkDirectory;
    private int page, layer = -1, scroll, maxScroll;
    private float yaw = DEFAULT_YAW, pitch = 25, zoom = 1, uiScale = 1;
    private boolean contents, instructions;
    private Button layerButton, previousLayer, resetView, details;
    private final List<Button> chapters = new ArrayList<>();
    private final List<Button> materialButtons = new ArrayList<>();
    private List<String> materialRoles = List.of();
    private String selectedMaterial;
    private int left, top;

    public IndustryManualScreen() { this(Minecraft.getInstance().gameDirectory.toPath()); }

    IndustryManualScreen(Path gameDirectory) {
        super(Component.translatable("item.resonant_industry.manual"));
        bookmarkDirectory = gameDirectory.toAbsolutePath().normalize();
        ManualBookmark saved = BOOKMARKS.computeIfAbsent(bookmarkDirectory, ManualBookmark::load);
        int restoredPage = List.of(PAGES).indexOf(saved.chapter());
        if (restoredPage < 0) saved = ManualBookmark.DEFAULT;
        page = Math.max(0, restoredPage);
        layer = saved.layer();
        instructions = saved.instructions();
        scroll = saved.scroll();
        yaw = saved.yaw();
        pitch = saved.pitch();
        zoom = saved.zoom();
    }
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
        resetView = button(12, 169, 59, label("reset_view"), () -> { yaw = DEFAULT_YAW; pitch = 25; zoom = 1; remember(); });
        details = button(182, 33, 196, label("instructions"), () -> { instructions = !instructions; scroll = 0; syncButtons(); remember(); });
        chapters.clear();
        for (int index = 0; index < PAGES.length; index++) {
            final int target = index;
            var title = Component.translatable("guide.resonant_industry." + PAGES[index] + ".title");
            chapters.add(button(12 + (index / 8) * 186, 35 + (index % 8) * 19, 180, title, () -> showPage(target)));
        }
        materialButtons.clear();
        // All nine non-air roles in MachineDefinition; real buttons also support keyboard navigation.
        for (int index = 0; index < 9; index++) {
            final int row = index;
            materialButtons.add(addRenderableWidget(new Button(left + 182, top + 68 + row * 14, 196, 14,
                    Component.empty(), ignored -> selectMaterial(row), narration -> narration.get()) {
                @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
                    renderMaterialRow(g, row, isHoveredOrFocused());
                }
            }));
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
        selectedMaterial = null;
        syncButtons();
        remember();
    }

    private void changeLayer(int direction) {
        var machine = machine();
        if (machine != null) layer = Math.floorMod(layer + 1 + direction, machine.height() + 1) - 1;
        syncButtons();
        remember();
    }

    private void syncButtons() {
        var machine = machine();
        boolean structure = !contents && machine != null;
        layerButton.visible = previousLayer.visible = resetView.visible = details.visible = structure;
        if (machine != null) {
            layer = Mth.clamp(layer, -1, machine.height() - 1);
            layerButton.setMessage(layer < 0 ? label("all_layers") : label("structure_layer", layer + 1, machine.height()));
        } else {
            layer = -1;
            instructions = false;
        }
        details.setMessage(label(instructions ? "materials" : "instructions"));
        chapters.forEach(button -> button.visible = contents);
        var counts = materialCounts();
        materialRoles = List.copyOf(counts.keySet());
        for (int row = 0; row < materialButtons.size(); row++) {
            var button = materialButtons.get(row);
            button.visible = structure && !instructions && row < materialRoles.size();
            if (row < materialRoles.size()) {
                String role = materialRoles.get(row);
                var count = counts.get(role);
                button.setMessage(materialName(role).append(". ").append(label("material_count_detail", count.visible(), count.total()))
                        .append(". ").append(label("material_select")));
            }
        }
    }

    record MaterialCount(int visible, int total) {}

    SortedMap<String, MaterialCount> materialCounts() {
        var counts = new TreeMap<String, MaterialCount>();
        var machine = machine();
        if (machine != null) machine.cells().forEach((cell, role) -> {
            if (role.equals("air")) return;
            var old = counts.getOrDefault(role, new MaterialCount(0, 0));
            counts.put(role, new MaterialCount(old.visible() + (layer < 0 || cell.y() == layer - 1 ? 1 : 0), old.total() + 1));
        });
        return counts;
    }

    String selectedMaterial() { return selectedMaterial; }
    double screenX(double panelX) { return (left + panelX) * uiScale; }
    double screenY(double panelY) { return (top + panelY) * uiScale; }

    private void selectMaterial(int row) {
        String role = materialRoles.get(row);
        selectedMaterial = role.equals(selectedMaterial) ? null : role;
    }

    @Override public void removed() {
        remember();
        super.removed();
    }

    ManualBookmark bookmark() {
        return new ManualBookmark(PAGES[page], layer, instructions, scroll, yaw, pitch, zoom);
    }

    private void remember() {
        ManualBookmark current = bookmark();
        boolean changed = !current.equals(BOOKMARKS.put(bookmarkDirectory, current));
        if (!changed && !UNSAVED_BOOKMARKS.contains(bookmarkDirectory)) return;
        if (current.save(bookmarkDirectory)) UNSAVED_BOOKMARKS.remove(bookmarkDirectory);
        else UNSAVED_BOOKMARKS.add(bookmarkDirectory);
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
                    else materialTooltip = renderMaterials(g, logicalMouseX, logicalMouseY);
                } else if (PAGES[page].equals("distillation")) {
                    renderDistillationMap(g);
                    renderText(g, 12, 113, 366, 72);
                } else if (List.of("labware", "chemistry", "experiments", "filtration").contains(PAGES[page])) {
                    renderBenchMap(g);
                    renderText(g, 12, 103, 366, 82);
                } else renderText(g, 12, 34, 366, 151);
            }
            g.drawString(font, (page + 1) + " / " + PAGES.length, left + 48, top + 202, 0xffa5becb, false);
            if (maxScroll > 0) g.drawString(font, label("scroll"), left + 122, top + 202, 0xffa5becb, false);
            // Screen.render draws the background again on 1.21.1; render only the widgets here.
            for (var renderable : renderables) renderable.render(g, logicalMouseX, logicalMouseY, partial);
            for (var child : children()) {
                if (child instanceof Button button && button.visible && button.isHovered() && !materialButtons.contains(button)
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
        Vector3f front = new Vector3f(), marker = new Vector3f();
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
            if (selectedMaterial != null) renderMaterialOutlines(g, machine, buffers, false);
            for (var entry : machine.cells().entrySet()) {
                var cell = entry.getKey();
                if (entry.getValue().equals("air") || layer >= 0 && cell.y() != layer - 1) continue;
                pose.pushPose();
                pose.translate(cell.x(), cell.y(), cell.z());
                if (selectedMaterial == null || selectedMaterial.equals(entry.getValue())) {
                    minecraft.getBlockRenderer().renderSingleBlock(IndustryContent.block(entry.getValue()).defaultBlockState(),
                            pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }
                pose.popPose();
            }
            buffers.endBatch();
            if (selectedMaterial != null) renderMaterialOutlines(g, machine, buffers, true);
            // Project the controller side with the same model transform, including layer and zoom.
            float frontY = layer < 0 ? .5f : layer - .5f;
            pose.last().pose().transformPosition(front.set(.5f, frontY, -.03f));
            pose.last().pose().transformPosition(marker.set(.5f, frontY, -1.1f));
            front.div(uiScale);
            marker.div(uiScale);
        } finally {
            pose.popPose();
            g.disableScissor();
            Lighting.setupFor3DItems();
        }
        pose.pushPose();
        pose.translate(0, 0, 400);
        try {
            renderFrontMarker(g, front, marker);
            if (selectedMaterial != null && materialCounts().get(selectedMaterial).visible() == 0) {
                g.fill(left + 12, top + 56, left + 170, top + 69, 0xe5101d26);
                g.drawCenteredString(font, label("material_none_here"), left + 91, top + 58, 0xffffd694);
            }
        } finally {
            g.flush();
            pose.popPose();
        }
        g.drawString(font, label("rotate_zoom"), left + 76, top + 174, 0xffa5becb, false);
    }

    private void renderMaterialOutlines(GuiGraphics g, MachineDefinition machine, MultiBufferSource.BufferSource buffers, boolean selected) {
        var lines = buffers.getBuffer(ManualRenderTypes.lines());
        for (var entry : machine.cells().entrySet()) {
            var cell = entry.getKey();
            if (entry.getValue().equals("air") || layer >= 0 && cell.y() != layer - 1
                    || selectedMaterial.equals(entry.getValue()) != selected) continue;
            var box = new AABB(cell.x(), cell.y(), cell.z(), cell.x() + 1, cell.y() + 1, cell.z() + 1);
            LevelRenderer.renderLineBox(g.pose(), lines, box.inflate(selected ? .007 : -.015),
                    selected ? 1f : .32f, selected ? .78f : .47f, selected ? .38f : .56f, selected ? 1f : .45f);
        }
        buffers.endBatch(ManualRenderTypes.lines());
    }

    private void renderFrontMarker(GuiGraphics g, Vector3f front, Vector3f marker) {
        if (front.x < left + 11 || front.x > left + 171 || front.y < top + 56 || front.y > top + 162) return;
        int anchorX = Math.round(front.x), anchorY = Math.round(front.y);
        int tipX = Mth.clamp(Math.round(marker.x), left + 15, left + 167);
        int tipY = Mth.clamp(Math.round(marker.y), top + 65, top + 152);
        int distance = Math.max(Math.abs(tipX - anchorX), Math.abs(tipY - anchorY));
        for (int i = 0; i <= distance; i++) {
            float t = distance == 0 ? 0 : (float) i / distance;
            int x = Math.round(Mth.lerp(t, anchorX, tipX)), y = Math.round(Mth.lerp(t, anchorY, tipY));
            g.fill(x, y, x + 1, y + 1, 0xffffc86b);
        }
        g.fill(anchorX - 2, anchorY - 2, anchorX + 3, anchorY + 3, 0xffffc86b);
        var text = label("structure_front");
        int textWidth = font.width(text);
        int x = Mth.clamp(tipX - textWidth / 2, left + 13, left + 169 - textWidth);
        int y = tipY + 4;
        g.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0xe5101d26);
        g.drawString(font, text, x, y, 0xffffd694, false);
    }

    private Component renderMaterials(GuiGraphics g, int mx, int my) {
        var heading = label(layer < 0 ? "material_count_all" : "material_count_layer");
        int headingX = left + 377 - font.width(heading);
        g.drawString(font, font.plainSubstrByWidth(label("material_hint").getString(), headingX - left - 187),
                left + 183, top + 55, 0xffa5becb, false);
        g.drawString(font, heading, headingX, top + 55, 0xffa5becb, false);
        int row = (my - top - 68) / 14;
        if (mx < left + 182 || mx >= left + 378 || my < top + 68 || row >= materialRoles.size()) return null;
        String role = materialRoles.get(row);
        var count = materialCounts().get(role);
        var tooltip = materialName(role).append("\n").append(label("material_count_detail", count.visible(), count.total()))
                .append("\n").append(label("material_select"));
        if (count.visible() == 0) tooltip.append("\n").append(label("material_other_layer"));
        return tooltip;
    }

    private void renderMaterialRow(GuiGraphics g, int row, boolean hovered) {
        String role = materialRoles.get(row);
        var count = materialCounts().get(role);
        boolean selected = role.equals(selectedMaterial);
        int y = top + 68 + row * 14;
        if (selected || hovered) g.fill(left + 182, y, left + 378, y + 14, selected ? 0xff4a4251 : 0xff283d4a);
        if (selected) g.fill(left + 182, y, left + 184, y + 14, 0xffffc86b);
        g.pose().pushPose();
        g.pose().translate(left + 185, y + 1, 0);
        g.pose().scale(.75f, .75f, .75f);
        g.renderItem(new ItemStack(IndustryContent.block(role)), 0, 0);
        g.pose().popPose();
        String countText = layer < 0 ? "×" + count.total() : count.visible() + " / " + count.total();
        int countX = left + 376 - font.width(countText);
        int color = selected ? 0xffffd694 : count.visible() == 0 ? 0xff84949e : 0xffc4d8df;
        g.drawString(font, font.plainSubstrByWidth(materialName(role).getString(), countX - left - 205), left + 200, y + 3, color, false);
        g.drawString(font, countText, countX, y + 3, color, false);
    }

    private static net.minecraft.network.chat.MutableComponent materialName(String role) {
        return Component.translatable("block.resonant_industry." + role);
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

    private void renderDistillationMap(GuiGraphics g) {
        renderDistillationDevice(g, left + 13, false);
        renderDistillationDevice(g, left + 217, true);
        g.drawCenteredString(font, label("distillation.touching"), left + 195, top + 43, 0xffa5becb);
        g.fill(left + 178, top + 66, left + 212, top + 68, 0xffa6cdd7);
        for (int i = 0; i < 5; i++) g.fill(left + 207 + i, top + 62 + i,
                left + 208 + i, top + 72 - i, 0xffa6cdd7);
        g.drawCenteredString(font, label("distillation.front"), left + 195, top + 93, 0xffa5becb);
    }

    private void renderDistillationDevice(GuiGraphics g, int x, boolean receiver) {
        int y = top + 34;
        g.fill(x, y, x + 160, y + 53, 0xff101d26);
        g.drawString(font, label(receiver ? "distillation.condenser" : "distillation.heater"), x + 6, y + 5, 0xffe1cdf2, false);
        for (int position = 0; position < 6; position++) {
            int slot = 5 - position;
            int slotX = x + 6 + position % 3 * 21, slotY = y + 19 + position / 3 * 15;
            int color = slot == 0 ? (receiver ? 0xff477f87 : 0xff826b9d) : 0xff354653;
            g.fill(slotX, slotY, slotX + 19, slotY + 13, color);
            g.drawCenteredString(font, Integer.toString(slot + 1), slotX + 9, slotY + 2, 0xffedf4f6);
        }
        g.drawString(font, label(receiver ? "bench.receiver" : "bench.sample", 1), x + 78, y + 25, 0xffedf4f6, false);
        g.drawString(font, label("distillation.near_right"), x + 78, y + 38, 0xffa5becb, false);
    }

    @Override public void mouseMoved(double mx, double my) {
        super.mouseMoved(mx / uiScale, my / uiScale);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(mx / uiScale, my / uiScale, button);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        remember();
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
            remember();
            return true;
        }
        if (!contents && maxScroll > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta) * 2, 0, maxScroll);
            remember();
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
