package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

final class OpeningMeteor {
    static final int FLIGHT_TICKS = 44;
    private static final String TAG = "ender_infection_opening_meteor";

    private OpeningMeteor() {}

    static UUID move(ServerLevel level, BlockPos site, UUID id, int tick) {
        Display.BlockDisplay display = id != null && level.getEntity(id) instanceof Display.BlockDisplay saved
            && saved.getTags().contains(TAG) ? saved : null;
        if (display == null) {
            // Reuse a saved display if the chunk was written before the intro's SavedData.
            var nearby = level.getEntitiesOfClass(Display.BlockDisplay.class,
                new AABB(site).inflate(2, 52, 2), entity -> entity.getTags().contains(TAG));
            if (!nearby.isEmpty()) display = nearby.get(0);
        }

        double progress = tick / (double) (FLIGHT_TICKS - 1);
        double altitude = Math.min(48, level.getMaxBuildHeight() - site.getY() - 4) * (1 - progress * progress);
        Vec3 center = Vec3.atBottomCenterOf(site).add(0, altitude, 0);
        if (display == null) {
            display = EntityType.BLOCK_DISPLAY.create(level);
            if (display == null) return null;
            CompoundTag settings = new CompoundTag();
            settings.put("block_state", NbtUtils.writeBlockState(Blocks.CRYING_OBSIDIAN.defaultBlockState()));
            CompoundTag transform = new CompoundTag();
            transform.put("translation", floats(-1.2F, 0F, -1.2F));
            transform.put("scale", floats(2.4F, 2.4F, 2.4F));
            transform.put("left_rotation", floats(0F, 0F, 0F, 1F));
            transform.put("right_rotation", floats(0F, 0F, 0F, 1F));
            settings.put("transformation", transform);
            settings.putFloat("view_range", 2.5F);
            settings.putFloat("width", 3F);
            settings.putFloat("height", 3F);
            display.load(settings);
            display.addTag(TAG);
            display.moveTo(center.x, center.y, center.z, tick * 9F, 0);
            if (!level.addFreshEntity(display)) return null;
        }
        display.moveTo(center.x, center.y, center.z, tick * 9F, 0);
        if (tick % 2 == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 1, center.z, 8, .8, .45, .8, .02);
            level.sendParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y + 1, center.z, 10, .9, .6, .9, .03);
        }
        if (tick == 0) {
            level.playSound(null, site, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, .85F, .65F);
        }
        return display.getUUID();
    }

    static void remove(ServerLevel level, UUID id) {
        if (id != null && level.getEntity(id) instanceof Display.BlockDisplay display && display.getTags().contains(TAG)) {
            display.discard();
        }
    }

    static void impact(ServerLevel level, BlockPos site) {
        Vec3 center = Vec3.atBottomCenterOf(site);
        level.playSound(null, site, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.3F, .65F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + .5, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + .8, center.z, 72, 2, .7, 2, .35);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + .4, center.z, 30, 2, .8, 2, .04);
        FactionEffects.rune(level, center, 5);
        FactionEffects.shatter(level, center, 28);
    }

    private static ListTag floats(float... values) {
        ListTag result = new ListTag();
        for (float value : values) result.add(FloatTag.valueOf(value));
        return result;
    }
}
