package dev.hui.enderinfection.client;

import com.google.gson.*;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.design.LabDetailBlock;
import dev.hui.enderinfection.institute.OperationsData;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.WorldIntroduction;
import dev.hui.enderinfection.worldgen.CentralInstitute;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Fast interior review of exact current templates in a fresh, isolated flat world.
 * This is a visual preview, separate from natural-generation acceptance.
 */
@EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class InstituteRoomVisualCheck {
    private static final String WORLD="InstituteRoomPreview_"+System.currentTimeMillis();
    private record Shot(String name,double x,double y,double z,float yaw,float pitch,BoundingBox room) {}
    private static final List<Shot> SHOTS=new ArrayList<>();
    private static final Map<BlockPos,Integer> LIGHTS=new HashMap<>();
    private static boolean launched,finished;
    private static volatile boolean ready;
    private static volatile String failure;
    private static long started;
    private static int index,age,stable;
    private static boolean enabled(){return Boolean.getBoolean("ender_infection.roomVisualCheck");}

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player)||!player.server.getWorldData().getLevelName().equals(WORLD))return;
        try{
            var level=player.serverLevel();
            var introduction=new CompoundTag();introduction.putBoolean("Completed",true);
            level.getDataStorage().set("ender_infection_world_introduction",WorldIntroduction.Data.load(introduction));
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,player.server);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,player.server);level.setDayTime(6000);
            JsonObject data;
            try(var reader=player.server.getResourceManager().getResourceOrThrow(ResourceLocation.parse("ender_infection:central_institute.json")).openAsReader()){
                data=JsonParser.parseReader(reader).getAsJsonObject();
            }
            int number=0;
            var requested=System.getProperty("ender_infection.roomPreviewRooms","B2,B4,A7");
            var roomIds=new LinkedHashSet<String>();
            if(requested.equals("all")){
                roomIds.addAll(List.of("F7","A3","B6","B7","C4","C5","C6","D3","D7","E5"));
                for(var value:data.getAsJsonArray("rooms"))roomIds.add(value.getAsJsonObject().get("id").getAsString());
            }else roomIds.addAll(Arrays.asList(requested.split(",")));
            for(String roomId:roomIds){
                JsonObject room=null;
                for(var value:data.getAsJsonArray("rooms"))if(value.getAsJsonObject().get("id").getAsString().equals(roomId))room=value.getAsJsonObject();
                if(room==null)throw new IllegalStateException("Missing preview room "+roomId);
                int x=room.get("x").getAsInt(),y=room.get("y").getAsInt(),z=room.get("z").getAsInt();
                int width=room.get("width").getAsInt(),depth=room.get("length").getAsInt(),height=room.get("height").getAsInt();
                int offset=number++*48;
                var origin=new BlockPos(-x,64-y,offset-z);
                var clip=new BoundingBox(0,64,offset,width-1,64+height,offset+depth-1);
                // A powered facility snapshot keeps the normal circuit refresh
                // from switching the preview lamps back off after placement.
                var power=OperationsData.get(level).save(new CompoundTag());
                var facility=new CompoundTag();facility.putLong("Origin",origin.asLong());
                facility.putBoolean("Repaired",true);facility.putBoolean("ServicePowered",true);facility.putBoolean("ContainmentPowered",true);
                power.getList("Facilities",10).add(facility);
                level.getDataStorage().set(OperationsData.NAME,OperationsData.load(power));
                for(int cx=-1;cx<=(width>>4)+1;cx++)for(int cz=(offset>>4)-1;cz<=((offset+depth)>>4)+1;cz++){
                    level.setChunkForced(cx,cz,true);level.getChunk(cx,cz);
                }
                for(var tile:CentralInstitute.tiles()){
                    var at=origin.offset(tile.x(),tile.y(),tile.z());
                    var tileBox=new BoundingBox(at.getX(),at.getY(),at.getZ(),at.getX()+tile.width()-1,at.getY()+tile.height()-1,at.getZ()+tile.length()-1);
                    if(tileBox.intersects(clip)&&!CentralInstitute.template(level,tile).placeInWorld(level,at,at,CentralInstitute.settings(clip),RandomSource.create(7312026L),CentralInstitute.UPDATE_FLAGS))
                        throw new IllegalStateException("Preview template failed "+tile.template());
                }
                // Review the layout with real lamp block states switched on.
                // Source templates keep their normal initial power state.
                int lamps=0;
                for(var value:data.getAsJsonObject("operations").getAsJsonArray("managed_lights")){
                    var point=value.getAsJsonObject().getAsJsonArray("pos");
                    var at=origin.offset(point.get(0).getAsInt(),point.get(1).getAsInt(),point.get(2).getAsInt());
                    if(!clip.isInside(at))continue;
                    var state=level.getBlockState(at);
                    if(state.getBlock() instanceof LabDetailBlock detail&&detail.isLamp()){
                        level.setBlock(at,state.setValue(LabDetailBlock.POWERED,true),3);lamps++;
                    }
                }
                for(var at:BlockPos.betweenClosed(clip.minX(),clip.minY(),clip.minZ(),clip.maxX(),clip.maxY(),clip.maxZ())){
                    int emission=level.getBlockState(at).getLightEmission();
                    if(emission>0)LIGHTS.put(at.immutable(),emission);
                }
                // Find two actual standing positions, clear of models and automatic parts.
                for(int side=0;side<2;side++){
                    BlockPos camera=null;
                    for(int dz=2;dz<depth-2&&camera==null;dz++)for(int dx=2;dx<width-2;dx++){
                        int X=side==0?dx:width-1-dx,Z=offset+(side==0?dz:depth-1-dz);
                        if(level.noCollision(new AABB(X+.2,65,Z+.2,X+.8,67.8,Z+.8))){camera=new BlockPos(X,65,Z);break;}
                    }
                    if(camera==null)throw new IllegalStateException("No clear preview camera "+roomId);
                    if(roomId.equals("F7")&&side==0)
                        look("room_F7_front",6.5,65.15,offset+10.5,13.5,66.4,offset+9.5,clip);
                    else if(roomId.equals("F7")&&side==1)
                        look("room_F7_back",21.5,65.15,offset+11.5,16,66.6,offset+9.5,clip);
                    else look("room_"+roomId+"_"+(side==0?"front":"back"),camera.getX()+.5,65.15,camera.getZ()+.5,width/2.,66.9,offset+depth/2.,clip);
                }
                System.out.println("INSTITUTE_ROOM_PREVIEW_BUILT room="+roomId+" width="+width+" depth="+depth+" powered_lamps="+lamps);
            }
            player.setGameMode(GameType.SPECTATOR);position(player,SHOTS.getFirst());ready=true;
            System.out.println("INSTITUTE_ROOM_PREVIEW_MANIFEST sha256="+CentralInstitute.manifest(level).hash());
        }catch(Exception error){failure=error.toString();error.printStackTrace();}
    }

    private static void look(String name,double x,double y,double z,double tx,double ty,double tz,BoundingBox room){
        double dx=tx-x,dz=tz-z,dy=ty-(y+1.62);
        SHOTS.add(new Shot(name,x,y,z,(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))),room));
    }
    private static void position(ServerPlayer player,Shot shot){player.teleportTo(player.serverLevel(),shot.x,shot.y,shot.z,shot.yaw,shot.pitch);}
    private static void finish(Minecraft mc,String result){
        finished=true;System.out.println(result);
        try{Files.writeString(mc.gameDirectory.toPath().resolve("room-preview-result.txt"),result+"\nworld="+WORLD+"\n");}
        catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
        mc.stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled()||finished)return;
        var mc=Minecraft.getInstance();if(started==0)started=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){
            launched=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.fov().set(80);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SPECTATOR,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                new WorldOptions(7312026L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),null);return;
        }
        if(failure!=null){finish(mc,"INSTITUTE_ROOM_PREVIEW_FAIL "+failure);return;}
        if(ready&&mc.player!=null&&index<SHOTS.size()){
            var shot=SHOTS.get(index);mc.setCameraEntity(mc.player);mc.player.setPos(shot.x,shot.y,shot.z);mc.player.setYRot(shot.yaw);mc.player.setXRot(shot.pitch);
            mc.player.yRotO=shot.yaw;mc.player.xRotO=shot.pitch;mc.player.setDeltaMovement(0,0,0);age++;
            boolean loaded=mc.level!=null;
            if(loaded)for(int x=shot.room.minX()>>4;x<=shot.room.maxX()>>4;x++)for(int z=shot.room.minZ()>>4;z<=shot.room.maxZ()>>4;z++)
                loaded&=mc.level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false)!=null;
            boolean compiled=mc.levelRenderer.isSectionCompiled(new BlockPos(shot.room.minX(),65,shot.room.minZ()));
            boolean motherReady=!shot.name.startsWith("room_F7")||mc.level!=null&&mc.level.getEntitiesOfClass(InfectedMob.class,
                new AABB(shot.room.minX(),shot.room.minY(),shot.room.minZ(),shot.room.maxX()+1,shot.room.maxY()+1,shot.room.maxZ()+1),m->m.kind==InfectedKind.CORE).size()==1;
            boolean lit=loaded;
            if(lit)for(var light:LIGHTS.entrySet())if(shot.room.isInside(light.getKey()))
                lit&=mc.level.getBlockState(light.getKey()).getLightEmission()==light.getValue()
                    &&mc.level.getBrightness(LightLayer.BLOCK,light.getKey())>=light.getValue();
            if(loaded&&!lit&&age%40==0){
                // A distant room may enter the client cache before its initial
                // lighting packet. Resend the server's actual light snapshot.
                var uuid=mc.player.getUUID();
                mc.getSingleplayerServer().execute(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);if(player==null)return;
                    var engine=player.serverLevel().getChunkSource().getLightEngine();
                    for(int cx=shot.room.minX()>>4;cx<=shot.room.maxX()>>4;cx++)for(int cz=shot.room.minZ()>>4;cz<=shot.room.maxZ()>>4;cz++)
                        player.connection.send(new ClientboundLightUpdatePacket(new ChunkPos(cx,cz),engine,null,null));
                });
            }
            if(loaded&&lit&&motherReady&&compiled&&mc.levelRenderer.hasRenderedAllSections()&&mc.screen==null&&mc.getOverlay()==null)stable++;else stable=0;
            if(age%100==0){
                System.out.println("INSTITUTE_ROOM_PREVIEW_WAIT "+shot.name+" loaded="+loaded+" lit="+lit+" compiled="+compiled+" stable="+stable);
                if(!lit&&loaded)for(var light:LIGHTS.entrySet())if(shot.room.isInside(light.getKey())
                    &&(mc.level.getBlockState(light.getKey()).getLightEmission()!=light.getValue()||mc.level.getBrightness(LightLayer.BLOCK,light.getKey())<light.getValue())){
                    var point=light.getKey();
                    System.out.println("INSTITUTE_ROOM_PREVIEW_LIGHT_PENDING pos="+point+" expected="+light.getValue()+" state="+mc.level.getBlockState(point)+" actual="+mc.level.getBrightness(LightLayer.BLOCK,point));
                    mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();System.out.println("INSTITUTE_ROOM_PREVIEW_SERVER_LIGHT pos="+point+" state="+level.getBlockState(point)+" actual="+level.getBrightness(LightLayer.BLOCK,point));});
                    break;
                }
            }
            if(age>=50&&stable>=25){
                try{
                    var folder=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(folder);
                    try(var frame=Screenshot.takeScreenshot(mc.getMainRenderTarget())){frame.writeToFile(folder.resolve(shot.name+".png"));}
                    System.out.println("INSTITUTE_ROOM_PREVIEW_CAPTURE "+shot.name);
                    if(++index==SHOTS.size()){finish(mc,"INSTITUTE_ROOM_PREVIEW_PASS rooms="+(SHOTS.size()/2)+" screenshots="+SHOTS.size()+" natural=false");return;}
                    age=0;stable=0;var uuid=mc.player.getUUID();int next=index;
                    mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);if(player!=null)position(player,SHOTS.get(next));});
                }catch(java.io.IOException error){failure=error.toString();}
            }
        }
        if(System.currentTimeMillis()-started>900000)finish(mc,"INSTITUTE_ROOM_PREVIEW_FAIL timeout screenshots="+index);
    }
}
