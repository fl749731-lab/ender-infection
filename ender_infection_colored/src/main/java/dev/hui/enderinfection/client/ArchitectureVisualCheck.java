package dev.hui.enderinfection.client;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.worldgen.ImportedBuildings;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class ArchitectureVisualCheck {
 private static final String WORLD="ImportedArchitectureAlpha6";
 private record Shot(String name,double x,double y,double z,float yaw,float pitch){}
 private static final Shot[] SHOTS={
  new Shot("hbm_lab_exterior",30,80,-10,42,18),
  new Shot("hbm_lab_interior",13.5,74,14.5,135,7),
  new Shot("hbm_factory_exterior",146,88,-16,48,12),
  new Shot("hbm_factory_interior",118,72,10,-65,6),
  new Shot("hbm_arctic_exterior",245,82,-13,47,17),
  new Shot("hbm_arctic_interior",209.5,73,11.5,145,6),
  new Shot("church_exterior",353,96,93,149,12),
  new Shot("church_nave",318,73,60,180,-6)
 };
 private static boolean launched,ready,finished;private static int index,age;private static long started;
 private static boolean enabled(){return Boolean.getBoolean("ender_infection.architectureCheck");}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
  if(!enabled()||!(event.getEntity() instanceof ServerPlayer p)||!p.server.getWorldData().getLevelName().equals(WORLD))return;
  var level=p.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);level.setDayTime(6000);
  int i=0;for(var spec:ImportedBuildings.catalog(level)){
   if(spec.local())continue;
   var origin=new BlockPos(i++*100,70-spec.ground(),0);
   if(!ImportedBuildings.template(level,spec).placeInWorld(level,origin,origin,new StructurePlaceSettings().setIgnoreEntities(true),level.random,2))throw new IllegalStateException("Could not preview "+spec.key());
  }
  p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,2400,0,false,false));p.setGameMode(GameType.CREATIVE);p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();position(p,SHOTS[0]);ready=true;
 }
 private static void position(ServerPlayer p,Shot s){p.teleportTo(p.serverLevel(),s.x,s.y,s.z,s.yaw,s.pitch);age=0;}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
  if(!enabled()||finished||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
  if(!launched&&mc.screen!=null&&mc.getOverlay()==null){
   launched=true;started=System.currentTimeMillis();mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;
   if(Files.exists(mc.gameDirectory.toPath().resolve("saves/"+WORLD+"/level.dat")))mc.createWorldOpenFlows().loadLevel(mc.screen,WORLD);
   else mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
   return;
  }
  if(ready&&mc.player!=null&&index<SHOTS.length){
   var s=SHOTS[index];mc.setCameraEntity(mc.player);mc.player.setPos(s.x,s.y,s.z);mc.player.setYRot(s.yaw);mc.player.setXRot(s.pitch);mc.player.yRotO=s.yaw;mc.player.xRotO=s.pitch;mc.player.setYHeadRot(s.yaw);mc.player.setDeltaMovement(0,0,0);mc.player.getAbilities().flying=true;
  }
  if(ready&&mc.level!=null&&mc.player!=null&&mc.screen==null&&mc.getOverlay()==null&&++age>=100){
   try{
    Path folder=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(folder);try(var frame=Screenshot.takeScreenshot(mc.getMainRenderTarget())){frame.writeToFile(folder.resolve(SHOTS[index].name+".png"));}
    System.out.println("ARCHITECTURE_CAPTURE "+SHOTS[index].name);
    if(++index==SHOTS.length){finished=true;System.out.println("ARCHITECTURE_VISUAL_PASS screenshots="+index);mc.stop();return;}
    var uuid=mc.player.getUUID();var server=mc.getSingleplayerServer();int next=index;age=0;server.execute(()->{var p=server.getPlayerList().getPlayer(uuid);if(p!=null)position(p,SHOTS[next]);});
   }catch(Exception e){throw new RuntimeException(e);}
  }
  if(launched&&System.currentTimeMillis()-started>180000){finished=true;throw new IllegalStateException("Architecture capture timed out after "+index+" screenshots");}
 }
}
