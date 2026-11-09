package dev.hui.resonantindustry.client;

import dev.hui.resonantindustry.IndustryContent;
import dev.hui.resonantindustry.IndustryNode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** One shared snapshot drives the HUD and world outlines, including the current layer. */
final class BuildGuide {
    record View(BlockPos controller, String machineId, int height, boolean formed, int layer,
                BuildProgress all, BuildProgress visible) {}
    private record Row(Component text, int color, String role, int count) {}
    private static final int MAX_WIDTH = 184;
    private static ClientLevel world;
    private static IndustryNode controller;
    private static BuildProgress progress;
    private static String machineId;
    private static Direction facing;
    private static long lastScan = Long.MIN_VALUE;

    static void clear(Level unloaded) {
        if (unloaded == world) reset();
    }

    private static void reset() {
        world = null;
        controller = null;
        progress = null;
        machineId = null;
        facing = null;
        lastScan = Long.MIN_VALUE;
    }

    static View current() {
        var mc = Minecraft.getInstance();
        if (mc.level != world) { reset(); world = mc.level; }
        if (world == null || mc.player == null) { reset(); return null; }
        boolean wrench = mc.player.getMainHandItem().is(IndustryContent.item("wrench"));
        if (mc.screen == null && wrench && mc.hitResult instanceof BlockHitResult hit) {
            var target = loadedNode(hit.getBlockPos());
            if (target != null && target.role().equals("controller") && target.definition() != null && target != controller) {
                controller = target;
                progress = null;
            }
        }
        if (controller == null) return null;
        var position = controller.getBlockPos();
        if (mc.player.distanceToSqr(Vec3.atCenterOf(position)) > 256 || loadedNode(position) != controller
                || controller.isRemoved() || !controller.role().equals("controller") || controller.definition() == null) {
            controller = null;
            progress = null;
            return null;
        }
        if (mc.screen != null || mc.options.hideGui || controller.formed() && !wrench) return null;
        long tick = world.getGameTime();
        if (progress == null || tick < lastScan || tick - lastScan >= 4
                || !controller.machineId().equals(machineId) || controller.facing() != facing) {
            progress = BuildProgress.scan(controller);
            lastScan = tick;
            machineId = controller.machineId();
            facing = controller.facing();
        }
        int height = controller.definition().height();
        int layer = mc.player.isShiftKeyDown() ? (int) Math.floorMod(tick / 30, height) : -1;
        return new View(position.immutable(), machineId, height, controller.formed(), layer, progress, progress.forLayer(layer));
    }

    private static IndustryNode loadedNode(BlockPos position) {
        var chunk = world.getChunkSource().getChunk(position.getX() >> 4, position.getZ() >> 4, false);
        return chunk != null && chunk.getBlockEntity(position) instanceof IndustryNode node ? node : null;
    }

    static void render(GuiGraphics g) {
        var view = current();
        if (view == null) return;
        var mc = Minecraft.getInstance();
        var font = mc.font;
        int width = Math.max(96, Math.min(MAX_WIDTH, mc.getWindow().getGuiScaledWidth() / 2 - 16));
        int content = width - 16;
        var rows = new ArrayList<Row>();
        add(rows, view.layer() < 0 ? text("all_layers") : text("layer", view.layer() + 1, view.height(),
                view.visible().placed(), view.visible().total()), 0xffa5becb);
        if (!view.visible().missing().isEmpty()) {
            add(rows, text(view.layer() < 0 ? "missing_all" : "missing_layer"), 0xffffcf89);
            view.visible().missing().forEach((role, count) -> rows.add(new Row(
                    Component.translatable("block.resonant_industry." + role), 0xffdce8ec, role, count)));
        } else if (view.layer() >= 0 && view.visible().complete() && !view.all().complete()) {
            add(rows, text("layer_complete"), 0xff81d4c6);
        }
        if (view.all().wrongBlocks() > 0) add(rows, text("wrong", view.all().wrongBlocks()), 0xffffa279);
        if (view.all().blockedAir() > 0) add(rows, text("clear", view.all().blockedAir()), 0xffffa279);
        if (view.all().unknown() > 0) add(rows, text("unknown", view.all().unknown()), 0xffb5bed0);
        if (view.formed()) add(rows, text("formed"), 0xff81d4c6);
        else if (view.all().complete()) add(rows, text("ready"), 0xff81d4c6);
        add(rows, text("hint"), 0xff8ea5b1);
        int rowHeight = 0;
        for (var row : rows) rowHeight += row.role() == null ? font.split(row.text(), content).size() * 11
                : Math.max(13, font.split(row.text(), content - 17 - font.width("×" + row.count())).size() * 11);
        int height = 43 + rowHeight;
        // Reserve the crosshair and bottom hotbar even on a 320x240 GUI.
        // Reflow the text before scaling, preserving crisp bitmap glyphs on small windows.
        float scale = Math.max(.1f, Math.min(1, Math.min((mc.getWindow().getGuiScaledWidth() / 2f - 16) / width,
                (mc.getWindow().getGuiScaledHeight() - 58f) / height)));
        g.pose().pushPose();
        try {
            g.pose().translate(8, 8, 0);
            g.pose().scale(scale, scale, 1);
            g.fill(0, 0, width, height, 0xe5101d26);
            g.fill(0, 0, width, 2, 0xffa585c2);
            var name = Component.translatable(controller.definition().nameKey());
            g.drawString(font, font.plainSubstrByWidth(text("title", name).getString(), content), 8, 7, 0xffe1cdf2, false);
            int percent = view.all().total() == 0 ? 0 : 100 * view.all().placed() / view.all().total();
            g.drawString(font, font.plainSubstrByWidth(text("placed", view.all().placed(), view.all().total(), percent).getString(), content),
                    8, 19, 0xffdce8ec, false);
            g.fill(8, 31, width - 8, 34, 0xff354653);
            g.fill(8, 31, 8 + Mth.floor(content * percent / 100f), 34, 0xff67c4bc);
            int y = 39;
            for (var row : rows) {
                if (row.role() == null) {
                    for (var line : font.split(row.text(), content)) {
                        g.drawString(font, line, 8, y, row.color(), false);
                        y += 11;
                    }
                } else {
                    g.pose().pushPose();
                    g.pose().translate(8, y, 0);
                    g.pose().scale(.625f, .625f, 1);
                    g.renderItem(new ItemStack(IndustryContent.block(row.role())), 0, 0);
                    g.pose().popPose();
                    String count = "×" + row.count();
                    int countX = width - 8 - font.width(count);
                    var lines = font.split(row.text(), countX - 25);
                    for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), 22, y + 1 + i * 11, row.color(), false);
                    g.drawString(font, count, countX, y + 1, 0xffffcf89, false);
                    y += Math.max(13, lines.size() * 11);
                }
            }
        } finally { g.pose().popPose(); }
    }

    private static void add(List<Row> rows, Component text, int color) { rows.add(new Row(text, color, null, 0)); }
    private static Component text(String key, Object... arguments) {
        return Component.translatable("hud.resonant_industry.build." + key, arguments);
    }
}
