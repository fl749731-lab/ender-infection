package dev.hui.enderinfection.rift;

import dev.hui.enderinfection.entity.*;
import dev.hui.enderinfection.particle.FactionEffects;
import dev.hui.enderinfection.design.DesignBehavior;
import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;
import java.util.EnumSet;

/** Telegraphs, safe landings and an anchor's local defensive network. */
public final class RiftCombat {
    private RiftCombat() {}
    private static final DustParticleOptions WARNING=new DustParticleOptions(new Vector3f(.75F,.25F,1),1.1F);
    public static boolean unit(InfectedMob m){return m.kind==InfectedKind.EARLY_ENDERMAN||m.kind==InfectedKind.ENDERMAN||m.kind==InfectedKind.ANCHOR_GUARD;}
    public static void bind(InfectedMob mob,BlockPos pos){mob.getPersistentData().putLong("RiftAnchor",pos.asLong());}
    public static RiftAnchorEntity anchor(InfectedMob mob){
        if(!(mob.level() instanceof ServerLevel level))return null;
        var tag=mob.getPersistentData();
        if(tag.contains("RiftAnchor")){
            var pos=BlockPos.of(tag.getLong("RiftAnchor"));
            return level.hasChunkAt(pos)&&pos.distSqr(mob.blockPosition())<=16*16&&level.getBlockEntity(pos) instanceof RiftAnchorEntity a?a:null;
        }
        if(mob.tickCount%20!=0)return null;
        var nearby=RiftAnchorEntity.nearby(level,mob.blockPosition(),16);
        if(nearby.isEmpty())return null;var a=nearby.get(0);bind(mob,a.getBlockPos());return a;
    }
    public static boolean suppressed(InfectedMob m){
        if(m.level().getGameTime()<m.getPersistentData().getLong("RiftSuppressedUntil"))return true;
        var a=anchor(m);return a!=null&&a.suppressed();
    }
    public static boolean guardProtected(InfectedMob m){var a=anchor(m);return m.kind==InfectedKind.ANCHOR_GUARD&&a!=null&&a.active()&&!suppressed(m)&&!m.attacking();}
    public static boolean accepts(InfectedMob mob,LivingEntity target){
        if(mob.kind!=InfectedKind.ANCHOR_GUARD)return true;
        var d=mob.getPersistentData();var home=BlockPos.of(d.getLong(d.contains("RiftAnchor")?"RiftAnchor":"RiftGuardHome"));
        return target.position().distanceToSqr(Vec3.atCenterOf(home))<=12*12;
    }
    public static void suppress(InfectedMob mob,int ticks){
        mob.getPersistentData().putLong("RiftSuppressedUntil",Math.max(mob.getPersistentData().getLong("RiftSuppressedUntil"),mob.level().getGameTime()+ticks));
        mob.getPersistentData().remove("RiftPhaseTicks");mob.getPersistentData().putLong("RiftPhaseReady",mob.level().getGameTime()+40);
        mob.interruptForOrders();if(mob.kind==InfectedKind.ANCHOR_GUARD)mob.setRiftExposed(true);
    }
    public static void tick(InfectedMob mob,ServerLevel level){
        if(!unit(mob))return;var d=mob.getPersistentData();
        if(mob.kind==InfectedKind.ANCHOR_GUARD){
            if(!d.contains("RiftGuardHome"))d.putLong("RiftGuardHome",mob.blockPosition().asLong());
            anchor(mob);mob.setRiftExposed(!guardProtected(mob));return;
        }
        int left=d.getInt("RiftPhaseTicks");if(left<=0)return;
        var target=mob.getTarget();
        if(suppressed(mob)||mob.attacking()||target==null||!FactionAI.enemy(mob,target)||!mob.hasLineOfSight(target)||!target.getUUID().equals(d.getUUID("RiftPhaseTarget"))){d.remove("RiftPhaseTicks");return;}
        var destination=new Vec3(d.getDouble("RiftPhaseX"),d.getDouble("RiftPhaseY"),d.getDouble("RiftPhaseZ"));
        mob.getNavigation().stop();
        if(left%3==0){
            level.sendParticles(WARNING,destination.x,destination.y+.12,destination.z,12,.55,.02,.55,0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,mob.getX(),mob.getY()+1,mob.getZ(),8,.25,.6,.25,.02);
        }
        d.putInt("RiftPhaseTicks",left-1);if(left>1)return;
        if(!safeLanding(mob,destination))return;
        var old=mob.position();var a=anchor(mob);boolean boosted=a!=null&&a.spend(2);
        mob.teleportTo(destination.x,destination.y,destination.z);mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;
        d.putLong("RiftPhaseReady",level.getGameTime()+(boosted?80:160));d.putInt("RiftPhaseCount",d.getInt("RiftPhaseCount")+1);
        FactionEffects.shatter(level,old,12);FactionEffects.shatter(level,destination,12);
        level.playSound(null,mob.blockPosition(),SoundEvents.ENDERMAN_TELEPORT,SoundSource.HOSTILE,1,.85F);
    }
    public static boolean combatStep(InfectedMob mob,LivingEntity target){
        if(mob.kind==InfectedKind.ANCHOR_GUARD){if(!accepts(mob,target)){mob.setTarget(null);mob.getNavigation().stop();return true;}return false;}
        if(!unit(mob))return false;
        var d=mob.getPersistentData();if(d.getInt("RiftPhaseTicks")>0)return true;
        if(mob.tickCount<50||mob.level().getGameTime()<d.getLong("RiftPhaseReady")||suppressed(mob)||!mob.hasLineOfSight(target))return false;
        boolean retreat=mob.getHealth()<mob.getMaxHealth()*.35F;double distance=mob.distanceTo(target);
        if(!retreat&&(distance<6||distance>14)||retreat&&distance>7)return false;
        for(int i=0;i<12;i++){
            double angle=(mob.getId()%8)*Math.PI/4+i*Math.PI/6,r=retreat?8:2.6;
            var column=BlockPos.containing(target.getX()+Math.cos(angle)*r,target.getY(),target.getZ()+Math.sin(angle)*r);
            for(int y:new int[]{0,1,-1,2,-2}){
                var dest=Vec3.atBottomCenterOf(column.offset(0,y,0));
                if(!safeLanding(mob,dest)||mob.position().distanceToSqr(dest)<4)continue;
                d.putDouble("RiftPhaseX",dest.x);d.putDouble("RiftPhaseY",dest.y);d.putDouble("RiftPhaseZ",dest.z);
                d.putUUID("RiftPhaseTarget",target.getUUID());d.putInt("RiftPhaseTicks",12);d.putLong("RiftPhaseReady",mob.level().getGameTime()+60);
                mob.getNavigation().stop();mob.triggerAnim("action","phase");FactionAI.task(mob,retreat?"相位撤退":"裂隙突袭","落点蓄能，可用抑制器打断");return true;
            }
        }
        d.putLong("RiftPhaseReady",mob.level().getGameTime()+30);return false;
    }
    public static boolean safeLanding(InfectedMob mob,Vec3 dest){
        if(!(mob.level() instanceof ServerLevel level))return false;
        var box=mob.getBoundingBox().move(dest.subtract(mob.position()));
        for(int x=((int)Math.floor(box.minX))>>4;x<=((int)Math.floor(box.maxX))>>4;x++)
            for(int z=((int)Math.floor(box.minZ))>>4;z<=((int)Math.floor(box.maxZ))>>4;z++)if(level.getChunkSource().getChunkNow(x,z)==null)return false;
        var foot=BlockPos.containing(dest);var ground=level.getBlockState(foot.below());
        if(!level.getWorldBorder().isWithinBounds(box)||level.isOutsideBuildHeight(foot)||box.maxY>=level.getMaxBuildHeight()
            ||!ground.isFaceSturdy(level,foot.below(),Direction.UP)||!level.noCollision(mob,box)||level.containsAnyLiquid(box))return false;
        if(ground.is(Blocks.MAGMA_BLOCK)||ground.is(Blocks.CACTUS)||ground.is(Blocks.CAMPFIRE)||ground.is(Blocks.SOUL_CAMPFIRE))return false;
        for(var p:BlockPos.betweenClosed(foot,BlockPos.containing(dest.add(0,mob.getBbHeight(),0))))if(level.getBlockState(p).is(BlockTags.FIRE))return false;
        return level.clip(new ClipContext(mob.getEyePosition(),dest.add(0,mob.getEyeHeight(),0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob)).getType()==HitResult.Type.MISS;
    }
    public static void impact(InfectedMob mob,ServerLevel level){
        var target=mob.getTarget();if(target==null)return;
        double reach=mob.kind==InfectedKind.ANCHOR_GUARD?3.3:mob.kind.radius;
        if(!FactionAI.enemy(mob,target)||!mob.hasLineOfSight(target)||!DesignBehavior.withinMeleeReach(mob,target,reach))return;
        if(target.hurt(mob.damageSources().mobAttack(mob),mob.kind.damage)){target.knockback(.5,mob.getX()-target.getX(),mob.getZ()-target.getZ());FactionEffects.shatter(level,target.position().add(0,1,0),12);}
    }
    public static final class GuardAnchorGoal extends Goal {
        private final InfectedMob mob;
        private int nextPath;
        public GuardAnchorGoal(InfectedMob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE));}
        private BlockPos home(){var d=mob.getPersistentData();return BlockPos.of(d.getLong(d.contains("RiftAnchor")?"RiftAnchor":"RiftGuardHome"));}
        @Override public boolean canUse(){return mob.kind==InfectedKind.ANCHOR_GUARD&&!mob.emerging()&&mob.getTarget()==null&&mob.getPersistentData().contains("RiftGuardHome")&&mob.position().distanceToSqr(Vec3.atCenterOf(home()))>25;}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){if(mob.tickCount>=nextPath){nextPath=mob.tickCount+10;var p=home();mob.getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,.9);FactionAI.task(mob,"守卫锚点","返回锚点附近");}}
    }
}
