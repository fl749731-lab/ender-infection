package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.institute.WardenEncounter;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Original facility boss. Attacks never break blocks or damage through walls. */
public final class ContainmentWarden extends Monster implements GeoEntity {
    public enum Attack {
        NONE(0, "idle"), CLAW(36, "claw"), STOMP(48, "stomp"), BURST(52, "burst");
        public final int duration; public final String clip;
        Attack(int duration, String clip) { this.duration = duration; this.clip = clip; }
    }
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(ContainmentWarden.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> EXPOSED = SynchedEntityData.defineId(ContainmentWarden.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(ContainmentWarden.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(.69F, .3F, .95F), 1.2F);
    private static final String PREFIX = "animation.containment_warden.";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.ender_infection.containment_warden"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private BlockPos facilityOrigin;
    private UUID encounterRun;
    private int attackTicks, cooldown = 40, nextAttack, exposureTicks, consoleMask, consoleCooldown;
    private float attackYaw;

    public ContainmentWarden(EntityType<? extends ContainmentWarden> type, Level level) {
        super(type, level); xpReward = 50; setPersistenceRequired();
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 360).add(Attributes.MOVEMENT_SPEED, .24)
            .add(Attributes.ATTACK_DAMAGE, 12).add(Attributes.ARMOR, 6).add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 28);
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(ACTION, 0); entityData.define(EXPOSED, false); entityData.define(ENRAGED, false);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this)); goalSelector.addGoal(1, new CombatGoal());
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    public Optional<BlockPos> facilityOrigin() { return Optional.ofNullable(facilityOrigin); }
    public Optional<UUID> encounterRun() { return Optional.ofNullable(encounterRun); }
    public void bindFacility(BlockPos origin, UUID run) { facilityOrigin = origin.immutable(); encounterRun = run; }
    public Attack attack() { int id = entityData.get(ACTION); return Attack.values()[Math.max(0, Math.min(3, id))]; }
    public int attackTicks() { return attackTicks; }
    public boolean exposed() { return entityData.get(EXPOSED); }
    public boolean enraged() { return entityData.get(ENRAGED); }
    public int consoleMask() { return consoleMask; }
    public int consoleCooldown() { return consoleCooldown; }
    public boolean fighting() { return level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL && (facilityOrigin == null || WardenEncounter.canFight(this)); }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean isAlliedTo(net.minecraft.world.entity.Entity other) { return other != null && (other instanceof ContainmentWarden || FactionAI.infected(other) || super.isAlliedTo(other)); }
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bar.removePlayer(player); }
    @Override public void remove(RemovalReason reason) { super.remove(reason); bar.removeAllPlayers(); }

    private boolean validTarget(LivingEntity victim) {
        if (victim == null || !victim.isAlive() || isAlliedTo(victim)) return false;
        if (victim instanceof Player p && (p.isSpectator() || p.isCreative())) return false;
        return facilityOrigin == null || WardenEncounter.inArena(this, victim.position());
    }
    @Override public boolean canAttack(LivingEntity target) { return super.canAttack(target) && validTarget(target); }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        entityData.set(ENRAGED, getHealth() <= getMaxHealth() * .6F);
        bar.setProgress(getHealth() / getMaxHealth());
        bar.setName(Component.translatable("entity.ender_infection.containment_warden"));
        bar.setColor(exposed() ? BossEvent.BossBarColor.WHITE : enraged() ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
        bar.setVisible(fighting());
        if (!fighting() || isNoAi()) { cancelAttack(); getNavigation().stop(); return; }
        if (consoleCooldown > 0) consoleCooldown--;
        if (exposureTicks > 0) {
            exposureTicks--; cancelAttack(); getNavigation().stop();
            if (exposureTicks == 0) { entityData.set(EXPOSED, false); consoleMask = 0; cooldown = 30; }
            return;
        }
        if (cooldown > 0) cooldown--;
        if (attack() != Attack.NONE) tickAttack(server);
    }
    private void cancelAttack() { entityData.set(ACTION, 0); attackTicks = 0; }
    private void beginAttack(Attack attack, LivingEntity target) {
        var delta = target.position().subtract(position());
        attackYaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        setYRot(attackYaw); yBodyRot = attackYaw; setYHeadRot(attackYaw);
        entityData.set(ACTION, attack.ordinal()); attackTicks = 0; getNavigation().stop();
        level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1F, attack == Attack.BURST ? .5F : .8F);
    }
    private void telegraph(ServerLevel level) {
        if (attack() == Attack.STOMP) {
            for (int i=0; i<24; i++) {
                double angle = i * Math.PI / 12;
                level.sendParticles(DUST, getX()+Math.cos(angle)*5.5, getY()+.12, getZ()+Math.sin(angle)*5.5, 1, 0,0,0,0);
            }
        } else {
            for (int ray=0; ray<(attack()==Attack.BURST?3:1); ray++) {
                double angle = Math.toRadians(attackYaw + (attack()==Attack.BURST ? (ray-1)*20 : 0));
                for (int i=2; i<=(attack()==Attack.BURST?12:4); i++)
                    level.sendParticles(DUST, getX()-Math.sin(angle)*i, getY()+.15, getZ()+Math.cos(angle)*i, 1,0,0,0,0);
            }
        }
    }
    private boolean cone(LivingEntity victim, float yaw, double halfAngle) {
        Vec3 delta = victim.position().subtract(position()).multiply(1,0,1);
        if (delta.lengthSqr() < .01) return true;
        double angle = Math.toRadians(yaw);
        return delta.normalize().dot(new Vec3(-Math.sin(angle),0,Math.cos(angle))) >= Math.cos(Math.toRadians(halfAngle));
    }
    private void strike(ServerLevel level, double range, float yaw, double halfAngle, float damage, boolean ground) {
        for (var victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range),
                e -> validTarget(e) && (e instanceof Player || e == getTarget()))) {
            if (distanceToSqr(victim) > range*range || Math.abs(victim.getY()-getY()) > 2 || !hasLineOfSight(victim)) continue;
            if (!cone(victim, yaw, halfAngle) || ground && (!victim.onGround() || Math.abs(victim.getY()-getY()) > 1.2)) continue;
            if (victim.hurt(damageSources().mobAttack(this), damage * (enraged() ? 1.2F : 1F)))
                victim.knockback(ground ? .7 : .45, getX()-victim.getX(), getZ()-victim.getZ());
        }
    }
    private void tickAttack(ServerLevel level) {
        attackTicks++; setYRot(attackYaw); yBodyRot = attackYaw; setYHeadRot(attackYaw); getNavigation().stop();
        Attack action = attack();
        if (attackTicks % 4 == 0 && attackTicks < (action == Attack.CLAW ? 17 : 20)) telegraph(level);
        if (action == Attack.CLAW && attackTicks == 17) {
            strike(level, 4.2, attackYaw, 60, 12, false);
            level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1, .65F);
        } else if (action == Attack.STOMP && attackTicks == 22) {
            strike(level, 5.5, attackYaw, 180, 10, true);
            level.sendParticles(ParticleTypes.CRIT, getX(),getY()+.2,getZ(),40,3,.2,3,.1);
            level.playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1, .65F);
        } else if (action == Attack.BURST && (attackTicks == 20 || attackTicks == 26 || attackTicks == 32)) {
            float yaw = attackYaw + ((attackTicks-20)/6-1)*20;
            strike(level, 12, yaw, 10, 8, false);
            double angle = Math.toRadians(yaw);
            for (int i=1; i<=12; i++) level.sendParticles(ParticleTypes.END_ROD,getX()-Math.sin(angle)*i,getY()+1.8,getZ()+Math.cos(angle)*i,2,.1,.15,.1,.01);
            level.playSound(null,blockPosition(),SoundEvents.AMETHYST_BLOCK_BREAK,SoundSource.HOSTILE,.8F,1.2F);
        }
        if (attackTicks >= action.duration) { cancelAttack(); cooldown = enraged() ? 24 : 40; }
    }
    /** Both independent consoles must discharge. Reusing one cannot open the core. */
    public boolean activateConsole(int index) {
        if (level().isClientSide || index < 0 || index > 1 || !isAlive() || !fighting() || consoleCooldown > 0 || exposed() || (consoleMask & 1<<index) != 0) return false;
        consoleMask |= 1<<index;
        if (consoleMask == 3) {
            exposureTicks = 100; consoleCooldown = 300; entityData.set(EXPOSED,true); cancelAttack(); getNavigation().stop();
            level().playSound(null,blockPosition(),SoundEvents.BEACON_DEACTIVATE,SoundSource.HOSTILE,1,.7F);
        }
        return true;
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (facilityOrigin != null && !fighting() || isAlliedTo(source.getEntity())) return false;
            amount *= exposed() ? 1.35F : .65F;
        }
        return super.hurt(source, amount);
    }
    @Override public void die(DamageSource source) { super.die(source); if (dead) { bar.setVisible(false); if (level() instanceof ServerLevel) WardenEncounter.defeated(this); } }
    @Override protected void tickDeath() {
        deathTime++;
        if (deathTime >= 60 && !level().isClientSide) { level().broadcastEntityEvent(this,(byte)60); remove(RemovalReason.KILLED); }
    }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (facilityOrigin != null && encounterRun != null) { tag.putLong("InstituteHome",facilityOrigin.asLong()); tag.putUUID("WardenRun",encounterRun); }
        tag.putInt("WardenExposure",exposureTicks); tag.putInt("WardenConsoles",consoleMask); tag.putInt("WardenConsoleCooldown",consoleCooldown); tag.putInt("WardenNextAttack",nextAttack);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        facilityOrigin = tag.contains("InstituteHome",4) && tag.hasUUID("WardenRun") ? BlockPos.of(tag.getLong("InstituteHome")) : null;
        encounterRun = facilityOrigin == null ? null : tag.getUUID("WardenRun");
        exposureTicks = Math.max(0,Math.min(100,tag.getInt("WardenExposure"))); consoleMask = tag.getInt("WardenConsoles")&3;
        consoleCooldown = Math.max(0,Math.min(300,tag.getInt("WardenConsoleCooldown"))); nextAttack = Math.floorMod(tag.getInt("WardenNextAttack"),3);
        entityData.set(EXPOSED,exposureTicks>0); entityData.set(ENRAGED,getHealth()<=getMaxHealth()*.6F); cancelAttack(); cooldown=40;
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this,"body",0,state -> {
            if (isDeadOrDying()) return state.setAndContinue(RawAnimation.begin().thenPlayAndHold(PREFIX+"death"));
            if (exposed()) return state.setAndContinue(RawAnimation.begin().thenLoop(PREFIX+"exposed"));
            if (attack()!=Attack.NONE) return state.setAndContinue(RawAnimation.begin().thenPlay(PREFIX+attack().clip));
            return state.setAndContinue(RawAnimation.begin().thenLoop(PREFIX+(state.isMoving()?"walk":"idle")));
        }));
    }
    private final class CombatGoal extends Goal {
        CombatGoal() { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean canUse() { return validTarget(getTarget()) && fighting(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { getNavigation().stop(); }
        @Override public void tick() {
            var target = getTarget(); if (!validTarget(target)) return;
            if (attack()!=Attack.NONE || exposed()) { getNavigation().stop(); return; }
            getLookControl().setLookAt(target,20,20);
            double distance = distanceTo(target);
            if (cooldown==0 && hasLineOfSight(target) && distance<=12) {
                Attack selected = distance>7 ? Attack.BURST : distance>4 ? Attack.STOMP : Attack.values()[1+nextAttack];
                nextAttack=(nextAttack+1)%3; beginAttack(selected,target);
            } else if (distance>3.2) getNavigation().moveTo(target,1); else getNavigation().stop();
        }
    }
}
