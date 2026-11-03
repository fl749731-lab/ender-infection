package dev.hui.enderinfection.client;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.design.*;
import dev.hui.enderinfection.block.ExpansionNodeEntity;
import dev.hui.enderinfection.worldgen.ResearchLabPlacement;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;

/** Opt-in visual regression run. Creates only its own isolated, disposable test world. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class LabVisualCheck {
    private static final String WORLD="EnderLabVisual";
    private static final BlockPos ORIGIN=new BlockPos(0,70,0);
    private record Shot(String name,double x,double y,double z,float yaw,float pitch,boolean open){}
    private static final Shot[] SHOTS={
        new Shot("01_entrance_closed",16.5,91.4,16.5,0,7,false),
        new Shot("02_entrance_open",16.5,91.4,16.5,0,7,true),
        new Shot("03_control_room",11.3,87.2,17.3,128,10,false),
        new Shot("04_ward",19.8,77.2,1.7,-55,12,false),
        new Shot("05_culture",20.0,82.2,17.0,-122,8,false),
        new Shot("06_reactor",11.4,72.2,26.6,130,12,false)
    };
    private static boolean launched,finished;
    private static volatile boolean prepared;
    private static volatile int age;
    private static int shot;
    private static final java.util.Set<String> CAPTURED=new java.util.LinkedHashSet<>();
    private static boolean enabled(){return Boolean.getBoolean("ender_infection.labVisualCheck");}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player)||!player.server.getWorldData().getLevelName().equals(WORLD))return;
        var level=player.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,player.server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,player.server);level.setDayTime(6000);
        if(!ResearchLabPlacement.place(level,ORIGIN))throw new IllegalStateException("Visual test lab did not place");
        for(BlockPos pos:BlockPos.betweenClosed(ORIGIN,ORIGIN.offset(31,25,27)))if(level.getBlockEntity(pos) instanceof ExpansionNodeEntity node){var tag=node.saveWithFullMetadata();tag.putBoolean("Wild",false);node.load(tag);node.inhibit(level,1200);}
        player.setGameMode(GameType.CREATIVE);player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();
        System.out.println("LAB_ENTRY_STATE "+level.getBlockState(ORIGIN.offset(16,21,23))+" CONTROL_FLOOR "+level.getBlockState(ORIGIN.offset(6,15,14)));position(player,SHOTS[0]);prepared=true;age=0;
    }
    private static void position(ServerPlayer player,Shot s){
        var level=player.serverLevel();var p=ORIGIN.offset(16,21,23);var state=level.getBlockState(p);level.setBlock(p,state.setValue(LabFixtureBlock.OPEN,s.open),3);
        player.teleportTo(level,s.x,s.y,s.z,s.yaw,s.pitch);age=0;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!enabled()||finished||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(!launched&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            launched=true;mc.options.hideGui=true;
            if(Files.exists(mc.gameDirectory.toPath().resolve("saves/"+WORLD+"/level.dat")))mc.createWorldOpenFlows().loadLevel(mc.screen,WORLD);
            else mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            return;
        }
        if(prepared&&mc.player!=null&&shot<SHOTS.length){
            var camera=SHOTS[shot];mc.setCameraEntity(mc.player);mc.player.setPos(camera.x,camera.y,camera.z);mc.player.setYRot(camera.yaw);mc.player.setXRot(camera.pitch);mc.player.yRotO=camera.yaw;mc.player.xRotO=camera.pitch;mc.player.setYHeadRot(camera.yaw);mc.player.setDeltaMovement(0,0,0);mc.player.getAbilities().flying=true;
        }
        if(!prepared||mc.level==null||mc.player==null||mc.getOverlay()!=null||mc.screen!=null||++age<100)return;
        try{
            Path folder=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(folder);
            try(var frame=Screenshot.takeScreenshot(mc.getMainRenderTarget())){frame.writeToFile(folder.resolve(SHOTS[shot].name+".png"));}
            CAPTURED.add(SHOTS[shot].name);System.out.println("LAB_VISUAL_CAPTURE "+SHOTS[shot].name+" player="+mc.player.position()+" camera="+mc.gameRenderer.getMainCamera().getPosition()+" yaw="+mc.gameRenderer.getMainCamera().getYRot()+" pitch="+mc.gameRenderer.getMainCamera().getXRot());
            if(++shot==SHOTS.length){
                finished=true;String result="LAB_VISUAL_CHECK_PASS native_world=true screenshots="+CAPTURED.size();Files.writeString(mc.gameDirectory.toPath().resolve("lab-visual-result.txt"),result);System.out.println(result);mc.stop();return;
            }
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();int next=shot;age=0;
            server.execute(()->{var player=server.getPlayerList().getPlayer(uuid);if(player!=null)position(player,SHOTS[next]);});
        }catch(Exception ex){throw new RuntimeException("Laboratory visual check failed",ex);}
    }
}
