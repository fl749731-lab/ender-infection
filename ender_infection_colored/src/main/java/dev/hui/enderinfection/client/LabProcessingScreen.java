package dev.hui.enderinfection.client;

import dev.hui.enderinfection.processing.LabProcessingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Deliberately plain, readable native UI; actual slots/progress are server synchronized. */
public final class LabProcessingScreen extends AbstractContainerScreen<LabProcessingMenu> {
    public LabProcessingScreen(LabProcessingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 176; imageHeight = 191; inventoryLabelY = 97;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x-1,y-1,x+177,y+192,0xff506774); graphics.fill(x,y,x+176,y+191,0xffe4ecec);
        graphics.fill(x+7,y+23,x+169,y+27,menu.powered()?0xff3f9d79:0xffa86a50);
        for (var slot : menu.slots) { graphics.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff677b82); graphics.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xffc3d0d1); }
        graphics.fill(x+108,y+47,x+126,y+55,0xff9aaeb0);
        int progress = menu.duration() <= 0 ? 0 : Math.min(18, menu.progress()*18/menu.duration());
        graphics.fill(x+108,y+47,x+108+progress,y+55,0xff42aa92);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font,title,8,7,0xff223c47,false);
        label(graphics,52,Component.translatable("gui.ender_infection.processing.input"));
        label(graphics,88,Component.translatable("gui.ender_infection.processing.reagent"));
        label(graphics,142,Component.translatable("gui.ender_infection.processing.output"));
        String message = !menu.powered()?"needs_power":menu.status()==2?"output_full":menu.status()==3?"running":"insert_sample";
        graphics.drawString(font,Component.translatable("gui.ender_infection.processing."+message),8,71,0xff36515b,false);
        if (menu.duration()>0) graphics.drawString(font,menu.progress()+" / "+menu.duration(),8,84,0xff36515b,false);
        graphics.drawString(font,playerInventoryTitle,8,inventoryLabelY,0xff36515b,false);
    }
    private void label(GuiGraphics graphics,int center,Component text) {
        graphics.drawString(font,text,center-font.width(text)/2,32,0xff36515b,false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); super.render(graphics,mouseX,mouseY,partialTick); renderTooltip(graphics,mouseX,mouseY);
    }
}
