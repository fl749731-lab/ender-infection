package dev.hui.enderinfection.client;

import com.google.gson.*;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.design.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Opt-in native natural-generation and visual acceptance, always in a fresh isolated world. */
@EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class CentralInstituteVisualCheck {
    private static final String WORLD="CentralInstituteNatural_"+System.currentTimeMillis();
    private static final long SEED=Long.getLong("ender_infection.centralInstituteSeed",7312026L);
    private record Shot(String name,double x,double y,double z,float yaw,float pitch,boolean night){}
    private static final List<Shot> SHOTS=new ArrayList<>();
    private static final List<String> CAPTURED=new ArrayList<>();
    private static volatile boolean ready;private static boolean launched,finished;private static int index,age,preparedShot=-1,stableRenderTicks,blankAttempts;private static long started;
    private static volatile String failure;private static int fullChunks;private static BoundingBox bounds;
    private static boolean enabled(){return Boolean.getBoolean("ender_infection.centralInstituteVisualCheck");}
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    private static BlockPos pos(JsonElement value){if(value.isJsonArray()){var a=value.getAsJsonArray();return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}var o=value.getAsJsonObject();return new BlockPos(o.get("x").getAsInt(),o.get("y").getAsInt(),o.get("z").getAsInt());}
    private static JsonObject manifest(ServerPlayer player){
        try(var reader=new InputStreamReader(player.server.getResourceManager().getResource(id("ender_infection:central_institute.json")).orElseThrow().open(),java.nio.charset.StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}
        catch(IOException e){throw new UncheckedIOException(e);}
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player)||!player.server.getWorldData().getLevelName().equals(WORLD))return;
        try{
            var level=player.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,player.server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,player.server);level.setDayTime(6000);
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id("ender_infection:central_institute"));
            var set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(id("ender_infection:central_institute"));
            var placement=(RandomSpreadStructurePlacement)set.placement();ChunkPos found=null;int examined=0;
            var rejected=new LinkedHashMap<String,Integer>();for(String reason:new String[]{"biome_center","biome_edge","wet","slope","bounds","registered_generation"})rejected.put(reason,0);
            var drySpreads=new TreeMap<Integer,Integer>();int dryVerified=0;
            int bestSpread=Integer.MAX_VALUE;ChunkPos bestSlope=null;long searchStarted=System.currentTimeMillis();
            // This is only a search hint. Validate the exact same real-world terrain,
            // biome predicate, random-spread cell and registered generation path again.
            var known=new ChunkPos(1558,-946);
            if(level.getSeed()==7312026L&&placement.getPotentialStructureChunk(level.getSeed(),known.x,known.z).equals(known)){
                var probe=probeTerrain(player,known,structure);
                if(probe.reason.isEmpty()){
                    var context=new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(level.registryAccess(),level.getChunkSource().getGenerator(),level.getChunkSource().getGenerator().getBiomeSource(),level.getChunkSource().randomState(),level.getStructureManager(),level.getSeed(),known,level,structure.biomes()::contains);
                    if(structure.findValidGenerationPoint(context).isPresent()){found=known;examined=1;dryVerified=1;drySpreads.put(probe.spread,1);System.out.println("CENTRAL_INSTITUTE_CANDIDATE_REVALIDATED seed="+level.getSeed()+" target="+known+" full_spread="+probe.spread);}
                }
            }
            for(int i=0;i<4096&&found==null;i++){
                // Distinct legal random-spread cells, balanced around spawn. This is a search
                // over the actual normal biome source, never a fixed-biome replacement.
                int gridX=i%64,gridZ=i/64;int cellX=(gridX%2==0?gridX/2:-1-gridX/2),cellZ=(gridZ%2==0?gridZ/2:-1-gridZ/2);
                var candidate=placement.getPotentialStructureChunk(level.getSeed(),cellX*64,cellZ*64);examined++;
                var probe=probeTerrain(player,candidate,structure);String reason=probe.reason;
                if(!reason.equals("biome_center")&&!reason.equals("wet")){
                    dryVerified++;drySpreads.merge(probe.spread,1,Integer::sum);if(probe.spread<bestSpread){bestSpread=probe.spread;bestSlope=candidate;}
                }
                if(reason.isEmpty()){
                    var context=new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(level.registryAccess(),level.getChunkSource().getGenerator(),level.getChunkSource().getGenerator().getBiomeSource(),level.getChunkSource().randomState(),level.getStructureManager(),level.getSeed(),candidate,level,structure.biomes()::contains);
                    if(structure.findValidGenerationPoint(context).isPresent())found=candidate;else reason="registered_generation";
                }
                if(!reason.isEmpty()){rejected.put(reason,rejected.get(reason)+1);if(examined<=8)System.out.println("CENTRAL_INSTITUTE_TERRAIN_REJECT candidate="+candidate+" reason="+reason+" full_spread="+probe.spread);}
                if(examined%128==0||found!=null)System.out.println("CENTRAL_INSTITUTE_TERRAIN_SEARCH seed="+level.getSeed()+" examined="+examined+" rejected="+rejected+" dry_verified="+dryVerified+" best_full_dry_spread="+bestSpread+" best_dry_candidate="+bestSlope+" max_relief="+dev.hui.enderinfection.worldgen.CentralInstituteStructure.MAX_TERRAIN_RELIEF+" elapsed_ms="+(System.currentTimeMillis()-searchStarted));
            }
            System.out.println("CENTRAL_INSTITUTE_TERRAIN_FULL_SPREADS dry_verified="+dryVerified+" histogram="+drySpreads);
            if(found==null)throw new IllegalStateException("No eligible central-institute noise-terrain candidate in 4096 spread cells; seed="+level.getSeed()+" rejected="+rejected+" best_full_dry_spread="+bestSpread+" candidate="+bestSlope);
            final var target=found;System.out.println("CENTRAL_INSTITUTE_NATURAL_REQUEST seed="+level.getSeed()+" target="+target+" examined="+examined+" rejected="+rejected);
            // First obtain the actual registered start, then derive the complete chunk footprint.
            level.setChunkForced(target.x,target.z,true);
            level.getChunkSource().getChunkFuture(target.x,target.z,ChunkStatus.FULL,true).thenRun(()->player.server.execute(()->{
                try{
                    var chunk=level.getChunkSource().getChunkNow(target.x,target.z);var start=chunk==null?null:chunk.getStartForStructure(structure);
                    if(start==null||!start.isValid())throw new IllegalStateException("No registered natural StructureStart at "+target);
                    bounds=start.getBoundingBox();
                    if(bounds.getXSpan()!=120||bounds.getYSpan()!=55||bounds.getZSpan()!=104)throw new IllegalStateException("Unexpected natural bounding box: "+bounds);
                    var requests=new ArrayList<CompletableFuture<?>>();
                    for(int x=(bounds.minX()>>4)-1;x<=(bounds.maxX()>>4)+1;x++)for(int z=(bounds.minZ()>>4)-1;z<=(bounds.maxZ()>>4)+1;z++){level.setChunkForced(x,z,true);requests.add(level.getChunkSource().getChunkFuture(x,z,ChunkStatus.FULL,true));}
                    fullChunks=requests.size();CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).thenRun(()->player.server.execute(()->{
                        try{
                            for(int x=(bounds.minX()>>4)-1;x<=(bounds.maxX()>>4)+1;x++)for(int z=(bounds.minZ()>>4)-1;z<=(bounds.maxZ()>>4)+1;z++)if(level.getChunkSource().getChunkNow(x,z)==null)throw new IllegalStateException("Natural chunk did not reach FULL: "+x+","+z);
                            var data=manifest(player);var origin=new BlockPos(bounds.minX(),bounds.minY(),bounds.minZ());int supplies=0;
                            for(var value:data.getAsJsonArray("loot_containers")){var container=value.getAsJsonObject();var at=origin.offset(pos(container));if(!level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.BARREL))throw new IllegalStateException("Natural room missing supply: "+container.get("room")+"/"+at);supplies++;}
                            if(supplies!=48)throw new IllegalStateException("Natural room count was "+supplies);
                            makeShots(player,data,origin);player.setGameMode(GameType.SPECTATOR);position(player,SHOTS.get(0));ready=true;
                            System.out.println("CENTRAL_INSTITUTE_NATURAL_PASS full_chunks="+fullChunks+" rooms="+supplies+" pieces="+start.getPieces().size()+" bounds="+bounds);
                        }catch(Exception e){failure=e.toString();}
                    })).exceptionally(e->{failure=e.toString();return null;});
                }catch(Exception e){failure=e.toString();}
            })).exceptionally(e->{failure=e.toString();return null;});
        }catch(Exception e){failure=e.toString();}
    }
    private record TerrainProbe(String reason,int spread){}
    private static int[] sampleAxis(int length){int count=(length-1)/16+2;int[] values=new int[count];for(int i=0;i<count-1;i++)values[i]=i*16;values[count-1]=length-1;return values;}
    private static TerrainProbe probeTerrain(ServerPlayer player,ChunkPos candidate,net.minecraft.world.level.levelgen.structure.Structure structure){
        var level=player.serverLevel();var generator=level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        int minX=candidate.getMiddleBlockX()-60,minZ=candidate.getMiddleBlockZ()-52;
        // Fast biome screening avoids expensive height scans across oceans/forests. The
        // production generator is still the final authority for every accepted candidate.
        int centerHeight=generator.getBaseHeight(candidate.getMiddleBlockX(),candidate.getMiddleBlockZ(),Heightmap.Types.WORLD_SURFACE_WG,level,random);
        var centerBiome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(candidate.getMiddleBlockX()),QuartPos.fromBlock(centerHeight-1),QuartPos.fromBlock(candidate.getMiddleBlockZ()),random.sampler());
        if(!structure.biomes().contains(centerBiome))return new TerrainProbe("biome_center",-1);
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;boolean wet=false;
        for(int x:sampleAxis(120))for(int z:sampleAxis(104)){
            int floor=generator.getBaseHeight(minX+x,minZ+z,Heightmap.Types.OCEAN_FLOOR_WG,level,random);
            int surface=generator.getBaseHeight(minX+x,minZ+z,Heightmap.Types.WORLD_SURFACE_WG,level,random);
            wet|=surface>floor||floor<=generator.getSeaLevel();low=Math.min(low,floor);high=Math.max(high,floor);
        }
        // Complete all 72 samples before reporting relief: an early >threshold
        // prefix is not a measurement of the whole site's actual height range.
        if(wet)return new TerrainProbe("wet",high-low);
        if(high-low>dev.hui.enderinfection.worldgen.CentralInstituteStructure.MAX_TERRAIN_RELIEF)return new TerrainProbe("slope",high-low);
        int bottom=high-1-28;if(bottom<level.getMinBuildHeight()+1||bottom+55>level.getMaxBuildHeight())return new TerrainProbe("bounds",high-low);
        for(int x:sampleAxis(120))for(int z:sampleAxis(104)){
            var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(minX+x),QuartPos.fromBlock(high-1),QuartPos.fromBlock(minZ+z),random.sampler());
            if(!structure.biomes().contains(biome))return new TerrainProbe("biome_edge",high-low);
        }
        return new TerrainProbe("",high-low);
    }
    private static void look(String name,double x,double y,double z,double tx,double ty,double tz,boolean night){
        double dx=tx-x,dz=tz-z,dy=ty-(y+1.62);SHOTS.add(new Shot(name,x,y,z,(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))),night));
    }
    private static void makeShots(ServerPlayer player,JsonObject data,BlockPos origin){
        int ox=origin.getX(),oy=origin.getY(),oz=origin.getZ();
        look("00_exterior_front",ox+148,oy+75,oz+142,ox+60,oy+32,oz+52,false);
        look("01_exterior_rear",ox-25,oy+60,oz-25,ox+60,oy+30,oz+52,false);
        look("02_atrium",ox+60,oy+47,oz+57,ox+60,oy+35,oz+43,false);
        var floors=new LinkedHashMap<String,Integer>();
        for(var value:data.getAsJsonArray("rooms")){var room=value.getAsJsonObject();String floor=room.get("floor").getAsString();var p=pos(room);floors.putIfAbsent(floor,p.getY());
            double cx=ox+p.getX()+room.get("width").getAsInt()/2.0,cz=oz+p.getZ()+room.get("length").getAsInt()/2.0;
            double cameraX=Double.NaN,cameraZ=Double.NaN;
            for(int dz=2;dz<room.get("length").getAsInt()-2&&Double.isNaN(cameraX);dz+=2)for(int dx=2;dx<room.get("width").getAsInt()-2;dx+=2){
                double x=ox+p.getX()+dx+.5,z=oz+p.getZ()+dz+.5,y=oy+p.getY()+1;
                if(player.serverLevel().noCollision(new net.minecraft.world.phys.AABB(x-.3,y,z-.3,x+.3,y+1.8,z+.3))){cameraX=x;cameraZ=z;break;}
            }
            if(Double.isNaN(cameraX))throw new IllegalStateException("No clear interior camera for "+room.get("id"));
            look("room_"+room.get("id").getAsString(),cameraX,oy+p.getY()+1.05,cameraZ,cx,oy+p.getY()+2.7,cz,false);
        }
        for(var floor:floors.entrySet())look("floor_"+floor.getKey(),ox+60,oy+floor.getValue()+3.2,oz+61,ox+60,oy+floor.getValue()+2,oz+43,false);
        int i=0;for(var value:data.getAsJsonArray("stairs")){var flight=value.getAsJsonObject();if(i++!=0&&i!=10)continue;var p=pos(flight.get("lower_landing"));var target=pos(flight.get("upper_landing"));look("stairs_"+i,ox+p.getX()+.5,oy+p.getY()+1.05,oz+p.getZ()+.5,ox+target.getX()+.5,oy+target.getY()+1.8,oz+target.getZ()+.5,false);}
        look("night_exterior",ox+148,oy+60,oz+142,ox+60,oy+30,oz+52,true);
        look("night_reactor",ox+44,oy+5.2,oz+40,ox+60,oy+8,oz+48,true);
        if(SHOTS.stream().filter(s->s.name.startsWith("room_")).count()!=48)throw new IllegalStateException("Visual fixture omitted room cameras");
    }
    private static void position(ServerPlayer player,Shot shot){player.serverLevel().setDayTime(shot.night?18000:6000);player.teleportTo(player.serverLevel(),shot.x,shot.y,shot.z,shot.yaw,shot.pitch);}
    private static int missingClientChunks(Minecraft mc){
        int missing=0;for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++)if(mc.level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false)==null)missing++;return missing;
    }
    private static BlockPos visibleGeometryProbe(Shot shot){
        if(shot.name.contains("exterior"))return new BlockPos(bounds.minX()+(shot.x<bounds.minX()+60?8:111),bounds.minY()+44,bounds.minZ()+(shot.z<bounds.minZ()+52?8:87));
        return BlockPos.containing(shot.x,shot.y+1,shot.z);
    }
    private static double nonSkyFraction(com.mojang.blaze3d.platform.NativeImage frame){
        int tested=0,nonSky=0;for(int y=0;y<frame.getHeight();y+=8)for(int x=0;x<frame.getWidth();x+=8){int pixel=frame.getPixelRGBA(x,y);int r=pixel&255,g=(pixel>>>8)&255,b=(pixel>>>16)&255;tested++;if(!(b>r+12&&b>g+4&&b>100))nonSky++;}return nonSky/(double)tested;
    }
    private static void result(Minecraft mc,String text){
        finished=true;System.out.println(text);try{Files.writeString(mc.gameDirectory.toPath().resolve("central-institute-result.txt"),text+"\nworld="+WORLD+"\nshots="+String.join(",",CAPTURED)+"\n");}catch(IOException e){throw new UncheckedIOException(e);}mc.stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled()||finished)return;var mc=Minecraft.getInstance();
        if(started==0)started=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){
            launched=true;started=System.currentTimeMillis();mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(14);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SPECTATOR,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(SEED,true,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(), null);return;
        }
        if(failure!=null){result(mc,"CENTRAL_INSTITUTE_VISUAL_FAIL "+failure);return;}
        if(ready&&mc.player!=null&&index<SHOTS.size()){
            if(preparedShot!=index){preparedShot=index;age=0;stableRenderTicks=0;blankAttempts=0;}
            var shot=SHOTS.get(index);mc.setCameraEntity(mc.player);mc.player.setPos(shot.x,shot.y,shot.z);mc.player.setYRot(shot.yaw);mc.player.setXRot(shot.pitch);mc.player.yRotO=shot.yaw;mc.player.xRotO=shot.pitch;mc.player.setDeltaMovement(0,0,0);
            int missing=mc.level==null?fullChunks:missingClientChunks(mc);boolean compiled=mc.level!=null&&mc.levelRenderer.isSectionCompiled(visibleGeometryProbe(shot));boolean renderDone=mc.level!=null&&mc.levelRenderer.hasRenderedAllSections();age++;
            if(missing==0&&compiled&&renderDone&&mc.screen==null&&mc.getOverlay()==null)stableRenderTicks++;else stableRenderTicks=0;
            if(age%100==0)System.out.println("CENTRAL_INSTITUTE_CAPTURE_WAIT "+shot.name+" missing_client_chunks="+missing+" probe_compiled="+compiled+" renderer_done="+renderDone+" stable_ticks="+stableRenderTicks);
            if(age>=40&&stableRenderTicks>=20){
                try{var folder=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(folder);try(var frame=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                        double content=nonSkyFraction(frame);
                        if(!shot.night&&shot.name.contains("exterior")&&content<.025){blankAttempts++;stableRenderTicks=0;age=0;System.out.println("CENTRAL_INSTITUTE_CAPTURE_RETRY "+shot.name+" reason=sky_only non_sky_fraction="+content+" attempt="+blankAttempts);if(blankAttempts>=3)failure="Exterior capture remained sky-only after three fully loaded/compiled attempts: "+shot.name;else mc.levelRenderer.allChanged();return;}
                        frame.writeToFile(folder.resolve(shot.name+".png"));System.out.println("CENTRAL_INSTITUTE_CAPTURE_QUALITY "+shot.name+" non_sky_fraction="+content+" compiled_stable_ticks="+stableRenderTicks);
                    }CAPTURED.add(shot.name);System.out.println("CENTRAL_INSTITUTE_CAPTURE "+shot.name);
                    if(++index==SHOTS.size()){result(mc,"CENTRAL_INSTITUTE_VISUAL_PASS natural=true rooms=48 full_chunks="+fullChunks+" screenshots="+CAPTURED.size());return;}
                    var uuid=mc.player.getUUID();int next=index;age=0;mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);if(player!=null)position(player,SHOTS.get(next));});
                }catch(IOException e){failure=e.toString();}
            }
        }
        if(System.currentTimeMillis()-started>900000){Thread.getAllStackTraces().forEach((thread,trace)->{if(thread.getName().contains("Server")||thread.getName().contains("Worker")){System.out.println("CENTRAL_TIMEOUT_THREAD "+thread.getName()+" "+thread.getState());for(var frame:trace)System.out.println("  at "+frame);}});result(mc,"CENTRAL_INSTITUTE_VISUAL_FAIL timeout screenshots="+index);}
    }
}
