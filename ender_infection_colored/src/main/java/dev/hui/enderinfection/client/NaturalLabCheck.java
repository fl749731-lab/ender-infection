package dev.hui.enderinfection.client;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.design.*;
import dev.hui.enderinfection.worldgen.ResearchLabPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.concurrent.CompletableFuture;

/** Opt-in real noise-world generation probe; never opens a user world or places a template manually. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class NaturalLabCheck {
 private static final String BIOME=System.getProperty("ender_infection.naturalBiome","mushroom_fields");
 private static final String KEY=System.getProperty("ender_infection.naturalBuilding","");
 private static final String WORLD="NaturalLabAlpha6Dry3_"+BIOME+"_"+KEY.replace('/','_');
 private static boolean launched,done;private static long started;
 private static double cameraX,cameraY,cameraZ;private static float cameraYaw,cameraPitch;private static int captureAge;
 private static volatile CompletableFuture<?>[] futures;private static volatile String result;
 private static ResearchLabPlacement.Spec spec=ResearchLabPlacement.SPECS.stream().filter(s->s.biome().equals(BIOME)).findFirst().orElse(null);
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
  if(!Boolean.getBoolean("ender_infection.naturalLabCheck")||!(event.getEntity() instanceof ServerPlayer player)||!player.server.getWorldData().getLevelName().equals(WORLD))return;
  var level=player.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,player.server);
  var set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(new ResourceLocation("ender_infection",(KEY.isEmpty()?spec.key():KEY)));
  var placement=(RandomSpreadStructurePlacement)set.placement();
  var structureType=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation("ender_infection",(KEY.isEmpty()?spec.key():KEY)));
  net.minecraft.world.level.ChunkPos found=null;int skipped=0;
  for(int i=0;i<64&&found==null;i++){
   var pos=placement.getPotentialStructureChunk(level.getSeed(),64+(i%8)*40,64+(i/8)*40);
   var ctx=new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(level.registryAccess(),level.getChunkSource().getGenerator(),level.getChunkSource().getGenerator().getBiomeSource(),level.getChunkSource().randomState(),level.getStructureManager(),level.getSeed(),pos,level,b->true);
   if(structureType.findValidGenerationPoint(ctx).isPresent())found=pos;else skipped++;
  }
  if(found==null)throw new IllegalStateException("No eligible natural building candidate");
  final var target=found;System.out.println("NATURAL_TERRAIN_FILTER rejected_candidates="+skipped+" target="+target);
  var requests=new java.util.ArrayList<CompletableFuture<?>>();
  for(int x=target.x-2;x<=target.x+4;x++)for(int z=target.z-2;z<=target.z+5;z++)requests.add(level.getChunkSource().getChunkFuture(x,z,ChunkStatus.FULL,true));
  futures=requests.toArray(CompletableFuture[]::new);System.out.println("NATURAL_LAB_REQUEST biome="+BIOME+" target="+target+" chunks="+requests.size());
  CompletableFuture.allOf(futures).thenRun(()->player.server.execute(()->{
   try{
    for(var future:requests){var either=(com.mojang.datafixers.util.Either<?,?>)future.join();if(either.left().isEmpty())throw new IllegalStateException("Chunk generation failed: "+either.right());}
    var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation("ender_infection",(KEY.isEmpty()?spec.key():KEY)));
    var chunk=level.getChunkSource().getChunkNow(target.x,target.z);var start=chunk.getStartForStructure(structure);
    if(start==null||!start.isValid())throw new IllegalStateException("No natural structure start at candidate");
    var bounds=start.getBoundingBox();int doors=0,lifts=0,architecture=0;
    for(var pos:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ())){
     var block=level.getBlockState(pos).getBlock();String path=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();if(path.contains("dark_oak")||path.contains("prismarine")||path.contains("deepslate_brick"))architecture++;if(block instanceof LabLiftBlock)lifts++;if(block instanceof LabFixtureBlock b&&b.base().equals("lab_door"))doors++;
    }
    if(KEY.isEmpty()&&(doors==0||lifts!=spec.floors()+1))throw new IllegalStateException("Incomplete natural structure: doors="+doors+", lifts="+lifts);
    cameraX=bounds.maxX()+30;cameraY=Math.min(level.getMaxBuildHeight()-10,bounds.minY()+32);cameraZ=bounds.maxZ()+35;
    double dx=(bounds.minX()+bounds.maxX())/2.0-cameraX,dz=(bounds.minZ()+bounds.maxZ())/2.0-cameraZ,dy=(bounds.minY()+bounds.maxY())/2.0-cameraY;
    cameraYaw=(float)Math.toDegrees(Math.atan2(-dx,dz));cameraPitch=(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)));
    level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,player.server);player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(level,cameraX,cameraY,cameraZ,cameraYaw,cameraPitch);
    if(KEY.equals("imported/church")&&architecture<10000)throw new IllegalStateException("Incomplete church architecture: "+architecture);
    result="NATURAL_LAB_PASS building="+(KEY.isEmpty()?spec.key():KEY)+" biome="+BIOME+" full_chunks="+requests.size()+" doors="+doors+" lifts="+lifts+" architecture_blocks="+architecture+" bounds="+bounds;
   }catch(Exception e){result="NATURAL_LAB_FAIL "+e;}
  }));
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
  if(!Boolean.getBoolean("ender_infection.naturalLabCheck")||done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
  if(!launched&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
   launched=true;started=System.currentTimeMillis();mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;
   if(Boolean.getBoolean("ender_infection.naturalRerender"))mc.createWorldOpenFlows().loadLevel(mc.screen,WORLD);
   else mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,true,false),r->{
    var dimensions=r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions();
    return dimensions.replaceOverworldGenerator(r,new NoiseBasedChunkGenerator(new FixedBiomeSource(r.registryOrThrow(Registries.BIOME).getHolderOrThrow(ResourceKey.create(Registries.BIOME,new ResourceLocation("minecraft",BIOME)))),r.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD)));
   });
  }
  if(launched&&result!=null&&result.startsWith("NATURAL_LAB_PASS")&&!KEY.isEmpty()&&mc.player!=null&&++captureAge<=400){
   if(mc.screen!=null)mc.setScreen(null);mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.setCameraEntity(mc.player);mc.player.setPos(cameraX,cameraY,cameraZ);mc.player.setYRot(cameraYaw);mc.player.setXRot(cameraPitch);mc.player.yRotO=cameraYaw;mc.player.xRotO=cameraPitch;mc.player.setDeltaMovement(0,0,0);mc.player.getAbilities().flying=true;
   if(captureAge==400)try{var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var shot=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(folder.resolve("natural_"+KEY.replace('/','_')+".png"));}}catch(Exception e){throw new RuntimeException(e);}return;
  }
  if(launched&&result!=null){done=true;System.out.println(result);try{java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("natural-lab-result-"+BIOME+".txt"),result);}catch(Exception e){throw new RuntimeException(e);}mc.stop();}
  else if(launched&&System.currentTimeMillis()-started>180000){
   done=true;System.out.println("NATURAL_LAB_TIMEOUT biome="+BIOME);
   Thread.getAllStackTraces().forEach((thread,trace)->{if(thread.getName().contains("Server")||thread.getName().contains("Worker")){System.out.println("THREAD "+thread.getName()+" "+thread.getState());for(var frame:trace)System.out.println("  at "+frame);}});mc.stop();
  }
 }
}
