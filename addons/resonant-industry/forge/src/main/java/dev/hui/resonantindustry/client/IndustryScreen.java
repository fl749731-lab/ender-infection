package dev.hui.resonantindustry.client;
import dev.hui.resonantindustry.IndustryMenu;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.core.registries.BuiltInRegistries;

public final class IndustryScreen extends AbstractContainerScreen<IndustryMenu> {
    public IndustryScreen(IndustryMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=390;imageHeight=231;inventoryLabelX=30;inventoryLabelY=137;}
    private String number(int n){return String.format(java.util.Locale.ROOT,"%,d",n);}
    private Component liquid(int index){int id=menu.value(index);return id==0?Component.translatable("gui.resonant_industry.empty"):BuiltInRegistries.FLUID.getKey(BuiltInRegistries.FLUID.byId(id)).toString().equals("resonant_industry:nutrient_slurry")?Component.translatable("gui.resonant_industry.short_slurry"):Component.translatable(BuiltInRegistries.FLUID.byId(id).getFluidType().getDescriptionId());}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x-1,y-1,x+391,y+232,0xff926b9c);g.fill(x,y,x+390,y+231,0xff17252e);g.fill(x+5,y+5,x+385,y+19,0xff30414a);
        for(var slot:menu.slots){g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff72818b);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff263740);}
        g.fill(x+11,y+30,x+22,y+133,0xff090e14);int e=menu.capacity()==0?0:Math.min(101,(int)(menu.energy()*101L/menu.capacity()));g.fill(x+12,y+132-e,x+21,y+132,0xffcc9df1);
        if(menu.value(21)==0)for(int i=0;i<2;i++){int at=x+188+i*13;g.fill(at,y+30,at+10,y+108,0xff090e14);int cap=Math.max(1,menu.value(19+i)),amount=menu.value(6+i),height=Math.min(76,amount*76/cap);g.fill(at+1,y+107-height,at+9,y+107,i==0?0xff50a9d5:0xffa36acc);}
        g.fill(x+80,y+80,x+115,y+86,0xff3b4c55);int p=menu.duration()==0?0:Math.min(35,(int)(menu.progress()*35L/menu.duration()));g.fill(x+80,y+80,x+80+p,y+86,0xff55c9a0);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,9,8,0xffecf3f6,false);
        g.drawString(font,Component.translatable("gui.resonant_industry.stored",number(menu.energy()),number(menu.capacity())),30,25,0xffcfb0ed,false);
        int use=menu.value(16)|(menu.value(17)<<16);
        g.drawString(font,Component.translatable(menu.value(21)==1?"gui.resonant_industry.generation":"gui.resonant_industry.consumption",menu.value(21)==1?menu.value(15):use),30,37,0xffb6cbd5,false);
        g.drawString(font,Component.translatable("gui.resonant_industry.status."+menu.value(8)),30,49,0xffa9d7c6,false);
        g.drawString(font,Component.translatable("gui.resonant_industry.input"),41,63,0xffd7e2e8,false);g.drawString(font,Component.translatable("gui.resonant_industry.output"),127,63,0xffd7e2e8,false);
        Component progress=menu.value(21)==1?Component.translatable("gui.resonant_industry.fuel_time",String.format(java.util.Locale.ROOT,"%.1f",menu.burn()/20.0)):menu.duration()>0&&(menu.progress()>0||menu.value(8)==2||menu.value(8)==4||menu.value(8)==5)?Component.literal(menu.progress()*100/menu.duration()+"%  "+menu.progress()+" / "+menu.duration()+" t"):Component.translatable("gui.resonant_industry.no_recipe");
        g.drawString(font,progress,30,101,0xffd3e4e8,false);
        g.drawString(font,menu.value(21)==1?Component.translatable("gui.resonant_industry.idle_load"):Component.translatable("gui.resonant_industry.tank_in",liquid(11),number(menu.value(6)),number(menu.value(19))),30,113,0xff70bce6,false);
        g.drawString(font,menu.value(21)==1?Component.translatable("gui.resonant_industry.coal_budget"):Component.translatable("gui.resonant_industry.tank_out",liquid(18),number(menu.value(7)),number(menu.value(20))),30,125,0xffc396ec,false);
        g.drawString(font,playerInventoryTitle,30,inventoryLabelY,0xffb6cbd5,false);
        g.drawString(font,Component.translatable("gui.resonant_industry.extra_inputs"),234,29,0xffd7e2e8,false);
        g.drawString(font,Component.translatable("gui.resonant_industry.catalysts"),310,29,0xffdfbd79,false);
        g.drawString(font,Component.translatable("gui.resonant_industry.extra_outputs"),310,69,0xffd7e2e8,false);
        g.drawString(font,Component.translatable("gui.resonant_industry.fluid_inputs"),234,106,0xff70bce6,false);
        g.drawString(font,Component.translatable("gui.resonant_industry.fluid_outputs"),310,106,0xffc396ec,false);
        for(int channel=0;channel<4;channel++)for(int side=0;side<2;side++){
            int xx=234+side*76,yy=122+channel*22,index=22+channel*4+side*2;
            g.drawString(font,(side==0?"I":"O")+(channel+1)+" "+font.plainSubstrByWidth(liquid(index).getString(),48),xx,yy,0xffc4d8df,false);
            g.drawString(font,number(menu.value(index+1))+" mB",xx,yy+10,side==0?0xff70bce6:0xffc396ec,false);
        }
        g.drawString(font,Component.translatable("gui.resonant_industry.channel_hint"),234,216,0xff9db5c0,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        for(int channel=0;channel<4;channel++)for(int side=0;side<2;side++)if(mx>=leftPos+234+side*76&&mx<leftPos+306+side*76&&my>=topPos+122+channel*22&&my<topPos+143+channel*22){
            int index=22+channel*4+side*2;g.renderTooltip(font,liquid(index).copy().append(" "+number(menu.value(index+1))+" / "+number(menu.value(19))+" mB"),mx,my);
        }
    }
}
