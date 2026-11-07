package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.IndustryApi;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = IndustryApi.MOD_ID, value = Dist.CLIENT)
public final class MeteringPumpScreen extends AbstractContainerScreen<MeteringPumpMenu> {
    private static final int INK = 0xffe7eceb, MUTED = 0xffa5b5bf, COPPER = 0xffb9805d,
            STEEL = 0xff26323c, RECESS = 0xff172129, EDGE = 0xff485b65, WARNING = 0xfff1b66a;
    private static final int[] PATINA = {0xffd29562, 0xffa78b5f, 0xff558c79, 0xff397b70};
    private final ItemStack sealIcon = new ItemStack(IndustryContent.item("vulcanized_seal"));
    private final ItemStack headIcon = new ItemStack(IndustryContent.item("precision_pump_head"));
    private boolean maintenance;
    private Button doseMode, autoMode, maintenanceTab, dose, bench, seal, repair;

    public MeteringPumpScreen(MeteringPumpMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 244;
        imageHeight = 224;
    }

    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(WorkshopContent.PUMP_MENU.get(), MeteringPumpScreen::new);
    }

    @Override protected void init() {
        super.init();
        doseMode = button(8, 23, 71, label("dose_tab"), () -> !menu.automatic(), () -> selectMode(false));
        autoMode = button(82, 23, 76, label("auto_tab"), menu::automatic, () -> selectMode(true));
        maintenanceTab = button(168, 23, 68, label("maintenance"), () -> maintenance, () -> {
            maintenance = !maintenance;
            syncButtons();
        });
        dose = button(83, 86, 74, label("change_dose"), () -> false, () -> send(MeteringPumpMenu.CYCLE_DOSE));
        bench = button(83, 103, 74, label("open_bench"), () -> false, () -> send(MeteringPumpMenu.OPEN_BENCH));
        seal = button(34, 93, 78, label("install_seal"), () -> false, () -> send(MeteringPumpMenu.INSTALL_SEAL));
        repair = button(154, 93, 76, label("replace_head"), () -> false, () -> send(MeteringPumpMenu.REPLACE_HEAD));
        seal.setTooltip(Tooltip.create(label("seal_help")));
        repair.setTooltip(Tooltip.create(label("repair_help")));
        syncButtons();
    }

    private void selectMode(boolean automatic) {
        maintenance = false;
        if (automatic != menu.automatic()) send(automatic ? MeteringPumpMenu.SET_AUTO_MODE : MeteringPumpMenu.SET_DOSE_MODE);
        syncButtons();
    }

    private void send(int action) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    @Override protected void containerTick() {
        super.containerTick();
        syncButtons();
    }

    private void syncButtons() {
        menu.showVessels(!maintenance);
        dose.visible = !maintenance && !menu.automatic();
        bench.visible = !maintenance;
        bench.active = menu.benchConnected();
        seal.visible = repair.visible = maintenance;
        seal.active = !menu.sealed() && menu.hasPart("vulcanized_seal");
        repair.active = menu.wear() > 0 && menu.hasPart("precision_pump_head");
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        box(g, -2, -2, imageWidth + 4, imageHeight + 4, 0xd0091016);
        box(g, 0, 0, imageWidth, imageHeight, COPPER);
        box(g, 1, 1, imageWidth - 2, imageHeight - 2, STEEL);
        box(g, 2, 2, imageWidth - 4, 18, RECESS);
        line(g, 9, 7, title, INK, 111);
        line(g, 124, 7, label(menu.signaled() ? "signal_on" : "signal_off"), menu.signaled() ? WARNING : MUTED, 47);
        right(g, 233, 7, Component.literal(menu.energy() + " / " + menu.capacity() + " FE"), INK);
        box(g, 7, 40, 230, 79, RECESS);
        if (maintenance) renderMaintenance(g);
        else renderProcess(g);
        renderStatus(g);
        line(g, 42, 136, playerInventoryTitle, MUTED, 150);
        for (int i = 2; i < menu.slots.size(); i++) slot(g, menu.getSlot(i).x, menu.getSlot(i).y);
        for (int x : new int[]{3, 238}) for (int y : new int[]{3, 218}) {
            box(g, x, y, 3, 3, EDGE);
            box(g, x, y, 1, 1, COPPER);
        }
    }

    private void renderProcess(GuiGraphics g) {
        boolean automatic = menu.automatic();
        card(g, 8, 41, 69, 77);
        card(g, 80, 41, 80, 77);
        card(g, 163, 41, 73, 77);
        if (automatic) {
            center(g, 26, 46, label("acid_input"), MUTED, 29);
            center(g, 57, 46, label("alkali_input"), MUTED, 29);
        } else {
            center(g, 42, 46, label("input"), MUTED, 60);
        }
        center(g, 120, 46, label(automatic ? "reaction" : "dose_label"), MUTED, 73);
        center(g, 182, 46, label(automatic ? "product" : "vessel"), MUTED, 34);
        center(g, 216, 46, Component.literal("FE"), MUTED, 25);
        drawGauge(g, automatic ? 19 : 36, menu.gauge(MeteringPumpMenu.STOCK));
        if (automatic) drawGauge(g, 50, menu.gauge(MeteringPumpMenu.REAGENT));
        drawGauge(g, 175, menu.gauge(MeteringPumpMenu.OUTPUT));
        center(g, 42, 109, Component.literal("mB"), MUTED, 30);
        center(g, 182, 109, Component.literal("mB"), MUTED, 30);
        bar(g, 212, 61, 8, 34, menu.energy(), menu.capacity(), WARNING);
        center(g, 216, 99, Component.literal(Integer.toString(menu.energy())), INK, 29);
        if (automatic) {
            slot(g, 102, 61);
            slot(g, 127, 61);
            box(g, 87, 83, 66, 4, 0xff10191e);
            int width = menu.duration() > 0 ? Math.clamp(menu.progress() * 66 / menu.duration(), 0, 66) : 0;
            box(g, 87, 83, width, 4, 0xffae8bd5);
            center(g, 120, 92, Component.literal(menu.progress() + " / " + menu.duration() + " t"), INK, 74);
        } else {
            slot(g, 102, 61);
            line(g, 121, 65, Component.literal(menu.dose() + " mB"), INK, 36);
        }
        // Short copper connections show flow without taking space from the controls.
        for (int x : new int[]{73, 157}) {
            box(g, x, 73, 10, 2, COPPER);
            box(g, x + 7, 72, 2, 4, COPPER);
        }
    }

    private void drawGauge(GuiGraphics g, int x, MeteringPumpMenu.Gauge gauge) {
        bar(g, x, 61, 14, 34, gauge.amount(), gauge.capacity(), fluidColor(gauge));
        for (int y = 67; y <= 89; y += 7) box(g, x + 10, y, 3, 1, 0x88e7eceb);
        center(g, x + 7, 99, gauge.connected() ? Component.literal(Integer.toString(gauge.amount())) : label("disconnected_short"),
                gauge.connected() ? INK : MUTED, 30);
    }

    private void bar(GuiGraphics g, int x, int y, int width, int height, int amount, int capacity, int color) {
        box(g, x - 1, y - 1, width + 2, height + 2, EDGE);
        box(g, x, y, width, height, 0xff10191e);
        if (capacity > 0 && amount > 0) {
            int fill = Math.clamp(height * amount / capacity, 1, height);
            box(g, x, y + height - fill, width, fill, color);
            box(g, x, y + height - fill, width, 1, 0x88ffffff);
        }
    }

    private int fluidColor(MeteringPumpMenu.Gauge gauge) {
        var id = BuiltInRegistries.FLUID.getKey(gauge.fluid()).toString();
        return switch (id) {
            case "resonant_industry:acid_extract" -> 0xffd39845;
            case "resonant_industry:alkaline_extract" -> 0xff54a98b;
            case "resonant_industry:neutral_solution" -> 0xffa381d2;
            default -> 0xff5b9fca;
        };
    }

    private void renderMaintenance(GuiGraphics g) {
        card(g, 8, 41, 228, 77);
        line(g, 15, 47, label("condition", wear(), menu.wear()), INK, 126);
        right(g, 229, 47, sealStatus(), menu.sealed() ? 0xff8dc6b2 : WARNING);
        for (int stage = 0; stage < PATINA.length; stage++) {
            int x = 15 + stage * 23;
            box(g, x, 61, 20, 9, stage == menu.oxidation() ? WARNING : EDGE);
            box(g, x + 1, 62, 18, 7, PATINA[stage]);
        }
        Component info = menu.oxidation() == 3 ? label("service_needed") : menu.sealed() ? label("protected")
                : label("next_stage", clock(menu.seconds()), menu.rate());
        line(g, 114, 61, info, MUTED, 116);
        box(g, 15, 80, 214, 1, EDGE);
        g.renderItem(sealIcon, leftPos + 15, topPos + 92);
        g.renderItem(headIcon, leftPos + 135, topPos + 92);
    }

    private void renderStatus(GuiGraphics g) {
        box(g, 8, 122, 228, 12, 0xff121c23);
        box(g, 8, 122, 2, 12, menu.oxidation() == 3 ? WARNING : COPPER);
        line(g, 14, 124, sealStatus(), menu.sealed() ? 0xff8dc6b2 : WARNING, 67);
        box(g, 84, 123, 1, 10, EDGE);
        line(g, 91, 124, menu.statusText(true), menu.oxidation() == 3 ? WARNING : INK, 139);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
        if (over(mouseX, mouseY, 8, 122, 228, 12)) {
            g.renderComponentTooltip(font, List.of(menu.statusText(false), wear(), sealStatus()), mouseX, mouseY);
        } else if (maintenance) {
            if (over(mouseX, mouseY, 15, 91, 17, 18)) g.renderTooltip(font, sealIcon, mouseX, mouseY);
            if (over(mouseX, mouseY, 135, 91, 17, 18)) g.renderTooltip(font, headIcon, mouseX, mouseY);
        } else {
            gaugeTooltip(g, mouseX, mouseY, menu.automatic() ? 19 : 36, MeteringPumpMenu.STOCK, "stock");
            if (menu.automatic()) gaugeTooltip(g, mouseX, mouseY, 50, MeteringPumpMenu.REAGENT, "reagent");
            gaugeTooltip(g, mouseX, mouseY, 175, MeteringPumpMenu.OUTPUT, menu.automatic() ? "product" : "vessel");
        }
    }

    private void gaugeTooltip(GuiGraphics g, int mx, int my, int x, int index, String role) {
        if (!over(mx, my, x - 3, 58, 20, 50)) return;
        var gauge = menu.gauge(index);
        if (!gauge.connected()) {
            g.renderComponentTooltip(font, List.of(label(role), label("disconnected")), mx, my);
            return;
        }
        var fluid = gauge.fluid() == Fluids.EMPTY ? label("empty")
                : Component.translatable(gauge.fluid().getFluidType().getDescriptionId());
        g.renderComponentTooltip(font, List.of(label(role), fluid,
                Component.literal(gauge.amount() + " / " + gauge.capacity() + " mB")), mx, my);
    }

    private boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + y && my < topPos + y + h;
    }

    private void card(GuiGraphics g, int x, int y, int w, int h) {
        box(g, x, y, w, h, EDGE);
        box(g, x + 1, y + 1, w - 2, h - 2, RECESS);
    }

    private void slot(GuiGraphics g, int x, int y) {
        box(g, x - 1, y - 1, 18, 18, EDGE);
        box(g, x, y, 16, 16, 0xff151e25);
    }

    private void box(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + h, color);
    }

    private void line(GuiGraphics g, int x, int y, Component text, int color, int width) {
        g.drawString(font, font.plainSubstrByWidth(text.getString(), width), leftPos + x, topPos + y, color, false);
    }

    private void center(GuiGraphics g, int x, int y, Component text, int color, int width) {
        String clipped = font.plainSubstrByWidth(text.getString(), width);
        g.drawString(font, clipped, leftPos + x - font.width(clipped) / 2, topPos + y, color, false);
    }

    private void right(GuiGraphics g, int x, int y, Component text, int color) {
        g.drawString(font, text, leftPos + x - font.width(text), topPos + y, color, false);
    }

    private Component wear() { return Component.translatable("workshop.resonant_industry.wear." + menu.oxidation()); }
    private Component sealStatus() { return Component.translatable("workshop.resonant_industry.sealed." + menu.sealed()); }
    private static String clock(int seconds) { return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
    private static Component label(String key, Object... args) { return Component.translatable("workshop.resonant_industry.gui." + key, args); }

    private Button button(int x, int y, int width, Component text, BooleanSupplier selected, Runnable action) {
        return addRenderableWidget(new Button(Button.builder(text, b -> action.run()).bounds(leftPos + x, topPos + y, width, 14)) {
            @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
                int color = selected.getAsBoolean() ? COPPER : EDGE;
                if (active && isHoveredOrFocused()) color = WARNING;
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);
                g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, active ? STEEL : RECESS);
                center(g, getX() - leftPos + getWidth() / 2, getY() - topPos + 3, getMessage(), active ? INK : MUTED, getWidth() - 6);
            }
        });
    }
}
