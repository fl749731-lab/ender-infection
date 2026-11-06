package dev.hui.enderinfection.client;

import com.mojang.authlib.GameProfile;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.design.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Fresh-world native geometry review of cupboard interiors and paired entrances. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class LabDoorVisualCheck {
    private static final String WORLD="LabDoorVisual_"+System.currentTimeMillis();
    private record Shot(String name,BlockPos root,Direction facing,boolean open,boolean pair,boolean side){}
    private static final List<Shot> SHOTS=new ArrayList<>();
    private static boolean launched,finished;private static volatile boolean ready;private static volatile String failure;
    private static int index,age,stable;private static long started;private static ServerPlayer viewer;
    private static boolean enabled(){return Boolean.getBoolean("ender_infection.labDoorVisualCheck");}
    private static Block block(String key){return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new ResourceLocation("ender_infection:"+key));}
    private static void put(ServerPlayer player,BlockPos root,String key,Direction facing,boolean pair){
        var level=player.serverLevel();var state=block(key).defaultBlockState().setValue(LabFixtureBlock.FACING,facing);level.setBlock(root,state,3);
        if(state.getBlock() instanceof LabFixtureBlock fixture)fixture.fillParts(level,root,state);
        if(pair){var mate=root.relative(facing.getClockWise());level.setBlock(mate,state,3);((LabFixtureBlock)state.getBlock()).fillParts(level,mate,state);}
    }
    private static void addPair(ServerPlayer player,BlockPos at,String key,Direction facing,boolean pair){
        put(player,at,key,facing,pair);
        for(boolean open:new boolean[]{false,true})SHOTS.add(new Shot(key+"_"+facing.getName()+"_"+(pair?"double":"single")+"_"+(open?"open":"closed"),at,facing,open,pair,false));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player)||!player.server.getWorldData().getLevelName().equals(WORLD))return;
        try{
            var level=player.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,player.server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,player.server);level.setDayTime(6000);
            for(int x=-1;x<=4;x++)for(int z=-1;z<=4;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
            for(var p:BlockPos.betweenClosed(new BlockPos(-4,-61,-4),new BlockPos(60,-61,60)))level.setBlock(p,Blocks.SMOOTH_STONE.defaultBlockState(),2);
            int n=0;for(Direction facing:Direction.Plane.HORIZONTAL)addPair(player,new BlockPos(5+n++*12,-60,6),"lab_door",facing,true);
            addPair(player,new BlockPos(5,-60,18),"lab_door_eroded",Direction.NORTH,true);addPair(player,new BlockPos(17,-60,18),"lab_door",Direction.NORTH,false);
            String[] cabinets={"lab_cabinet","lab_cabinet_eroded","lab_glass_cabinet","lab_cryo_cabinet","lab_server_rack"};
            for(int i=0;i<cabinets.length;i++){
                var at=new BlockPos(5+i*11,-60,32);put(player,at,cabinets[i],Direction.NORTH,false);
                SHOTS.add(new Shot(cabinets[i]+"_open",at,Direction.NORTH,true,false,true));
                if(cabinets[i].equals("lab_cryo_cabinet"))SHOTS.add(new Shot(cabinets[i]+"_closed",at,Direction.NORTH,false,false,true));
            }
            addPair(player,new BlockPos(29,-60,18),"lab_privacy_curtain",Direction.NORTH,false);
            // Same freezer + wall-monitor stack used in the atrium reception desk.
            var desk=new BlockPos(17,-60,48);put(player,desk,"lab_freezer",Direction.SOUTH,false);put(player,desk.above(),"lab_monitor",Direction.SOUTH,false);
            SHOTS.add(new Shot("reception_front",desk,Direction.SOUTH,false,false,false));SHOTS.add(new Shot("reception_back",desk,Direction.NORTH,false,false,true));
            var profile=new GameProfile(UUID.randomUUID(),"DoorModelViewer");viewer=new ServerPlayer(player.server,level,profile);viewer.connection=FakePlayerFactory.get(level,profile).connection;viewer.setGameMode(GameType.SURVIVAL);level.addNewPlayer(viewer);
            player.setGameMode(GameType.CREATIVE);player.getInventory().clearContent();player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();prepare(player);
        }catch(Exception error){failure=error.toString();}
    }
    private static Vec3 target(Shot shot){return Vec3.atBottomCenterOf(shot.root).add(shot.pair?shot.facing.getClockWise().getStepX()*.5:0,1.05,shot.pair?shot.facing.getClockWise().getStepZ()*.5:0);}
    private static Vec3 camera(Shot shot){var at=target(shot);var side=shot.facing.getClockWise();return at.add(shot.facing.getStepX()*3.7+(shot.side?side.getStepX()*1.4:0),-.95,shot.facing.getStepZ()*3.7+(shot.side?side.getStepZ()*1.4:0));}
    private static float yaw(Shot shot){var d=target(shot).subtract(camera(shot));return (float)(Math.toDegrees(Math.atan2(d.z,d.x))-90);}
    private static float pitch(Shot shot){var d=target(shot).subtract(camera(shot).add(0,1.62,0));return (float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));}
    private static void prepare(ServerPlayer player){
        ready=false;viewer.closeContainer();var shot=SHOTS.get(index);var level=player.serverLevel();var state=level.getBlockState(shot.root);
        if(state.getBlock() instanceof LabFixtureBlock fixture){
            if(fixture.isStorage()&&shot.open){viewer.setPos(shot.root.getX()+.5,shot.root.getY(),shot.root.getZ()-2);fixture.use(state,level,shot.root,viewer,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(shot.root),Direction.NORTH,shot.root,false));}
            else if(state.getValue(LabFixtureBlock.OPEN)!=shot.open)fixture.use(state,level,shot.root,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(shot.root),Direction.NORTH,shot.root,false));
        }
        var c=camera(shot);player.teleportTo(level,c.x,c.y,c.z,yaw(shot),pitch(shot));
        System.out.println("LAB_DOOR_PREPARE "+shot.name+" state="+level.getBlockState(shot.root));ready=true;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!enabled()||finished||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(started==0)started=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){
            launched=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(6);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
        }
        if(failure!=null)throw new IllegalStateException("LAB_DOOR_VISUAL_FAIL "+failure);
        if(ready&&mc.player!=null&&mc.level!=null&&mc.screen==null&&mc.getOverlay()==null){
            var shot=SHOTS.get(index);var c=camera(shot);mc.player.setPos(c.x,c.y,c.z);mc.player.setYRot(yaw(shot));mc.player.setXRot(pitch(shot));mc.player.yRotO=yaw(shot);mc.player.xRotO=pitch(shot);mc.player.setYHeadRot(yaw(shot));mc.player.setDeltaMovement(0,0,0);mc.player.getAbilities().flying=true;age++;
            var state=mc.level.getBlockState(shot.root);boolean expected=!(state.getBlock() instanceof LabFixtureBlock)||state.getValue(LabFixtureBlock.OPEN)==shot.open;
            if(expected&&mc.levelRenderer.isChunkCompiled(shot.root)&&mc.levelRenderer.hasRenderedAllChunks()&&age>35)stable++;else stable=0;
            if(stable>=15){
                try{var folder=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(shot.name+".png"));}}catch(Exception error){throw new RuntimeException(error);}
                System.out.println("LAB_DOOR_CAPTURE "+shot.name);ready=false;age=stable=0;
                if(++index==SHOTS.size()){finished=true;System.out.println("LAB_DOOR_VISUAL_PASS screenshots="+index);mc.stop();return;}
                var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{prepare(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid));}catch(Exception error){failure=error.toString();}});
            }
        }
        if(System.currentTimeMillis()-started>360000)throw new IllegalStateException("LAB_DOOR_VISUAL_FAIL timeout index="+index);
    }
}
