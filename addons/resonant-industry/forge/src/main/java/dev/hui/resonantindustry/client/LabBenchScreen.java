package dev.hui.resonantindustry.client;

import dev.hui.resonantindustry.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class LabBenchScreen extends AbstractContainerScreen<LabBenchMenu> {
    private static final int SOURCE_COLOR = 0xff288db5;
    private static final int TARGET_COLOR = 0xffb6792f;
    private Button distill;

    public LabBenchScreen(LabBenchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = menu.station() ? 410 : 320;
        imageHeight = 237;
    }
    private void send(int id){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    @Override protected void init(){super.init();for(int i=0;i<6;i++){final int slot=i;int x=leftPos+14+(i%3)*94,y=topPos+55+(i/3)*50;addRenderableWidget(Button.builder(Component.translatable("lab.resonant_industry.from"),b->send(slot)).bounds(x,y,43,16).tooltip(Tooltip.create(slotText("selection_help"))).build());addRenderableWidget(Button.builder(Component.translatable("lab.resonant_industry.to"),b->send(slot+6)).bounds(x+44,y,43,16).tooltip(Tooltip.create(slotText("selection_help"))).build());}
        addRenderableWidget(Button.builder(Component.translatable("lab.resonant_industry.change_dose"),b->send(12)).bounds(leftPos+14,topPos+126,108,20).build());
        addRenderableWidget(Button.builder(Component.translatable("lab.resonant_industry.transfer"),b->send(13)).bounds(leftPos+132,topPos+126,174,20).build());
        if (menu.station()) {
            addRenderableWidget(Button.builder(label("start"), b -> send(14)).bounds(leftPos + 322, topPos + 198, 39, 20).build());
            addRenderableWidget(Button.builder(label("stop"), b -> send(15)).bounds(leftPos + 365, topPos + 198, 39, 20).build());
            distill = addRenderableWidget(Button.builder(label("distill"), b -> send(16))
                    .bounds(leftPos + 14, topPos + 157, 57, 20).tooltip(Tooltip.create(label("distill_help"))).build());
            distill.visible = menu.heater();
        }
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
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
        if (hoveredSlot != null && hoveredSlot.hasItem()) return;
        for (int i = 0; i < 6; i++) {
            if (isHovering(14 + i % 3 * 94, 23 + i / 3 * 50, 88, 30, mx, my)) {
                g.renderComponentTooltip(font, slotHelp(i), mx, my);
                return;
            }
        }
    }
}
