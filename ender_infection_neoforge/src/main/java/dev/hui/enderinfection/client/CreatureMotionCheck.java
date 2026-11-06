package dev.hui.enderinfection.client;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Opt-in real movement, AI attack, emergence and death; nine fresh subjects per kind. */
@EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class CreatureMotionCheck {
    private static final String WORLD="CreatureMotion_"+System.currentTimeMillis();
    private static final int FLOOR=-60;
    private record Shot(InfectedKind kind,String phase,int delay){}
    private static final List<Shot> SHOTS=new ArrayList<>();
    private static boolean launched,finished,actionSent;private static volatile boolean ready,acted;
    private static volatile String failure;private static volatile int entityId,victimId;
    private static int index,age,phaseTicks;private static long started;
    private static InfectedMob subject;private static Cow victim;
    private static boolean enabled(){return Boolean.getBoolean("ender_infection.creatureMotionCheck");}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player)||!player.server.getWorldData().getLevelName().equals(WORLD))return;
        try{
            var level=player.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,player.server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,player.server);level.setDayTime(6000);
            level.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(()->WorldIntroduction.Data.forWorldAge(200),(tag,registries)->WorldIntroduction.Data.load(tag),null),"ender_infection_world_introduction").complete();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
            for(var p:BlockPos.betweenClosed(new BlockPos(-8,FLOOR-1,-8),new BlockPos(30,FLOOR-1,30)))level.setBlock(p,Blocks.SMOOTH_STONE.defaultBlockState(),2);
            for(String key:System.getProperty("ender_infection.creatureMotionSubjects","spider").split(",")){
                var kind=Arrays.stream(InfectedKind.values()).filter(k->k.key.equals(key)).findFirst().orElseThrow();
                String[] phases={"idle_front","idle_back","walk_a","walk_b","attack_windup","attack_impact","infection","death_start","death_end"};int[] delays={20,20,8,14,8,15,12,4,16};
                for(int i=0;i<phases.length;i++)SHOTS.add(new Shot(kind,phases[i],delays[i]));
            }
            player.setGameMode(GameType.SPECTATOR);player.getInventory().clearContent();prepare(player);
        }catch(Exception error){failure=error.toString();}
    }
    private static void prepare(ServerPlayer player){
        ready=false;acted=false;if(subject!=null)subject.discard();if(victim!=null)victim.discard();victim=null;victimId=-1;
        var shot=SHOTS.get(index);var level=player.serverLevel();subject=Objects.requireNonNull(InfectedContent.TYPES.get(shot.kind).get().create(level));
        var tag=new CompoundTag();subject.addAdditionalSaveData(tag);tag.putInt("EmergenceTicks",0);subject.readAdditionalSaveData(tag);
        // A path can only be requested after the fresh walker has naturally landed.
        subject.moveTo(8,FLOOR,8,0,0);subject.setNoAi(!shot.phase.startsWith("walk"));subject.setPersistenceRequired();level.addFreshEntity(subject);entityId=subject.getId();
        if(shot.phase.startsWith("attack")){victim=Objects.requireNonNull(EntityType.COW.create(level));victim.moveTo(8,FLOOR,10.5,0,0);victim.setNoAi(true);victim.setPersistenceRequired();level.addFreshEntity(victim);victimId=victim.getId();}
        player.teleportTo(level,11,FLOOR+2,12,135,15);System.out.println("CREATURE_MOTION_PREPARE "+shot.kind.key+"_"+shot.phase+" entity="+entityId);ready=true;
    }
    private static void act(ServerPlayer player){
        try{
            var shot=SHOTS.get(index);var level=player.serverLevel();
            if(shot.phase.startsWith("walk")){subject.setNoAi(false);if(!subject.getNavigation().moveTo(14,FLOOR,8,1.0))throw new IllegalStateException("No walk path in the motion arena");}
            else if(shot.phase.startsWith("attack")){subject.setNoAi(false);subject.setTarget(victim);}
            else if(shot.phase.equals("infection")){var tag=new CompoundTag();subject.addAdditionalSaveData(tag);tag.putInt("EmergenceTicks",32);subject.readAdditionalSaveData(tag);}
            else if(shot.phase.startsWith("death"))subject.hurt(level.damageSources().generic(),10000);
            acted=true;
        }catch(Exception error){failure=error.toString();}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled()||finished)return;var mc=Minecraft.getInstance();if(started==0)started=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){
            launched=true;mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.options.fov().set(48);mc.options.renderDistance().set(5);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), null);return;
        }
        if(failure!=null)throw new IllegalStateException("CREATURE_MOTION_FAIL "+failure);
        if(ready&&mc.level!=null&&mc.player!=null&&mc.screen==null&&mc.getOverlay()==null&&mc.level.getEntity(entityId) instanceof InfectedMob mob){
            var shot=SHOTS.get(index);double side=shot.phase.equals("idle_back")?-1:1;var focus=mob.position().add(0,.65,0);var eye=focus.add(side*2.4,.85,side*3.2);var delta=focus.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)Math.toDegrees(Math.atan2(-delta.y,delta.horizontalDistance()));
            var p=eye.subtract(0,mc.player.getEyeHeight(),0);mc.player.setPos(p.x,p.y,p.z);mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.yRotO=yaw;mc.player.xRotO=pitch;mc.player.setDeltaMovement(Vec3.ZERO);age++;
            if(!actionSent&&age>=12&&mc.levelRenderer.isSectionCompiled(mob.blockPosition())&&mc.levelRenderer.hasRenderedAllSections()){
                actionSent=true;var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->act(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid)));
            }
            if(!acted)return;
            if(shot.phase.startsWith("attack")&&!mob.attacking())return;
            if(shot.phase.equals("infection")&&!mob.emerging())return;
            if(shot.phase.startsWith("death")){if(mob.deathTime<shot.delay)return;}else if(++phaseTicks<shot.delay)return;
            try{
                var folder=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(folder);String name=shot.kind.key+"_"+shot.phase;
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(name+".png"));}
                var target=mc.level.getEntity(victimId);double moved=mob.position().distanceTo(new Vec3(8,FLOOR,8));
                System.out.println("CREATURE_MOTION_CAPTURE "+name+" moved="+moved+" attacking="+mob.attacking()+" emerging="+mob.emerging()+" death_time="+mob.deathTime+" target_health="+(target instanceof Cow cow?cow.getHealth():-1));
            }catch(Exception error){throw new RuntimeException(error);}
            ready=false;actionSent=false;age=phaseTicks=0;
            if(++index==SHOTS.size()){finished=true;System.out.println("CREATURE_MOTION_PASS screenshots="+index);mc.stop();return;}
            var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{prepare(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid));}catch(Exception error){failure=error.toString();}});
        }
        if(System.currentTimeMillis()-started>300000)throw new IllegalStateException("CREATURE_MOTION_FAIL timeout index="+index);
    }
}
