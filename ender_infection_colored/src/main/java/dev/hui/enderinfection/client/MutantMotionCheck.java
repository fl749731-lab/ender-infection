package dev.hui.enderinfection.client;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.*;
import dev.hui.enderinfection.combat.AttackSpec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in real walking, native startAttack/hit resolution, emergence and death in a fresh world.
 * Attack selection is deterministic in this fixture; ordinary AI remains covered by GameTests. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class MutantMotionCheck {
    private static final String WORLD="MutantMotion_"+System.currentTimeMillis();
    private static final int FLOOR=-60;
    private record Shot(String phase,int delay,AttackSpec attack){}
    private static final List<Shot> SHOTS=new ArrayList<>();
    private static boolean launched,finished,actionSent;private static volatile boolean ready,acted;
    private static volatile String failure;private static volatile int entityId,victimId;
    private static int index,age,phaseTicks;private static long started;
    private static MutantZombie subject;private static Cow victim;
    private static boolean enabled(){return Boolean.getBoolean("ender_infection.mutantMotionCheck");}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player)||!player.server.getWorldData().getLevelName().equals(WORLD))return;
        try{
            var level=player.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,player.server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,player.server);level.setDayTime(6000);
            level.getDataStorage().computeIfAbsent(WorldIntroduction.Data::load,()->WorldIntroduction.Data.forWorldAge(200),"ender_infection_world_introduction").complete();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
            for(var p:BlockPos.betweenClosed(new BlockPos(-8,FLOOR-1,-8),new BlockPos(30,FLOOR-1,30)))level.setBlock(p,Blocks.SMOOTH_STONE.defaultBlockState(),2);
            for(String phase:List.of("idle_front","idle_back","walk_a","walk_b"))SHOTS.add(new Shot(phase,phase.equals("walk_a")?8:phase.equals("walk_b")?14:20,null));
            for(AttackSpec attack:AttackSpec.values()){
                SHOTS.add(new Shot("attack_"+attack.animation+"_windup",attack.hitTick-6,attack));
                SHOTS.add(new Shot("attack_"+attack.animation+"_impact",attack.hitTick+(attack==AttackSpec.PULSE?9:1),attack));
            }
            SHOTS.add(new Shot("infection",12,null));SHOTS.add(new Shot("death_start",4,null));SHOTS.add(new Shot("death_end",16,null));
            player.setGameMode(GameType.SPECTATOR);player.getInventory().clearContent();prepare(player);
        }catch(Exception error){failure=error.toString();}
    }
    private static void prepare(ServerPlayer player){
        ready=false;acted=false;if(subject!=null)subject.discard();if(victim!=null)victim.discard();victim=null;victimId=-1;
        var shot=SHOTS.get(index);var level=player.serverLevel();
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(-8,FLOOR-3,-8,30,FLOOR+12,30)).forEach(net.minecraft.world.entity.Entity::discard);
        subject=Objects.requireNonNull(EnderInfection.MUTANT_ZOMBIE.get().create(level));
        var tag=new CompoundTag();subject.addAdditionalSaveData(tag);tag.putInt("EmergenceTicks",0);subject.readAdditionalSaveData(tag);
        // A path can only be requested after the fresh walker has naturally landed.
        subject.moveTo(8,FLOOR,8,0,0);subject.setNoAi(!shot.phase.startsWith("walk"));subject.setPersistenceRequired();level.addFreshEntity(subject);entityId=subject.getId();
        if(shot.phase.startsWith("attack")){
            victim=Objects.requireNonNull(EntityType.COW.create(level));victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);victim.setHealth(100);victim.moveTo(8,FLOOR,10.5,0,0);victim.setNoAi(true);victim.setPersistenceRequired();level.addFreshEntity(victim);victimId=victim.getId();
        }
        player.teleportTo(level,11,FLOOR+2,12,135,15);System.out.println("MUTANT_MOTION_PREPARE "+"mutant_zombie_"+shot.phase+" entity="+entityId);ready=true;
    }
    private static void act(ServerPlayer player){
        try{
            var shot=SHOTS.get(index);var level=player.serverLevel();
            if(shot.phase.startsWith("walk")){subject.setNoAi(false);if(!subject.getNavigation().moveTo(14,FLOOR,8,1.0))throw new IllegalStateException("No walk path in the motion arena");}
            else if(shot.phase.startsWith("attack")){var method=MutantZombie.class.getDeclaredMethod("startAttack",AttackSpec.class,LivingEntity.class);method.setAccessible(true);method.invoke(subject,shot.attack,victim);System.out.println("MUTANT_MOTION_NATIVE_ATTACK "+shot.attack.animation+" hit_tick="+shot.attack.hitTick+" duration="+shot.attack.duration);}
            else if(shot.phase.equals("infection")){var tag=new CompoundTag();subject.addAdditionalSaveData(tag);tag.putInt("EmergenceTicks",32);subject.readAdditionalSaveData(tag);}
            else if(shot.phase.startsWith("death"))subject.hurt(level.damageSources().generic(),10000);
            acted=true;
        }catch(Exception error){failure=error.toString();}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!enabled()||finished||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(started==0)started=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){
            launched=true;mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.options.fov().set(48);mc.options.renderDistance().set(5);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
        }
        if(failure!=null)throw new IllegalStateException("MUTANT_MOTION_FAIL "+failure);
        if(ready&&mc.level!=null&&mc.player!=null&&mc.screen==null&&mc.getOverlay()==null&&mc.level.getEntity(entityId) instanceof MutantZombie mob){
            var shot=SHOTS.get(index);double side=shot.phase.equals("idle_back")?-1:1;var focus=mob.position().add(0,1.1,0);var eye=focus.add(side*3.1,1.05,side*4.1);var delta=focus.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)Math.toDegrees(Math.atan2(-delta.y,delta.horizontalDistance()));
            var p=eye.subtract(0,mc.player.getEyeHeight(),0);mc.player.setPos(p.x,p.y,p.z);mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.yRotO=yaw;mc.player.xRotO=pitch;mc.player.setDeltaMovement(Vec3.ZERO);age++;
            if(!actionSent&&age>=12&&mc.levelRenderer.isChunkCompiled(mob.blockPosition())&&mc.levelRenderer.hasRenderedAllChunks()){
                actionSent=true;var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->act(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid)));
            }
            if(!acted)return;
            if(shot.phase.startsWith("attack")&&!mob.isAttacking())return;
            if(shot.phase.equals("infection")&&!mob.emerging())return;
            if(shot.phase.startsWith("death")){if(mob.deathTime<shot.delay)return;}else if(++phaseTicks<shot.delay)return;
            try{
                var folder=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(folder);String name="mutant_zombie_"+shot.phase;
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(name+".png"));}
                var target=mc.level.getEntity(victimId);double moved=mob.position().distanceTo(new Vec3(8,FLOOR,8));
                System.out.println("MUTANT_MOTION_CAPTURE "+name+" moved="+moved+" attacking="+mob.isAttacking()+" emerging="+mob.emerging()+" death_time="+mob.deathTime+" target_health="+(target instanceof Cow cow?cow.getHealth():-1));
            }catch(Exception error){throw new RuntimeException(error);}
            ready=false;actionSent=false;age=phaseTicks=0;
            if(++index==SHOTS.size()){finished=true;System.out.println("MUTANT_MOTION_PASS screenshots="+index);mc.stop();return;}
            var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{prepare(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid));}catch(Exception error){failure=error.toString();}});
        }
        if(System.currentTimeMillis()-started>300000)throw new IllegalStateException("MUTANT_MOTION_FAIL timeout index="+index);
    }
}
