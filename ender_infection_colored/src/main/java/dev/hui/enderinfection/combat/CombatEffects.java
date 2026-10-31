package dev.hui.enderinfection.combat;

import dev.hui.enderinfection.entity.MutantZombie;
import dev.hui.enderinfection.particle.FactionEffects;
import dev.hui.enderinfection.particle.FactionParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Server-spawned particles and positional sounds are sent to nearby clients. No terrain is destroyed. */
public final class CombatEffects {
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.68F, 0.16F, 0.91F), 1.2F);
    private CombatEffects() {}

    public static void begin(MutantZombie mob, AttackSpec attack) {
        mob.level().playSound(null, mob.blockPosition(),
            attack == AttackSpec.PULSE ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.ZOMBIE_AMBIENT,
            SoundSource.HOSTILE, 1.2F, attack == AttackSpec.PULSE ? 0.55F : 0.65F);
    }

    public static void tick(MutantZombie mob, AttackSpec attack, int tick, Vec3 forward) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        Vec3 center = attack == AttackSpec.SLAM ? mob.position().add(forward.scale(1.6)) : mob.position();
        if (tick < attack.hitTick && tick % 2 == 0) {
            FactionEffects.gather(level,mob.position().add(0,1.2,0),2);
            double progress = tick / (double)attack.hitTick;
            // A contracting charge ring makes the wind-up readable.
            double radius = 1.5 - progress;
            ring(level, mob.position().add(0, 0.4 + progress, 0), radius, 12, tick * 0.2);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 1.4,
                mob.getZ(), 3, 0.35, 0.45, 0.35, 0.025);
        }
        if (tick == attack.hitTick) {
            level.playSound(null, mob.blockPosition(),
                attack == AttackSpec.SWEEP ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, attack == AttackSpec.SWEEP ? 1.3F : 1.8F, 0.65F);
            if (attack == AttackSpec.PULSE)
                level.playSound(null, mob.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.8F, 0.6F);
            FactionEffects.rune(level,center,attack.radius);
            FactionEffects.shatter(level,center,attack==AttackSpec.PULSE?24:16);
            level.sendParticles(FactionParticles.SPORE.get(),center.x,center.y+.3,center.z,12,.65,.35,.65,.04);
            if (attack != AttackSpec.PULSE) groundDebris(level, center);
            if (attack == AttackSpec.SWEEP) clawTrail(level, mob, forward);
        }
        int elapsed = tick - attack.hitTick;
        if (elapsed >= 0 && elapsed <= 10 && tick % 2 == 0) {
            double radius = attack.radius * (elapsed + 1) / 11.0;
            ring(level, center.add(0, 0.12, 0), radius, attack == AttackSpec.PULSE ? 24 : 16, 0);
            if (attack == AttackSpec.PULSE) ring(level, center.add(0, 0.75, 0), radius * 0.95, 16, 0.1);
        }
    }

    private static void ring(ServerLevel level, Vec3 center, double radius, int count, double phase) {
        for (int i = 0; i < count; i++) {
            double angle = phase + Math.PI * 2 * i / count;
            level.sendParticles(FactionParticles.SHARD.get(), center.x + Math.cos(angle) * radius, center.y,
                center.z + Math.sin(angle) * radius, 1, 0.015, 0.015, 0.015, 0);
        }
    }

    private static void groundDebris(ServerLevel level, Vec3 center) {
        BlockPos pos = BlockPos.containing(center.x, center.y - 0.15, center.z);
        BlockState block = level.getBlockState(pos);
        if (!block.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block),
            center.x, center.y + 0.12, center.z, 45, 0.9, 0.15, 0.9, 0.22);
    }

    private static void clawTrail(ServerLevel level, MutantZombie mob, Vec3 forward) {
        double baseAngle = Math.atan2(forward.z, forward.x);
        for (int row = 0; row < 3; row++) {
            for (int i = 0; i <= 18; i++) {
                double angle = baseAngle + Math.toRadians(-70 + i * 140.0 / 18);
                level.sendParticles(FactionParticles.SHARD.get(), mob.getX() + Math.cos(angle) * 2.4,
                    mob.getY() + 0.7 + row * 0.25, mob.getZ() + Math.sin(angle) * 2.4, 1, 0, 0, 0, 0);
            }
        }
    }
}
