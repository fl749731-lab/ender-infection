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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import java.nio.file.*;

/** Opt-in visual regression run. Creates only its own isolated, disposable test world. */
@EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class LabVisualCheck {
    private static final String WORLD="EnderFacilitiesVisualAlpha6";
    private static final BlockPos ORIGIN=new BlockPos(0,70,0);
    private record Shot(String name,double x,double y,double z,float yaw,float pitch,boolean open){}
    private static final Shot[] SHOTS={
        new Shot("01_entrance_closed",20.5,81.4,-3.5,0,7,false),
        new Shot("02_entrance_open",20.5,81.4,-3.5,0,7,true),
        new Shot("03_greenhouse",15.5,76,11.5,-35,25,false),
        new Shot("04_wetland",128,71,25,-150,8,false),
        new Shot("05_forest",224.5,81,10.5,35,18,false),
        new Shot("06_medical",324,71,23,160,5,false),
        new Shot("07_cryo",412.5,76,14.5,-40,10,false),
        new Shot("08_excavation",515.5,81,12.5,-45,10,false)
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
        for(int i=0;i<ResearchLabPlacement.SPECS.size();i++){
            var spec=ResearchLabPlacement.SPECS.get(i);var base=ORIGIN.offset(i*100,0,0);
            if(!ResearchLabPlacement.place(level,base,spec))throw new IllegalStateException("Visual test did not place "+spec.key());
            for(BlockPos pos:BlockPos.betweenClosed(base,base.offset(spec.width()-1,spec.height()-1,spec.length()-1)))if(level.getBlockEntity(pos) instanceof ExpansionNodeEntity node){var tag=node.saveWithFullMetadata();tag.putBoolean("Wild",false);node.load(tag);node.inhibit(level,2400);}
        }
        player.setGameMode(GameType.CREATIVE);player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();
        System.out.println("LAB_ENTRY_STATE "+level.getBlockState(ORIGIN.offset(ResearchLabPlacement.SPECS.get(0).entrance()))+" CONTROL_FLOOR "+level.getBlockState(ORIGIN.offset(6,5,14)));position(player,SHOTS[0]);prepared=true;age=0;
    }
    private static void position(ServerPlayer player,Shot s){
        var level=player.serverLevel();var p=ORIGIN.offset(ResearchLabPlacement.SPECS.get(0).entrance());var state=level.getBlockState(p);level.setBlock(p,state.setValue(LabFixtureBlock.OPEN,s.open),3);
        player.teleportTo(level,s.x,s.y,s.z,s.yaw,s.pitch);age=0;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled()||finished)return;
        var mc=Minecraft.getInstance();
        if(!launched&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            launched=true;mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;
            if(Files.exists(mc.gameDirectory.toPath().resolve("saves/"+WORLD+"/level.dat")))mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(mc.screen));
            else mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), null);
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
