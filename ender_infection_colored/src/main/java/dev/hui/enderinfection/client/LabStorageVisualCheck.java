package dev.hui.enderinfection.client;

import com.mojang.authlib.GameProfile;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.design.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Real use-item packets, vanilla generic-container screen/slots, and a menu-held open-model photo. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class LabStorageVisualCheck {
    private static final String WORLD="LabStorageVisual_"+System.currentTimeMillis();
    private static final String[] KEYS={"lab_cabinet","lab_cabinet_eroded","lab_workbench","lab_workbench_eroded",
            "lab_freezer","lab_storage_crate","lab_shelf","lab_glass_cabinet","lab_cryo_cabinet",
            "lab_server_rack","lab_control_console","lab_rolling_cart","lab_sink"};
    private static final List<BlockPos> ROOTS=new ArrayList<>();
    private static final List<String> CAPTURED=new ArrayList<>();
    private static boolean launched,finished,verificationRequested,modelRequested;private static int index,age,stable,phase;
    private static long started;private static volatile boolean ready,verified,modelReady,sceneBuilt,lightSyncPending;private static volatile int expectedMenuId;private static int lightingWaitTicks;
    private static volatile String failure;private static volatile boolean lightingDiagnosticPending;private static ServerPlayer modelViewer;
    private static boolean enabled(){return Boolean.getBoolean("ender_infection.storageVisualCheck");}
    private static ResourceLocation id(String value){return new ResourceLocation(value);}
    private static Block block(String key){return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id("ender_infection:"+key)).orElseThrow();}
    private static void load(LabStorageInventory inventory,CompoundTag tag,ServerPlayer player){inventory.load(tag);}
    private static void use(ServerPlayer player,BlockPos at){
        var level=player.serverLevel();var state=level.getBlockState(at);var hit=new BlockHitResult(Vec3.atCenterOf(at),Direction.NORTH,at,false);
        if(state.getBlock() instanceof LabFixtureBlock fixture)fixture.use(state,level,at,player,InteractionHand.MAIN_HAND,hit);
        else ((LabStorageBlock)state.getBlock()).use(state,level,at,player,InteractionHand.MAIN_HAND,hit);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player)||!player.server.getWorldData().getLevelName().equals(WORLD))return;
        try{
            var level=player.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,player.server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,player.server);level.setDayTime(6000);
            var requests=new ArrayList<CompletableFuture<?>>();
            for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++){level.setChunkForced(x,z,true);requests.add(level.getChunkSource().getChunkFuture(x,z,ChunkStatus.FULL,true));}
            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).thenRun(()->player.server.execute(()->{
                try{for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++)if(level.getChunkSource().getChunkNow(x,z)==null)throw new IllegalStateException("Scene chunk did not reach FULL: "+x+","+z);createScene(player);}
                catch(Exception error){failure=error.toString();}
            })).exceptionally(error->{failure=error.toString();return null;});
        }catch(Exception error){failure=error.toString();}
    }
    private static void createScene(ServerPlayer player){
            var level=player.serverLevel();
            // Use the existing illuminated flat-surface section rather than inventing
            // a floating section far above the vanilla heightmap/light initialization.
            for(int x=0;x<32;x++)for(int z=0;z<33;z++)level.setBlock(new BlockPos(x,-61,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
            for(int x=0;x<32;x++)for(int y=-60;y<-56;y++)level.setBlock(new BlockPos(x,y,31),Blocks.WHITE_CONCRETE.defaultBlockState(),3);
            for(int i=0;i<KEYS.length;i++){
                var root=new BlockPos(4+(i%4)*7,-60,4+(i/4)*7);ROOTS.add(root);var state=block(KEYS[i]).defaultBlockState();level.setBlock(root,state,3);
                if(state.getBlock() instanceof LabFixtureBlock fixture)fixture.fillParts(level,root,state);
                if(!(level.getBlockEntity(root) instanceof LabStorageInventory inventory)||!inventory.isStorage())throw new IllegalStateException("No storage BE for "+KEYS[i]);
                var tag=new CompoundTag();tag.putString("LootTable","ender_infection:chests/central_institute/furniture/research");tag.putLong("LootTableSeed",7312026L+i);load(inventory,tag,player);inventory.setChanged();
                if(!inventory.saveInventoryTag().contains("LootTable",8))throw new IllegalStateException("Scene eagerly generated loot for "+KEYS[i]);
                level.setBlock(root.north(2).above(3),Blocks.SEA_LANTERN.defaultBlockState(),3);
            }
            player.setGameMode(GameType.CREATIVE);player.getInventory().clearContent();position(player,0);sceneBuilt=true;
    }
    private static Vec3 camera(int i){var p=ROOTS.get(i);return new Vec3(p.getX()+.5,p.getY(),p.getZ()-2.5);}
    private static Vec3 target(int i){var p=ROOTS.get(i);return new Vec3(p.getX()+.5,p.getY()+(block(KEYS[i]) instanceof LabFixtureBlock?1.45:.5),p.getZ()+.15);}
    private static float pitch(int i){var from=camera(i).add(0,1.62,0);var delta=target(i).subtract(from);return (float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));}
    private static void position(ServerPlayer player,int i){var p=camera(i);player.teleportTo(player.serverLevel(),p.x,p.y,p.z,0,pitch(i));}
    private static void localCamera(Minecraft mc,int i){var from=camera(i);mc.setCameraEntity(mc.player);mc.player.setPos(from.x,from.y,from.z);mc.player.setYRot(0);mc.player.setXRot(pitch(i));mc.player.yRotO=0;mc.player.xRotO=pitch(i);mc.player.setDeltaMovement(0,0,0);}
    private static boolean sceneLoaded(Minecraft mc){
        if(mc.level==null)return false;for(int x=0;x<=1;x++)for(int z=0;z<=2;z++)if(mc.level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false)==null)return false;return true;
    }
    private static int frontLight(Minecraft mc,int i){return mc.level==null?0:mc.level.getBrightness(LightLayer.BLOCK,ROOTS.get(i).north().above());}
    private static void sendSceneLight(ServerPlayer player){
        var level=player.serverLevel();int sent=0;
        for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++)if(level.getChunkSource().getChunkNow(x,z)!=null){
            player.connection.send(new ClientboundLightUpdatePacket(new ChunkPos(x,z),level.getLightEngine(),null,null));sent++;
        }
        System.out.println("LAB_STORAGE_LIGHT_RESYNC chunks="+sent+" native_packet=ClientboundLightUpdatePacket");
    }
    private static void awaitServerLighting(Minecraft mc){
        if(lightSyncPending||mc.player==null||mc.getSingleplayerServer()==null)return;lightSyncPending=true;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();boolean diagnostic=lightingWaitTicks%100==0;
        server.execute(()->{try{var player=server.getPlayerList().getPlayer(uuid);if(player==null)return;var level=player.serverLevel();int minimum=15;
            for(var root:ROOTS)minimum=Math.min(minimum,level.getBrightness(LightLayer.BLOCK,root.north().above()));
            if(diagnostic)System.out.println("LAB_STORAGE_SERVER_LIGHT_WAIT minimum_front_light="+minimum);
            if(minimum>=12){sendSceneLight(player);ready=true;}
        }finally{lightSyncPending=false;}});
    }
    private static void diagnoseLighting(Minecraft mc,int i){
        if(lightingDiagnosticPending||mc.player==null||mc.getSingleplayerServer()==null)return;lightingDiagnosticPending=true;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();int clientLight=frontLight(mc,i);
        server.execute(()->{try{var player=server.getPlayerList().getPlayer(uuid);if(player==null)return;var level=player.serverLevel();var root=ROOTS.get(i);var lamp=root.north(2).above(3);int front=level.getBrightness(LightLayer.BLOCK,root.north().above());System.out.println("LAB_STORAGE_LIGHT_DIAGNOSTIC index="+i+" server_front_light="+front+" server_lamp_light="+level.getBrightness(LightLayer.BLOCK,lamp)+" lamp_state="+level.getBlockState(lamp));if(front>=12&&clientLight<12)sendSceneLight(player);}finally{lightingDiagnosticPending=false;}});
    }
    private static void verifyServerMenu(Minecraft mc){
        verificationRequested=true;var uuid=mc.player.getUUID();int current=index;
        mc.getSingleplayerServer().execute(()->{
            try{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);var inventory=(LabStorageInventory)player.serverLevel().getBlockEntity(ROOTS.get(current));
                if(!(player.containerMenu instanceof ChestMenu menu)||menu.getContainer()!=inventory||menu.getRowCount()!=3||menu.slots.size()!=63)throw new IllegalStateException("Server menu did not bind the clicked 27-slot inventory: "+KEYS[current]);
                var tag=inventory.saveInventoryTag();if(tag.contains("LootTable",8)||tag.getList("Items",10).isEmpty())throw new IllegalStateException("First real interaction did not generate room loot: "+KEYS[current]);
                int light=player.serverLevel().getBrightness(LightLayer.BLOCK,ROOTS.get(current).north().above());if(light<12)throw new IllegalStateException("Server fixture lighting is below 12 for "+KEYS[current]+": "+light);
                System.out.println("LAB_STORAGE_SCENE_LIGHT_PASS "+KEYS[current]+" server_front_block_light="+light);
                expectedMenuId=menu.containerId;verified=true;
            }catch(Exception error){failure=error.toString();}
        });
    }
    private static void prepareOpenModel(Minecraft mc){
        modelRequested=true;mc.options.hideGui=true;var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
            try{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);player.closeContainer();position(player,0);var level=player.serverLevel();var root=ROOTS.get(0);
                var profile=new GameProfile(UUID.randomUUID(),"StorageViewer");modelViewer=new ServerPlayer(player.server,level,profile);modelViewer.connection=FakePlayerFactory.get(level,profile).connection;
                modelViewer.setPos(root.getX()+.5,root.getY(),root.getZ()+2.5);modelViewer.setGameMode(GameType.SURVIVAL);level.addNewPlayer(modelViewer);use(modelViewer,root);
                var inventory=(LabStorageInventory)level.getBlockEntity(root);if(!(modelViewer.containerMenu instanceof ChestMenu menu)||menu.getContainer()!=inventory||inventory.openerCount()<1)throw new IllegalStateException("Open-model photo has no real menu viewer");
                modelReady=true;
            }catch(Exception error){failure=error.toString();}
        });
    }
    private static void capture(Minecraft mc,String name)throws Exception{
        var folder=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(folder);try(var frame=Screenshot.takeScreenshot(mc.getMainRenderTarget())){frame.writeToFile(folder.resolve(name+".png"));}CAPTURED.add(name);System.out.println("LAB_STORAGE_CAPTURE "+name);
    }
    private static void finish(Minecraft mc,String result){
        finished=true;System.out.println(result);try{Files.writeString(mc.gameDirectory.toPath().resolve("storage-visual-result.txt"),result+"\nshots="+String.join(",",CAPTURED)+"\n");}catch(Exception error){throw new RuntimeException(error);}mc.stop();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!enabled()||finished||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(started==0)started=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){
            launched=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.renderDistance().set(6);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
        }
        if(failure!=null){finish(mc,"LAB_STORAGE_VISUAL_FAIL "+failure);return;}
        if(sceneBuilt&&!ready&&++lightingWaitTicks%10==0)awaitServerLighting(mc);
        if(ready&&mc.player!=null&&mc.level!=null){
            age++;localCamera(mc,index<KEYS.length?index:0);
            if(index<KEYS.length){
                if(phase==0){
                    boolean compiled=mc.levelRenderer.isChunkCompiled(ROOTS.get(index));
                    if(sceneLoaded(mc)&&compiled&&frontLight(mc,index)>=12&&mc.levelRenderer.hasRenderedAllChunks()&&mc.screen==null&&mc.getOverlay()==null)stable++;else stable=0;
                    if(stable>=20&&age>=40&&mc.gameMode!=null){
                        var from=mc.player.getEyePosition(1);var direction=target(index).subtract(from).normalize();var hit=mc.level.clip(new ClipContext(from,from.add(direction.scale(5)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
                        var expected=block(KEYS[index]) instanceof LabFixtureBlock?ROOTS.get(index).above():ROOTS.get(index);
                        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(expected)){failure="Real ray did not hit the intended storage root/part: "+KEYS[index]+" / "+hit.getBlockPos()+" expected "+expected;return;}
                        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);phase=1;age=0;stable=0;
                    }
                }else if(phase==1&&mc.screen instanceof ContainerScreen screen){
                    var menu=screen.getMenu();if(menu.getType()!=MenuType.GENERIC_9x3||menu.getRowCount()!=3||menu.slots.size()!=63){failure="Client did not receive vanilla 27+36 slots for "+KEYS[index];return;}
                    if(!verificationRequested)verifyServerMenu(mc);
                    boolean visibleLoot=false;for(int slot=0;slot<27;slot++)visibleLoot|=!menu.getSlot(slot).getItem().isEmpty();
                    if(verified&&menu.containerId==expectedMenuId&&visibleLoot&&age>=20)stable++;else stable=0;
                    if(stable>=10){try{capture(mc,"menu_"+KEYS[index]);System.out.println("LAB_STORAGE_MENU_PASS "+KEYS[index]+" rows=3 storage_slots=27 player_slots=36 container_id="+menu.containerId);mc.player.closeContainer();phase=2;age=0;stable=0;}catch(Exception error){failure=error.toString();}}
                }else if(phase==2&&mc.screen==null&&age>=12){
                    index++;phase=0;age=0;stable=0;verified=false;verificationRequested=false;
                    if(index<KEYS.length){var uuid=mc.player.getUUID();int next=index;mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);if(p!=null)position(p,next);});}
                }
            }else{
                if(!modelRequested)prepareOpenModel(mc);
                var be=mc.level.getBlockEntity(ROOTS.get(0));boolean open=be instanceof LabFixtureEntity fixture&&fixture.openProgress>.95F&&mc.level.getBlockState(ROOTS.get(0)).getValue(LabFixtureBlock.OPEN);
                if(modelReady&&sceneLoaded(mc)&&open&&frontLight(mc,0)>=12&&mc.screen==null&&mc.levelRenderer.isChunkCompiled(ROOTS.get(0))&&mc.levelRenderer.hasRenderedAllChunks())stable++;else stable=0;
                if(stable>=20){try{mc.options.hideGui=true;capture(mc,"model_open_lab_cabinet");finish(mc,"LAB_STORAGE_VISUAL_PASS menus="+KEYS.length+" screenshots="+(KEYS.length+1)+" storage_slots=27 player_slots=36 real_open_model=true");return;}catch(Exception error){failure=error.toString();}}
            }
            if(age%100==0){int selected=index<KEYS.length?index:0;System.out.println("LAB_STORAGE_VISUAL_WAIT index="+index+" phase="+phase+" stable="+stable+" front_block_light="+frontLight(mc,selected)+" screen="+(mc.screen==null?"none":mc.screen.getClass().getSimpleName()));diagnoseLighting(mc,selected);}
        }
        if(System.currentTimeMillis()-started>360000)finish(mc,"LAB_STORAGE_VISUAL_FAIL timeout index="+index+" phase="+phase);
    }
}
