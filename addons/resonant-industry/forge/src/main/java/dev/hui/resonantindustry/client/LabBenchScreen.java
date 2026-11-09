package dev.hui.resonantindustry.client;

import dev.hui.resonantindustry.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class LabBenchScreen extends AbstractContainerScreen<LabBenchMenu> {
    private static final int SOURCE_COLOR = 0xff288db5;
    private static final int TARGET_COLOR = 0xffb6792f;
    private Button distill;
    private float uiScale = 1;
    private int logicalWidth, logicalHeight;
    private final Map<Button, Component> buttonHelp = new LinkedHashMap<>();

    public LabBenchScreen(LabBenchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = menu.station() ? 410 : 320;
        imageHeight = 237;
    }
    private void send(int id){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    @Override protected void init() {
        uiScale = Math.max(.1f, Math.min(1, Math.min(width / (float) imageWidth, height / (float) imageHeight)));
        logicalWidth = Mth.ceil(width / uiScale);
        logicalHeight = Mth.ceil(height / uiScale);
        int screenWidth = width, screenHeight = height;
        try {
            width = logicalWidth;
            height = logicalHeight;
            super.init();
        } finally {
            width = screenWidth;
            height = screenHeight;
        }
        buttonHelp.clear();
        for (int i = 0; i < 6; i++) {
            final int slot = i;
            int x = 14 + i % 3 * 94, y = 55 + i / 3 * 50;
            button(x, y, 43, 16, Component.translatable("lab.resonant_industry.from"), slotText("selection_help"), () -> send(slot));
            button(x + 44, y, 43, 16, Component.translatable("lab.resonant_industry.to"), slotText("selection_help"), () -> send(slot + 6));
        }
        button(14, 126, 108, 20, Component.translatable("lab.resonant_industry.change_dose"), null, () -> send(12));
        button(132, 126, 174, 20, Component.translatable("lab.resonant_industry.transfer"), null, () -> send(13));
        if (menu.station()) {
            button(322, 198, 39, 20, label("start"), null, () -> send(14));
            button(365, 198, 39, 20, label("stop"), null, () -> send(15));
            distill = button(14, 157, 57, 20, label("distill"), label("distill_help"), () -> send(16));
            distill.visible = menu.heater();
        }
    }

    private Button button(int x, int y, int w, int h, Component title, Component help, Runnable action) {
        var button = addRenderableWidget(new Button(leftPos + x, topPos + y, w, h, title,
                b -> action.run(), narration -> narration.get()) {
            @Override public void renderString(GuiGraphics g, Font buttonFont, int color) {
                // Vanilla scrolling labels use scissors that do not inherit the pose scale.
                String text = buttonFont.plainSubstrByWidth(getMessage().getString(), getWidth() - 4);
                g.drawCenteredString(buttonFont, text, getX() + getWidth() / 2,
                        getY() + (getHeight() - 9) / 2 + 1, color);
            }
        });
        buttonHelp.put(button, help);
        return button;
    }
    @Override protected void containerTick() {
        super.containerTick();
        if (distill != null) distill.visible = menu.heater();
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffd9e2e4);
        g.fill(leftPos + 5, topPos + 23, leftPos + 315, topPos + 124, 0xffa6b9be);
        if (menu.station()) g.fill(leftPos + 318, topPos + 23, leftPos + 407, topPos + 224, 0xffa6b9be);
        for (var slot : menu.slots) {
            if (!slot.isActive()) continue;
            int x = leftPos + slot.x, y = topPos + slot.y;
            if (slot.index < 6) {
                if (menu.value(0) == slot.index) g.fill(x - 3, y - 3, x + 19, y + 19, SOURCE_COLOR);
                if (menu.value(1) == slot.index) g.fill(x - 2, y - 2, x + 18, y + 18, TARGET_COLOR);
            }
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xff53656e);
            g.fill(x, y, x + 16, y + 16, 0xffbac6c9);
        }
        for (int i = 0; i < 6; i++) {
            int x = leftPos + 14 + i % 3 * 94, y = topPos + 55 + i / 3 * 50;
            if (menu.value(0) == i) g.fill(x - 1, y - 1, x + 44, y + 17, SOURCE_COLOR);
            if (menu.value(1) == i) g.fill(x + 43, y - 1, x + 88, y + 17, TARGET_COLOR);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,8,6,0xff183e4f,false);for(int i=0;i<6;i++){int x=14+(i%3)*94,y=23+(i/3)*50;g.drawString(font,font.plainSubstrByWidth(slotName(i).getString(),88),x,y,0xff183e4f,false);var item=menu.slots.get(i).getItem();if(!item.isEmpty()){g.drawString(font,LabPlatform.data(item).getInt("LabAmount")+" mB",x+44,y+12,0xff16333d,false);g.drawString(font,"/ "+LabVesselItem.capacity(item),x+44,y+21,0xff365360,false);}}
        g.drawString(font,Component.translatable("lab.resonant_industry.dose",LabBenchEntity.DOSES[Math.max(0,Math.min(7,menu.value(2)))]),190,6,0xff143a50,false);
        int n=menu.value(3);if(n!=0)g.drawString(font,Component.translatable(n>0?"lab.resonant_industry.transferred":"lab.resonant_industry.blocked",n),14,228,n>0?0xff126447:0xff982f39,false);
        if (menu.station()) renderChemistry(g);
    }

    private static Component label(String key, Object... arguments) {
        return Component.translatable("chemistry.resonant_industry." + key, arguments);
    }

    private static Component slotText(String key) {
        return Component.translatable("lab.resonant_industry.slot." + key);
    }

    private Component slotName(int index) {
        String role = "vessel";
        if (menu.station()) {
            role = switch (index) {
                case 0 -> "sample";
                case 1 -> menu.kind() == Chemistry.Station.FILTER ? "receiver" : "reagent";
                default -> "spare";
            };
        }
        return Component.literal("#" + (index + 1) + " ").append(slotText(role));
    }

    private List<Component> slotHelp(int index) {
        return List.of(slotName(index), slotText("position." + index), slotText("selection_help"));
    }

    @Override protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot != null && hoveredSlot.index < 6) lines.addAll(slotHelp(hoveredSlot.index));
        return lines;
    }

    private void renderChemistry(GuiGraphics g) {
        int x = 323;
        int ink = 0xff16333d;
        g.drawString(font, label("sample"), x, 28, ink, false);
        boolean filter = menu.kind() == Chemistry.Station.FILTER;
        g.drawString(font, label(filter ? "receiver" : "reagent"), x, 40, ink, false);
        g.drawString(font, label(filter ? "paper" : "ingredient"), x, 56, ink, false);
        if (menu.heater()) g.drawString(font, label("fuel"), x + 27, 56, ink, false);
        g.drawString(font, label("product"), x + 54, 56, ink, false);

        int status = menu.chemistry(1);
        if (status >= 0 && status < ChemistryStation.Status.values().length) {
            var name = ChemistryStation.Status.values()[status].name().toLowerCase(java.util.Locale.ROOT);
            int y = 92;
            for (var line : font.split(label("status." + name), 80)) {
                g.drawString(font, line, x, y, ink, false);
                y += 10;
            }
        }
        int experiment = menu.chemistry(5) - 1;
        if (experiment >= 0 && experiment < Chemistry.Experiment.values().length) {
            int y = 115;
            for (var line : font.split(label("experiment." + Chemistry.Experiment.values()[experiment].id()), 80)) {
                g.drawString(font, line, x, y, ink, false);
                y += 10;
            }
        }
        int duration = menu.chemistry(4);
        int fill = duration == 0 ? 0 : Math.min(78, 78 * menu.chemistry(3) / duration);
        g.fill(x, 140, x + 78, 146, 0xff566971);
        g.fill(x, 140, x + fill, 146, 0xff44896e);
        if (menu.heater()) {
            g.drawString(font, label("temperature", menu.chemistry(2)), x, 153, ink, false);
            g.drawString(font, label("burn", menu.chemistry(6)), x, 166, ink, false);
        } else if (!filter) {
            g.drawString(font, label(menu.chemistry(7) > 0 ? "power_on" : "power_off"), x, 153, ink, false);
        }
        if (menu.chemistry(9) > 0) g.drawString(font, label("recovered", menu.chemistry(9)), x, 180, ink, false);
        else if (menu.chemistry(8) > 0) g.drawString(font, label("evaporated", menu.chemistry(8)), x, 180, ink, false);
        else if (menu.heater()) g.drawString(font, label(menu.chemistry(10) > 0 ? "condenser_linked" : "condenser_unlinked"), x, 180, ink, false);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        int logicalMouseX = Mth.floor(mx / uiScale), logicalMouseY = Mth.floor(my / uiScale);
        int screenWidth = width, screenHeight = height;
        g.pose().pushPose();
        try {
            width = logicalWidth;
            height = logicalHeight;
            g.pose().scale(uiScale, uiScale, uiScale);
            // The native container also renders carried items, touch dragging, quick-craft and snapback.
            // On 1.21.1 it draws its own background, using this temporary logical viewport.
            super.render(g, logicalMouseX, logicalMouseY, partial);
        } finally {
            g.pose().popPose();
            width = screenWidth;
            height = screenHeight;
        }
        // Tooltips must use the real viewport and mouse position, after leaving the scaled container.
        renderTooltip(g, mx, my);
        if (!menu.getCarried().isEmpty() || hoveredSlot != null && hoveredSlot.hasItem()) return;
        for (int i = 0; i < 6; i++) {
            if (isHovering(14 + i % 3 * 94, 23 + i / 3 * 50, 88, 30, logicalMouseX, logicalMouseY)) {
                g.renderComponentTooltip(font, slotHelp(i), mx, my);
                return;
            }
        }
        for (var entry : buttonHelp.entrySet()) {
            var button = entry.getKey();
            if (!button.visible || !button.isHovered()) continue;
            var help = entry.getValue();
            if (help != null) g.renderComponentTooltip(font, List.of(help), mx, my);
            else if (font.width(button.getMessage()) > button.getWidth() - 4)
                g.renderComponentTooltip(font, List.of(button.getMessage()), mx, my);
            return;
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
        return super.mouseDragged(mx / uiScale, my / uiScale, button, dx / uiScale, dy / uiScale);
    }

    // Panel-relative coordinates for native client acceptance and other client-side overlays.
    double screenX(double panelX) { return (leftPos + panelX) * uiScale; }
    double screenY(double panelY) { return (topPos + panelY) * uiScale; }
    float uiScale() { return uiScale; }
    int hoveredSlotIndex() { return hoveredSlot == null ? -1 : hoveredSlot.index; }
}
