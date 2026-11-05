package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.design.DesignContent;
import dev.hui.enderinfection.item.InfectionItems;
import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;

/** Individual movement and target choices inside the colony's existing command system. */
final class BroodTactics {
    private final InfectedMob mob;
    private final FactionAI.Motion motion;
    private int repositionAt;

    BroodTactics(InfectedMob mob, FactionAI.Motion motion) {
        this.mob = mob;
        this.motion = motion;
    }

    static boolean accepts(InfectedMob mob, LivingEntity target, boolean urgent) {
        double distance = mob.distanceToSqr(target);
        return switch (mob.kind) {
            case ROOT_TURRET, HATCH_SAC -> distance <= mob.kind.radius * mob.kind.radius;
            // A normal hit can knock the carrier beyond four blocks before its next target scan.
            case CARRIER -> distance < 144 && urgent;
            case SPIDER_MOTHER, WALKING_NEST -> urgent || distance < 144;
            default -> true;
        };
    }

    static int targetPreference(InfectedMob mob, LivingEntity target) {
        return switch (mob.kind) {
            case FORAGER -> target instanceof net.minecraft.world.entity.animal.Animal ? 19 : 0;
            case LEAP_AMBUSHER -> target.getHealth() <= target.getMaxHealth() * .5F ? 10 : 0;
            default -> 0;
        };
    }

    boolean position(LivingEntity target, boolean ready, Vec3 pursuitPoint) {
        double distance = mob.distanceTo(target);
        boolean visible = mob.hasLineOfSight(target);
        if (mob.kind == InfectedKind.SPOREWING_SCOUT) {
            if (ready && visible && distance >= 4 && distance <= mob.kind.radius && FactionAI.clearShot(mob, target)) {
                return false;
            }
            flyAround(pursuitPoint, visible);
            return true;
        }
        if (!visible) return false;
        return switch (mob.kind) {
            case THROAT_SPITTER -> keepRange(target, distance, 7, 14, "喷吐阵位");
            case BURROW_KING -> keepRange(target, distance, 8, 16, "地刺阵位");
            case RESONANCE_PRIEST -> supportPosition(target, distance);
            case FORAGER -> mob.getHealth() < mob.getMaxHealth() * .35F
                && !FactionAI.recalled(mob) && distance < 6 && stepBack(target, 4, "退向虫群");
            default -> false;
        };
    }

    private void flyAround(Vec3 center, boolean visible) {
        if (mob.tickCount < repositionAt) return;
        repositionAt = mob.tickCount + 12;
        Vec3 radial = mob.position().subtract(center).multiply(1, 0, 1).normalize();
        if (radial.lengthSqr() < .01) radial = new Vec3(1, 0, 0);
        Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
        Vec3 destination = visible ? center.add(radial.scale(7)).add(tangent.scale(3)).add(0, 4, 0)
                                   : center.add(0, 4, 0);
        if (mob.level().hasChunkAt(BlockPos.containing(destination))) {
            mob.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.18);
            FactionAI.task(mob, "高空侦察", visible ? "绕飞并标记猎物" : "升高寻找最后目击点");
        }
    }

    private boolean keepRange(LivingEntity target, double distance, double min, double max, String role) {
        boolean blocked = mob.kind == InfectedKind.THROAT_SPITTER && !FactionAI.clearShot(mob, target);
        if (distance < min || blocked) return stepBack(target, distance < min ? 4 : 0, role);
        if (distance <= max) return false;
        if (mob.tickCount >= repositionAt) {
            repositionAt = mob.tickCount + 12;
            motion.chase(target, true);
            FactionAI.task(mob, role, "接近至施法距离");
        }
        return true;
    }

    private boolean stepBack(LivingEntity target, double backward, String role) {
        if (mob.tickCount < repositionAt) return !mob.getNavigation().isDone();
        repositionAt = mob.tickCount + 12;
        Vec3 away = mob.position().subtract(target.position()).multiply(1, 0, 1).normalize();
        if (away.lengthSqr() < .01) away = new Vec3(1, 0, 0);
        double side = mob.getId() % 2 == 0 ? 3 : -3;
        for (double offset : new double[]{side, -side, 0}) {
            Vec3 destination = mob.position().add(away.scale(backward)).add(-away.z * offset, 0, away.x * offset);
            if (FactionAI.walkTo(mob, destination, 1.22)) {
                FactionAI.task(mob, role, backward > 0 ? "主动拉开距离" : "让开友军射线");
                return true;
            }
        }
        return false;
    }

    private boolean supportPosition(LivingEntity target, double distance) {
        if (distance < 7) return stepBack(target, 4, "祭司撤回后排");
        if (distance <= 12 || FactionAI.recalled(mob)) return false;
        if (mob.tickCount < repositionAt) return !mob.getNavigation().isDone();
        repositionAt = mob.tickCount + 12;
        var screen = mob.level().getEntitiesOfClass(InfectedMob.class, mob.getBoundingBox().inflate(12, 4, 12),
            ally -> ally != mob && ally.isAlive() && ally.getTarget() == target && ally.distanceToSqr(target) < mob.distanceToSqr(target))
            .stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
        if (screen == null) return false;
        Vec3 behind = screen.position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(4);
        Vec3 destination = screen.position().add(behind);
        if (mob.position().distanceToSqr(destination) <= 4) {
            mob.getNavigation().stop();
            FactionAI.task(mob, "祭司支援", "留在交战友军身后");
            return distance > mob.kind.radius;
        }
        if (!FactionAI.walkTo(mob, destination, .95)) return false;
        FactionAI.task(mob, "祭司支援", "跟随交战友军");
        return true;
    }

    static void windup(InfectedMob mob, ServerLevel level, int tick, Vec3 forward) {
        var target = mob.getTarget();
        if (target == null) return;
        switch (mob.kind) {
            case LEAP_AMBUSHER -> {
                if (tick == 5 && mob.onGround()) {
                    double speed = Math.min(1.05, Math.max(.2, (mob.distanceTo(target) - 1) / 7));
                    mob.setDeltaMovement(forward.x * speed, .36, forward.z * speed);
                    FactionEffects.shatter(level, mob.position(), 8);
                    FactionAI.task(mob, "跃刃突袭", "起跳切入猎物");
                }
            }
            case BURROWER, SIEGEBREAKER -> {
                // Keep the heavy unit's ten-tick rush after its longer planted windup.
                int rushStart = mob.kind == InfectedKind.SIEGEBREAKER ? mob.kind.hitTick - 10 : 6;
                if (tick >= rushStart && tick < mob.kind.hitTick && mob.onGround() && mob.distanceTo(target) > mob.kind.radius * .68) {
                    double speed = mob.kind == InfectedKind.BURROWER ? .38 : .46;
                    mob.setDeltaMovement(forward.x * speed, mob.getDeltaMovement().y, forward.z * speed);
                    if (tick == rushStart) FactionAI.task(mob, "冲撞", "沿蓄力方向突进");
                    if (tick % 4 == 0) FactionEffects.shatter(level, mob.position(), 5);
                }
            }
            default -> {}
        }
    }

    static final class CargoGoal extends Goal {
        private static final String CARGO = "CarriedBiomass";
        private final InfectedMob carrier;
        private InfectedMob mother;
        private ItemEntity item;
        private int scanAt, moveAt;

        CargoGoal(InfectedMob carrier) {
            this.carrier = carrier;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean available() {
            return !carrier.emerging() && carrier.getTarget() == null && !FactionAI.recalled(carrier);
        }

        @Override
        public boolean canUse() {
            if (carrier.kind != InfectedKind.CARRIER || !available() || carrier.tickCount < scanAt) return false;
            scanAt = carrier.tickCount + 20;
            mother = FactionAI.home(carrier);
            if (mother == null) return false;
            item = carrier.getPersistentData().getInt(CARGO) >= 16 ? null
                : carrier.level().getEntitiesOfClass(ItemEntity.class, carrier.getBoundingBox().inflate(10, 4, 10), this::biomass)
                    .stream().min(Comparator.comparingDouble(carrier::distanceToSqr)).orElse(null);
            return item != null || carrier.getPersistentData().getInt(CARGO) > 0 && mother.colonyBiomass() < 10000;
        }

        @Override
        public boolean canContinueToUse() {
            return available() && mother != null && mother.isAlive()
                && (item == null ? carrier.getPersistentData().getInt(CARGO) > 0 && mother.colonyBiomass() < 10000
                                 : biomass(item)) && carrier.tickCount < scanAt + 140;
        }

        private boolean biomass(ItemEntity drop) {
            return drop.isAlive() && (drop.getItem().is(InfectionItems.TISSUE.get())
                || drop.getItem().is(DesignContent.ITEMS_BY_KEY.get("condensed_biomass").get()));
        }

        @Override
        public void tick() {
            var data = carrier.getPersistentData();
            int cargo = data.getInt(CARGO);
            if (item != null && carrier.distanceToSqr(item) < 2.25) {
                int taken = Math.min(item.getItem().getCount(), Math.max(0, (64 - cargo) / 4));
                item.getItem().shrink(taken);
                if (item.getItem().isEmpty()) item.discard();
                data.putInt(CARGO, cargo + taken * 4);
                item = null;
                moveAt = 0;
                carrier.getNavigation().stop();
            }
            if (item == null && carrier.distanceTo(mother) < mother.getBbWidth() * .5 + 2.8) {
                int accepted = Math.min(data.getInt(CARGO), 10000 - mother.colonyBiomass());
                mother.receiveColonyBiomass(accepted);
                data.putInt(CARGO, data.getInt(CARGO) - accepted);
                if (accepted > 0 && carrier.level() instanceof ServerLevel level) {
                    FactionEffects.harvest(level, carrier.position(), mother.position());
                }
                return;
            }
            if (carrier.tickCount >= moveAt) {
                moveAt = carrier.tickCount + 14;
                if (item != null) {
                    carrier.getNavigation().moveTo(item, 1.05);
                    FactionAI.task(carrier, "回收生物质", "前往最近的感染残骸");
                } else if (carrier.getNavigation().isDone()) {
                    routeToMother();
                }
            }
        }

        private void routeToMother() {
            var delta = carrier.position().subtract(mother.position());
            double start = Math.atan2(delta.z, delta.x);
            // The navigation waypoint tolerance and block rounding must still leave
            // the carrier inside the transfer radius, especially for a small stage-one mother.
            double radius = mother.getBbWidth() * .5 + carrier.getBbWidth() * .5 + .35;
            for (int turn : new int[]{0, 1, -1, 2, -2, 3, -3, 4}) {
                double angle = start + turn * Math.PI / 4;
                Vec3 position = mother.position().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                var feet = BlockPos.containing(position);
                var path = carrier.getNavigation().createPath(feet, 0);
                double transfer = mother.getBbWidth() * .5 + 2.8;
                if (path != null && path.canReach() && path.getNodeCount() > 0
                    && path.getEntityPosAtNode(carrier, path.getNodeCount() - 1).distanceTo(mother.position()) < transfer - .8
                    && carrier.getNavigation().moveTo(path, 1.15)) {
                    FactionAI.task(carrier, "运回生物质", "向自己的母体交付载荷");
                    return;
                }
            }
            carrier.getNavigation().moveTo(mother, 1.15);
            FactionAI.task(carrier, "寻找交付路径", "绕向母体");
        }

        @Override
        public void stop() {
            carrier.getNavigation().stop();
            mother = null;
            item = null;
            moveAt = 0;
        }
    }
}
