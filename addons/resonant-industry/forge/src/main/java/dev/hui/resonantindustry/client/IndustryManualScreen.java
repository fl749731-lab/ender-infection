package dev.hui.resonantindustry.client;

import dev.hui.resonantindustry.api.*;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class IndustryManualScreen extends Screen {
    private int page,layer;
    private static final String[] PAGES={"intro","generator","processor","centrifuge","transport","assembly","processes","labware","chemistry","experiments","filtration","distillation"};
    public IndustryManualScreen(){super(Component.translatable("item.resonant_industry.manual"));}
    @Override public boolean isPauseScreen(){return false;}
    private MachineDefinition machine(){return page>=1&&page<=3?IndustryApi.machines().get(IndustryApi.MOD_ID+":"+PAGES[page]):null;}
    @Override protected void init(){
        int x=(width-390)/2,y=(height-222)/2;
        addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.floorMod(page-1,PAGES.length);layer=0;}).bounds(x+8,y+197,28,18).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->{page=(page+1)%PAGES.length;layer=0;}).bounds(x+354,y+197,28,18).build());
        addRenderableWidget(Button.builder(Component.translatable("guide.resonant_industry.layer"),b->{var m=machine();if(m!=null)layer=(layer+1)%m.height();}).bounds(x+100,y+197,85,18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x+205,y+197,85,18).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);
        int x=(width-390)/2,y=(height-222)/2;g.fill(0,0,width,height,0xb0000000);g.fill(x-1,y-1,x+391,y+223,0xff9270ae);g.fill(x,y,x+390,y+222,0xff17252e);
        g.drawString(font,Component.translatable("guide.resonant_industry."+PAGES[page]+".title"),x+12,y+10,0xffe1cdf2,false);
        var machine=machine();int textX=x+12,textY=y+32,textWidth=366;
        if(machine!=null){
            textX=x+132;textWidth=246;g.drawString(font,(layer+1)+" / "+machine.height()+"  NORTH ↑",x+20,y+31,0xffb8dfed,false);
            for(var cell:machine.cells().entrySet())if(cell.getKey().y()==layer-1){
                int xx=x+24+(cell.getKey().x()+machine.width()/2)*23,yy=y+50+cell.getKey().z()*23;
                g.fill(xx,yy,xx+21,yy+21,color(cell.getValue()));String symbol=symbol(cell.getValue());g.drawString(font,symbol,xx+7,yy+6,0xfff5f8ff,false);
            }
            textY=y+37;
        }
        for(var line:font.split(Component.translatable("guide.resonant_industry."+PAGES[page]+".body"),textWidth)){g.drawString(font,line,textX,textY,0xffc4d8df,false);textY+=12;}
        if(machine!=null){
            var counts=new TreeMap<String,Integer>();machine.cells().values().stream().filter(r->!r.equals("air")).forEach(r->counts.merge(r,1,Integer::sum));
            int i=0;for(var e:counts.entrySet()){
                int xx=x+133+(i%2)*123,yy=y+99+(i/2)*15;
                g.fill(xx,yy,xx+10,yy+10,color(e.getKey()));g.drawString(font,Component.translatable("block.resonant_industry."+e.getKey()).append(" ×"+e.getValue()),xx+13,yy,0xffc4d8df,false);i++;
            }
        }
        g.drawString(font,(page+1)+" / "+PAGES.length,x+12,y+182,0xffa5becb,false);
        for(var renderable:renderables)renderable.render(g,mx,my,partial);
    }
    public static int color(String role){return switch(role){case "air"->0xff21323b;case "controller"->0xff9569c2;case "rotor"->0xffc38248;case "window"->0xff4d9cba;case "energy_port"->0xffc9a247;case "fluid_input"->0xff357ab0;case "fluid_output"->0xff8557aa;case "item_input"->0xff569566;case "item_output"->0xffb46852;default->0xff657580;};}
    public static String symbol(String role){return switch(role){case "controller"->"C";case "air"->"·";case "rotor"->"R";case "energy_port"->"E";case "fluid_input"->"W";case "fluid_output"->"F";case "item_input"->"I";case "item_output"->"O";case "window"->"G";default->"#";};}
}
