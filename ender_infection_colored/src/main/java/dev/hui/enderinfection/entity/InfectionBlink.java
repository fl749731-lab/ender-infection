package dev.hui.enderinfection.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class InfectionBlink {
    private static final String READY_AT = "EnderBlinkReadyAt";

    private InfectionBlink() {}

    static boolean avoidsProjectile(Mob mob, DamageSource source) {
        var projectile = source.getDirectEntity();
        if (!source.is(DamageTypeTags.IS_PROJECTILE) && !(projectile instanceof Projectile)) {
            return false;
        }
        if (projectile instanceof ThrownPotion potion && PotionUtils.getPotion(potion.getItem()) == Potions.WATER
            && PotionUtils.getMobEffects(potion.getItem()).isEmpty()) {
            return false;
        }
        if (mob.level() instanceof ServerLevel level && ready(mob, level)) {
            search(mob, level, mob.position(), 3, 7, null, 120);
        }
        return true;
    }

    static boolean pursue(Mob mob, LivingEntity target, boolean ranged) {
        if (!(mob.level() instanceof ServerLevel level) || mob.tickCount < 60 || mob.tickCount % 10 != 0
            || target == null || !target.isAlive() || !ready(mob, level) || !mob.hasLineOfSight(target)) {
            return false;
        }
        double distance = mob.distanceToSqr(target);
        if (distance < (ranged ? 100 : 36) || distance > 196) {
            return false;
        }
        double range = ranged ? 6 : Math.max(2.5, (mob.getBbWidth() + target.getBbWidth()) / 2 + 1);
        return search(mob, level, target.position(), range, range + 1.5, target, 160);
    }

    private static boolean ready(Mob mob, ServerLevel level) {
        if (!mob.isAlive() || mob.isNoAi() || mob.isPassenger() || mob.isVehicle() || mob.isLeashed()
            || level.getGameTime() < mob.getPersistentData().getLong(READY_AT)) {
            return false;
        }
        if (mob instanceof InfectedMob infected) {
            return !infected.kind.stationary() && !infected.emerging();
        }
        return !(mob instanceof MutantZombie mutant) || !mutant.emerging();
    }

    private static boolean search(Mob mob, ServerLevel level, Vec3 center, double minRadius, double maxRadius,
                                  LivingEntity target, int cooldown) {
        // Block repeated scans from a projectile burst even when all nearby landing spots are blocked.
        mob.getPersistentData().putLong(READY_AT, level.getGameTime() + 30);
        double angle = level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 12; i++) {
            double radius = minRadius + level.random.nextDouble() * (maxRadius - minRadius);
            double a = angle + i * Math.PI / 6;
            BlockPos column = BlockPos.containing(center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            for (int offset : new int[]{0, 1, -1, 2, -2}) {
                BlockPos foot = column.above(offset);
                Vec3 destination = Vec3.atBottomCenterOf(foot);
                double travel = mob.position().distanceToSqr(destination);
                if (travel < 4 || travel > 256 || (target != null
                    && destination.distanceToSqr(target.position()) >= mob.distanceToSqr(target))) {
                    continue;
                }
                BlockPos ground = foot.below();
                var box = mob.getBoundingBox().move(destination.subtract(mob.position()));
                if (level.isOutsideBuildHeight(ground) || box.maxY >= level.getMaxBuildHeight()
                    || !loaded(level, box) || !level.getWorldBorder().isWithinBounds(box)
                    || !level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)
                    || !level.noCollision(mob, box) || level.containsAnyLiquid(box)) {
                    continue;
                }

                Vec3 previous = mob.position();
                mob.teleportTo(destination.x, destination.y, destination.z);
                mob.setDeltaMovement(Vec3.ZERO);
                mob.fallDistance = 0;
                if (mob instanceof InfectedMob infected) {
                    infected.interruptForOrders();
                } else if (mob instanceof MutantZombie mutant) {
                    mutant.interruptForOrders();
                }
                mob.getPersistentData().putLong(READY_AT, level.getGameTime() + cooldown);
                level.sendParticles(ParticleTypes.PORTAL, previous.x, previous.y + 1, previous.z, 22, .3, .6, .3, .2);
                level.sendParticles(ParticleTypes.PORTAL, destination.x, destination.y + 1, destination.z, 22, .3, .6, .3, .2);
                level.playSound(null, foot, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1F, 1F);
                return true;
            }
        }
        return false;
    }

    private static boolean loaded(ServerLevel level, AABB box) {
        int minX = ((int) Math.floor(box.minX)) >> 4;
        int maxX = ((int) Math.floor(box.maxX)) >> 4;
        int minZ = ((int) Math.floor(box.minZ)) >> 4;
        int maxZ = ((int) Math.floor(box.maxZ)) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
