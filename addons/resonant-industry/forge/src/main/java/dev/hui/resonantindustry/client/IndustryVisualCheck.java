package dev.hui.resonantindustry.client;

import dev.hui.resonantindustry.*;
import dev.hui.resonantindustry.api.*;
import dev.hui.resonantindustry.test.IndustryScene;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=IndustryApi.MOD_ID,value=Dist.CLIENT)
public final class IndustryVisualCheck {
    private static final String WORLD="ResonantIndustry-"+System.currentTimeMillis();
    private static IndustryScene scene;private static volatile boolean built,busy;private static boolean launched,finished,manualReopened,manualRotated;
    private static volatile String failure;private static volatile View view;
    private static BlockPos aim,missing;private static Vec3 eye,focus;private static int step,age,captureReadyAt;private static long began;
    private static final List<String> SHOTS=new ArrayList<>();
    private record View(boolean generator,boolean processor,boolean centrifuge,int progress,int output,int fluids,int channel){}
    private static boolean enabled(){return Boolean.getBoolean("resonant_industry.visualCheck");}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer p)||!p.server.getWorldData().getLevelName().equals(WORLD))return;
        try{
            var level=p.serverLevel();level.getDataStorage().computeIfAbsent(dev.hui.enderinfection.entity.WorldIntroduction.Data::load,()->dev.hui.enderinfection.entity.WorldIntroduction.Data.forWorldAge(200),"ender_infection_world_introduction").complete();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);level.setDayTime(6000);
            scene=new IndustryScene(level,new BlockPos(0,64,0));scene.build();missing=scene.origin.offset(-1,1,2);level.setBlock(missing,Blocks.AIR.defaultBlockState(),3);
            p.setGameMode(GameType.ADVENTURE);p.setNoGravity(true);p.getAbilities().invulnerable=true;p.getInventory().clearContent();
            p.getInventory().setItem(0,new ItemStack(IndustryContent.item("wrench")));p.getInventory().setItem(1,new ItemStack(Items.COAL));p.getInventory().setItem(2,new ItemStack(IndustryContent.item("ender_infection:mycelium_bundle")));p.getInventory().setItem(3,new ItemStack(Items.GLASS_BOTTLE));p.getInventory().setItem(4,new ItemStack(Items.WATER_BUCKET));p.getInventory().setItem(5,new ItemStack(IndustryContent.item("manual")));
            point(p,scene.origin,new Vec3(.5,64,-3));built=true;System.out.println("INDUSTRY_VISUAL_SCENE machines=3 direct_energy_injection=false");
        }catch(Exception e){failure=e.toString();}
    }
    private static void call(Minecraft mc,Consumer<ServerPlayer> work){if(busy)return;busy=true;var server=mc.getSingleplayerServer();var id=mc.player.getUUID();server.execute(()->{try{work.accept(server.getPlayerList().getPlayer(id));}catch(Exception e){failure=e.toString();}finally{busy=false;}});}
    private static float yaw(){var d=focus.subtract(eye.add(0,1.62,0));return (float)Math.toDegrees(Math.atan2(-d.x,d.z));}
    private static float pitch(){var d=focus.subtract(eye.add(0,1.62,0));return (float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));}
    private static void point(ServerPlayer p,BlockPos at,Vec3 stand){aim=at;eye=stand;focus=Vec3.atCenterOf(at).add(0,.15,0);p.teleportTo(p.serverLevel(),eye.x,eye.y,eye.z,yaw(),pitch());}
    private static void go(Minecraft mc,BlockPos at,Vec3 stand,int next){age=0;step=next;call(mc,p->point(p,at,stand));}
    private static void camera(Minecraft mc){if(eye==null)return;mc.player.setPos(eye);mc.player.setYRot(yaw());mc.player.setXRot(pitch());mc.player.setDeltaMovement(Vec3.ZERO);}
    private static boolean use(Minecraft mc){
        if(age<25||mc.screen!=null||busy)return false;var from=mc.player.getEyePosition(1);var hit=mc.level.clip(new ClipContext(from,from.add(focus.subtract(from).normalize().scale(4.8)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(aim))throw new IllegalStateException("Native ray missed "+aim+" hit "+hit.getBlockPos());mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);age=0;return true;
    }
    private static IndustryMenu menu(Minecraft mc){return mc.screen instanceof IndustryScreen&&mc.player.containerMenu instanceof IndustryMenu m&&m.slots.size()==IndustryMenu.SLOT_COUNT?m:null;}
    private static void move(Minecraft mc,int slot){var m=menu(mc);if(m==null)throw new IllegalStateException("Native menu missing");mc.gameMode.handleInventoryMouseClick(m.containerId,slot,0,ClickType.QUICK_MOVE,mc.player);age=0;}
    private static void shot(Minecraft mc,String name) throws Exception{if(SHOTS.contains(name))throw new IllegalStateException("Duplicate screenshot");var dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);try(var frame=Screenshot.takeScreenshot(mc.getMainRenderTarget())){frame.writeToFile(dir.resolve(name+".png"));}SHOTS.add(name);System.out.println("INDUSTRY_VISUAL_CAPTURE "+name);}
    static void openManualChapter(Minecraft mc,String chapter){
        mc.setScreen(new IndustryManualScreen());
        selectManualChapter(mc,chapter);
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),1,1);
    }
    private static void selectManualChapter(Minecraft mc,String chapter){
        manualButton(mc,Component.translatable("guide.resonant_industry.contents")).onPress();
        manualButton(mc,Component.translatable("guide.resonant_industry."+chapter+".title")).onPress();
    }
    private static Button manualButton(Minecraft mc,Component label){
        if(!(mc.screen instanceof IndustryManualScreen))throw new IllegalStateException("Manual screen missing");
        for(var child:mc.screen.children())
            if(child instanceof Button button&&button.visible&&button.active&&button.getMessage().getString().equals(label.getString()))return button;
        throw new IllegalStateException("Visible manual control missing: "+label.getString());
    }
    private static void finish(Minecraft mc,boolean pass){finished=true;var result=(pass?"INDUSTRY_VISUAL_PASS":"INDUSTRY_VISUAL_FAIL")+" step="+step+" shots="+SHOTS.size()+" "+failure;System.out.println(result);try{Files.writeString(mc.gameDirectory.toPath().resolve("industry-visual-result.txt"),result+"\n"+String.join("\n",SHOTS));}catch(Exception e){throw new RuntimeException(e);}mc.stop();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!enabled()||finished||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(began==0)began=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){launched=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;}
        if(failure!=null||System.currentTimeMillis()-began>480000){if(failure==null)failure="timeout";finish(mc,false);return;}if(!built||mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        camera(mc);age++;if(age%5==0&&!busy)call(mc,p->{var g=scene.generator();var b=scene.processor();var c=scene.centrifuge();if(g==null||b==null||c==null)throw new IllegalStateException("Acceptance fixture structure changed during client run");var port=IndustryNode.at(scene.level,scene.origin.offset(-11,0,4));view=new View(g.formed(),b.formed(),c.formed(),c.progress(),c.getItem(2).getCount(),c.getItem(3).getCount(),port.fluidChannel());});if(busy||view==null)return;
        try{switch(step){
            case 0->{if(age>70){shot(mc,"preview_missing");call(mc,p->p.serverLevel().setBlock(missing,IndustryContent.block("casing").defaultBlockState(),3));age=0;step=1;}}
            case 1->{if(use(mc))step=2;}
            case 2->{if(view.generator){go(mc,scene.origin.west(6),new Vec3(-5.5,64,-3),3);}}
            case 3->{if(use(mc))step=4;}
            case 4->{if(view.processor){go(mc,scene.origin.west(12),new Vec3(-11.5,64,-3),5);}}
            case 5->{if(use(mc))step=6;}
            case 6->{if(view.centrifuge){mc.player.getInventory().selected=6;go(mc,scene.origin,new Vec3(.5,64,-3),7);}}
            case 7->{if(use(mc))step=8;}
            case 8->{if(menu(mc)!=null&&age>15){move(mc,31);step=9;}}
            case 9->{var m=menu(mc);if(m!=null&&m.energy()>0&&m.burn()>0&&age>20){shot(mc,"generator_running");mc.player.closeContainer();go(mc,scene.origin.west(6),new Vec3(-5.5,64,-3),10);}}
            case 10->{if(use(mc))step=11;}
            case 11->{if(menu(mc)!=null&&age>15){move(mc,32);step=12;}}
            case 12->{if(menu(mc)!=null&&menu(mc).getSlot(0).hasItem()&&age>15){mc.player.closeContainer();mc.player.getInventory().selected=4;go(mc,scene.origin.offset(-5,0,2),new Vec3(-2.5,64,2.5),13);}}
            case 13->{if(use(mc))step=14;}
            case 14->{if(mc.player.getMainHandItem().is(Items.BUCKET)&&age>10){mc.player.getInventory().selected=6;go(mc,scene.origin.west(6),new Vec3(-5.5,64,-3),15);}}
            case 15->{if(use(mc))step=16;}
            case 16->{var m=menu(mc);if(m!=null&&m.progress()>20&&m.value(8)==5&&age>15){shot(mc,"processor_running");mc.player.closeContainer();go(mc,scene.origin.west(12),new Vec3(-11.5,64,-3),17);}}
            case 17->{if(use(mc))step=18;}
            case 18->{if(menu(mc)!=null&&age>15){move(mc,33);step=19;}}
            case 19->{var m=menu(mc);if(m!=null&&m.progress()>20&&m.value(8)==5&&age>15){shot(mc,"centrifuge_running");age=0;step=20;}}
            case 20->{var m=menu(mc);if(m!=null&&m.getSlot(1).hasItem()&&m.getSlot(2).hasItem()){if(captureReadyAt==0)captureReadyAt=age;if(age-captureReadyAt>=10){shot(mc,"centrifuge_complete");captureReadyAt=0;move(mc,1);step=21;}}}
            case 21->{var m=menu(mc);if(m!=null&&!m.getSlot(1).hasItem()&&age>15){move(mc,2);step=22;}}
            case 22->{if(menu(mc)!=null&&!menu(mc).getSlot(2).hasItem()&&age>15){int a=0,b=0;for(int i=0;i<36;i++){var s=mc.player.getInventory().getItem(i);if(s.is(IndustryContent.item("ender_infection:separated_reagent")))a+=s.getCount();if(s.is(IndustryContent.item("fuel_residue")))b+=s.getCount();}if(a!=1||b!=1)throw new IllegalStateException("Native output count "+a+" / "+b);mc.player.closeContainer();mc.options.hideGui=true;go(mc,scene.origin.west(6).above(),new Vec3(3,68,-7),23);}}
            case 23->{if(age>45){shot(mc,"production_line");go(mc,scene.origin.offset(0,0,1),new Vec3(3.8,65,-3.8),26);}}
            case 26->{if(age>35){shot(mc,"generator_detail");go(mc,scene.origin.offset(-6,1,1),new Vec3(-2.5,66,-3.2),27);}}
            case 27->{if(age>35){shot(mc,"processor_detail");go(mc,scene.origin.offset(-12,0,2),new Vec3(-8.5,66,-3.5),28);}}
            case 28->{if(age>35){shot(mc,"centrifuge_detail");go(mc,scene.origin.offset(-1,0,1),new Vec3(-3.3,64,-2),29);}}
            case 29->{if(age>35){shot(mc,"cable_detail");mc.options.hideGui=false;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.getInventory().selected=0;go(mc,scene.origin,new Vec3(.5,64,-3),45);}}
            case 45->{
                if(age>35&&mc.screen==null){
                    if(!mc.player.getMainHandItem().is(IndustryContent.item("wrench"))||mc.options.hideGui||mc.options.getCameraType()!=CameraType.FIRST_PERSON)throw new IllegalStateException("First-person wrench capture lost its visible held item");
                    var heldModel=mc.getItemRenderer().getModel(mc.player.getMainHandItem(),mc.level,mc.player,0);
                    int modelQuads=heldModel.getQuads(null,null,net.minecraft.util.RandomSource.create(42)).size();
                    for(var face:Direction.values())modelQuads+=heldModel.getQuads(null,face,net.minecraft.util.RandomSource.create(42)).size();
                    if(modelQuads==0)throw new IllegalStateException("Held wrench baked model contains no geometry");
                    System.out.println("INDUSTRY_WRENCH_MODEL_PASS quads="+modelQuads);
                    shot(mc,"wrench_first_person");
                    openManualChapter(mc,"intro");
                    age=0;
                    step=24;
                }
            }
            case 24->{
                if(age>20&&mc.screen instanceof IndustryManualScreen){
                    shot(mc,"handbook");
                    selectManualChapter(mc,"processor");
                    age=0;
                    step=46;
                }
            }
            case 46->{
                if(age>20&&mc.screen instanceof IndustryManualScreen manual){
                    var allLayers=manualButton(mc,Component.translatable("guide.resonant_industry.all_layers"));
                    if(!manualRotated){
                        if(!manual.mouseDragged(allLayers.getX()+51,allLayers.getY()+76,0,-8,3))throw new IllegalStateException("Manual structure did not accept native drag");
                        manualRotated=true;
                        age=0;
                    }else{
                        System.out.println("INDUSTRY_MANUAL_ROTATION_PASS chapter=processor full_structure=true");
                        shot(mc,"manual_structure_3d");
                        allLayers.onPress();
                        age=0;
                        step=25;
                    }
                }
            }
            case 25->{
                if(age>20&&mc.screen instanceof IndustryManualScreen){
                    int height=IndustryApi.machines().get(IndustryApi.PROCESSOR).height();
                    manualButton(mc,Component.translatable("guide.resonant_industry.structure_layer",1,height));
                    if(!manualReopened){
                        mc.setScreen(null);
                        mc.setScreen(new IndustryManualScreen());
                        manualReopened=true;
                        age=0;
                    }else{
                        System.out.println("INDUSTRY_MANUAL_MEMORY_PASS chapter=processor layer=1");
                        shot(mc,"layer_blueprint");
                        if(SHOTS.size()!=14)throw new IllegalStateException("Wrong handbook frame count");
                        mc.setScreen(null);
                        call(mc,p->{p.getInventory().clearContent();p.getInventory().setItem(1,new ItemStack(Items.GLASS_BOTTLE,2));p.getInventory().setItem(2,new ItemStack(Items.PAPER));p.getInventory().setItem(3,new ItemStack(Items.CHARCOAL));p.getInventory().setItem(4,new ItemStack(Items.WATER_BUCKET));p.getInventory().setItem(5,new ItemStack(IndustryContent.item("slurry_canister")));point(p,scene.origin.west(12),new Vec3(-11.5,64,-3));});
                        mc.player.getInventory().selected=6;
                        age=0;
                        step=30;
                    }
                }
            }
            case 30->{if(use(mc))step=31;}
            case 31->{if(menu(mc)!=null&&age>15){move(mc,31);step=32;}}
            case 32->{if(menu(mc)!=null&&menu(mc).getSlot(0).getItem().getCount()==2&&age>15){move(mc,32);step=33;}}
            case 33->{if(menu(mc)!=null&&menu(mc).getSlot(39).hasItem()&&age>15){move(mc,33);step=34;}}
            case 34->{if(menu(mc)!=null&&menu(mc).getSlot(44).hasItem()&&age>15){mc.player.closeContainer();mc.player.getInventory().selected=5;go(mc,scene.origin.offset(-11,0,4),new Vec3(-10.5,64,7),35);}}
            case 35->{if(use(mc))step=36;}
            case 36->{if(mc.player.getMainHandItem().is(Items.BUCKET)&&age>10){mc.player.getInventory().selected=6;go(mc,scene.origin.offset(-11,0,4),new Vec3(-10.5,64,7),37);}}
            case 37->{if(age>25){mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));if(use(mc))step=38;}}
            case 38->{if(view.channel()==1&&age>10){mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));mc.player.getInventory().selected=4;go(mc,scene.origin.offset(-11,0,4),new Vec3(-10.5,64,7),39);}}
            case 39->{if(use(mc))step=40;}
            case 40->{if(mc.player.getMainHandItem().is(Items.BUCKET)&&age>10){mc.player.getInventory().selected=6;go(mc,scene.origin.west(12),new Vec3(-11.5,64,-3),41);}}
            case 41->{if(use(mc))step=42;}
            case 42->{var m=menu(mc);if(m!=null&&m.progress()>20&&m.value(8)==5&&age>15){if(m.value(27)!=1000||!m.getSlot(44).hasItem()||m.value(16)!=32)throw new IllegalStateException("Compound menu did not synchronize second fluid, catalyst or real consumption");shot(mc,"compound_running");age=0;step=43;}}
            case 43->{var m=menu(mc);if(m!=null&&m.getSlot(1).getItem().getCount()==2){if(captureReadyAt==0)captureReadyAt=age;if(age-captureReadyAt>=10){if(m.getSlot(2).getItem().getCount()!=1||m.getSlot(39).hasItem()||!m.getSlot(44).getItem().is(Items.CHARCOAL)||m.value(25)!=500||m.value(29)!=250)throw new IllegalStateException("Native compound output/catalyst/fluid mismatch");shot(mc,"compound_complete");captureReadyAt=0;mc.player.closeContainer();openManualChapter(mc,"processes");age=0;step=44;}}}
            case 44->{if(age>20&&mc.screen instanceof IndustryManualScreen){shot(mc,"compound_guide");if(SHOTS.size()!=17)throw new IllegalStateException("Wrong extended frame count");finish(mc,true);}}
            default->throw new IllegalStateException("Unknown step");
        }}catch(Exception e){failure=e.toString();finish(mc,false);}
    }
}
