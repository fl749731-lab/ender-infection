package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.combat.AttackSpec;
import dev.hui.enderinfection.combat.CombatEffects;
import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MutantZombie extends Monster implements GeoEntity {
    public void interruptForOrders(){getPersistentData().putInt("CrystalCastSequence",getPersistentData().getInt("CrystalCastSequence")+1);entityData.set(ATTACK,0);attackTicks=0;meleeCooldown=Math.max(meleeCooldown,10);getNavigation().stop();}
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(MutantZombie.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> EMERGING = SynchedEntityData.defineId(MutantZombie.class, EntityDataSerializers.BOOLEAN);
    private static final String PREFIX = "animation.ender_mutant_zombie.";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int attackTicks;
    private int emergenceTicks=32;
    private boolean emergenceStarted;
    private int meleeCooldown;
    private int pulseCooldown = 60;
    private Vec3 attackForward = new Vec3(0, 0, 1);
    private final Set<UUID> hitVictims = new HashSet<>();

    public MutantZombie(EntityType<? extends MutantZombie> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 100)
            .add(Attributes.MOVEMENT_SPEED, 0.23).add(Attributes.ATTACK_DAMAGE, 10)
            .add(Attributes.ARMOR, 6).add(Attributes.KNOCKBACK_RESISTANCE, 0.75)
            .add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ATTACK, 0);
        entityData.define(EMERGING,true);
    }

    private FactionAI.HuntGoal factionHunt;
    private FactionAI.Motion factionMotion;
    @Override public boolean isAlliedTo(net.minecraft.world.entity.Entity other){return FactionAI.infected(other)||super.isAlliedTo(other);}
    @Override public boolean hurt(DamageSource source,float amount){if(FactionAI.infected(source.getEntity()))return false;boolean hit=super.hurt(source,amount);if(hit&&isAlive()&&source.getEntity() instanceof LivingEntity attacker)FactionAI.alert(this,attacker);return hit;}
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MutantCombatGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.75));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        factionHunt=new FactionAI.HuntGoal(this);
        factionMotion=new FactionAI.Motion(this,factionHunt);
        targetSelector.addGoal(1,factionHunt);
        goalSelector.addGoal(4,new FactionAI.ReturnToCoreGoal(this));
        goalSelector.addGoal(2,new FactionAI.InvestigateGoal(this));
        goalSelector.addGoal(3,new FactionAI.GuardMotherGoal(this));
    }

    public boolean isAttacking() { return entityData.get(ATTACK) != 0; }
    public boolean emerging(){return entityData.get(EMERGING);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("EmergenceTicks",emergenceTicks);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);if(getAttribute(Attributes.FOLLOW_RANGE)!=null&&getAttribute(Attributes.FOLLOW_RANGE).getBaseValue()<40)getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40);emergenceTicks=tag.getInt("EmergenceTicks")>0?32:0;entityData.set(EMERGING,emergenceTicks>0);emergenceStarted=false;}

    private void startAttack(AttackSpec attack, LivingEntity target) {
        Vec3 direction = target.position().subtract(position()).multiply(1, 0, 1);
        if (direction.lengthSqr() < 0.0001) direction = getLookAngle().multiply(1, 0, 1);
        attackForward = direction.normalize();
        float yaw = (float)Math.toDegrees(Math.atan2(-attackForward.x, attackForward.z));
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        attackTicks = 0;
        hitVictims.clear();
        entityData.set(ATTACK, attack.id);
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        triggerAnim("action", attack.animation);
        CombatEffects.begin(this, attack);
        if(attack==AttackSpec.SLAM)dev.hui.enderinfection.combat.CrystalAttacks.cast(this,InfectedKind.EARLY_ZOMBIE,target,attack.hitTick);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel) || !isAlive()) return;
        if(emergenceTicks>0){
            FactionEffects.emerge((ServerLevel)level(),position(),emergenceTicks);
            if(!emergenceStarted){triggerAnim("action","infection");emergenceStarted=true;}
            getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);
            if(--emergenceTicks==0)entityData.set(EMERGING,false);return;
        }
        FactionAI.support(this);
        if (meleeCooldown > 0) meleeCooldown--;
        if (pulseCooldown > 0) pulseCooldown--;
        AttackSpec attack = AttackSpec.byId(entityData.get(ATTACK));
        if (attack == null) return;
        ++attackTicks;
        // Attack direction is committed at the start; players can sidestep the wind-up.
        float yaw = (float)Math.toDegrees(Math.atan2(-attackForward.x, attackForward.z));
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        CombatEffects.tick(this, attack, attackTicks, attackForward);
        if (attack.resolvesAt(attackTicks)) resolveHit(attack);
        if (attackTicks >= attack.duration) {
            entityData.set(ATTACK, 0);
            meleeCooldown = attack == AttackSpec.PULSE ? 18 : attack.cooldown;
            if (attack == AttackSpec.PULSE) pulseCooldown = attack.cooldown;
        }
    }

    private void resolveHit(AttackSpec attack) {
        Vec3 center = attack == AttackSpec.SLAM ? position().add(attackForward.scale(1.6)) : position();
        double activeRadius = attack.activeRadius(attackTicks);
        double searchRadius = attack.radius + (attack == AttackSpec.SLAM ? 1.6 : 0);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
            getBoundingBox().inflate(searchRadius, 2, searchRadius), this::canDamage)) {
            Vec3 delta = victim.position().subtract(center);
            if (Math.abs(delta.y) > 2.5 || !hasLineOfSight(victim)) continue;
            boolean inside = attack == AttackSpec.SWEEP
                ? AttackSpec.inSector(delta.x, delta.z, attackForward.x, attackForward.z, attack.radius)
                : delta.x * delta.x + delta.z * delta.z <= activeRadius * activeRadius;
            if (!inside) continue;
            // A pulse expands across several ticks but never damages the same entity twice.
            if (!hitVictims.add(victim.getUUID())) continue;
            if (victim.hurt(damageSources().mobAttack(this), attack.damage)) {
                Vec3 push = victim.position().subtract(position()).multiply(1, 0, 1);
                if (push.lengthSqr() < 0.0001) push = attackForward;
                // knockback() subtracts its input direction, hence the negated outward vector.
                victim.knockback(attack.knockback, -push.x, -push.z);
                if (attack == AttackSpec.PULSE)
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            }
        }
    }

    private boolean canDamage(LivingEntity victim) {
        return FactionAI.enemy(this,victim);
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ZOMBIE_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ZOMBIE_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ZOMBIE_DEATH; }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "locomotion", 4, state -> {
            if (isAttacking()||emerging()) return PlayState.STOP;
            return state.setAndContinue(state.isMoving()
                ? RawAnimation.begin().thenLoop(PREFIX + "drag_walk")
                : RawAnimation.begin().thenLoop(PREFIX + "idle"));
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
            .triggerableAnim("claw_sweep", RawAnimation.begin().thenPlay(PREFIX + "claw_sweep"))
            .triggerableAnim("heavy_slam", RawAnimation.begin().thenPlay(PREFIX + "heavy_slam"))
            .triggerableAnim("crystal_burst", RawAnimation.begin().thenPlay(PREFIX + "crystal_burst"))
            .triggerableAnim("infection",RawAnimation.begin().thenPlay(PREFIX+"infection")));
    }

    private final class MutantCombatGoal extends Goal {
        MutantCombatGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            if(emerging())return false;
            LivingEntity target = getTarget();
            return isAttacking() || (target != null && target.isAlive());
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { getNavigation().stop(); factionMotion.reset(); }
        @Override public void tick() {
            if (isAttacking()) { getNavigation().stop(); return; }
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive()) return;
            getLookControl().setLookAt(target, 20, 20);
            double distance = distanceTo(target);
            if (meleeCooldown > 0 || !hasLineOfSight(target)) {
                factionMotion.chase(target, false);
                return;
            }
            if (pulseCooldown == 0 && distance <= 6.0 && distance > 2.7) {
                startAttack(AttackSpec.PULSE, target);
            } else if (distance <= 3.2) {
                startAttack(random.nextFloat() < 0.45F ? AttackSpec.SLAM : AttackSpec.SWEEP, target);
            } else factionMotion.chase(target, false);
        }
    }
}
