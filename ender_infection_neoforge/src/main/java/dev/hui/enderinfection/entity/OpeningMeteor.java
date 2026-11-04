package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

final class OpeningMeteor {
    static final int FLIGHT_TICKS = 44;
    private static final int BLAST_REACH = 12;
    private static final float BLAST_POWER = 5.5F;
    private static final TagKey<Block> TERRAIN = TagKey.create(Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath(EnderInfection.MOD_ID, "infectable_ground"));
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

    static boolean canLand(ServerLevel level, BlockPos site) {
        return blastAreaLoaded(level, site) && level.getBlockState(site.below()).is(TERRAIN);
    }

    static boolean blastAreaLoaded(ServerLevel level, BlockPos site) {
        for (int x = (site.getX() - BLAST_REACH) >> 4; x <= (site.getX() + BLAST_REACH) >> 4; x++) {
            for (int z = (site.getZ() - BLAST_REACH) >> 4; z <= (site.getZ() + BLAST_REACH) >> 4; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    static void impact(ServerLevel level, BlockPos site) {
        Vec3 center = Vec3.atBottomCenterOf(site);
        level.explode(null, null, new CraterDamage(site), center.add(0, .15, 0),
            BLAST_POWER, false, Level.ExplosionInteraction.MOB);
        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + .8, center.z, 72, 2, .7, 2, .35);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + .4, center.z, 30, 2, .8, 2, .04);
        FactionEffects.rune(level, center, 5);
        FactionEffects.shatter(level, center, 28);
    }

    private static final class CraterDamage extends ExplosionDamageCalculator {
        private final BlockPos site;

        CraterDamage(BlockPos site) {
            this.site = site;
        }

        private boolean inCrater(BlockPos pos, BlockState state) {
            int x = pos.getX() - site.getX();
            int z = pos.getZ() - site.getZ();
            int depth = site.getY() - pos.getY();
            return depth >= -1 && depth <= 3 && x * x + z * z + depth * depth <= 25
                && state.is(TERRAIN) && !state.hasBlockEntity() && state.getFluidState().isEmpty();
        }

        @Override
        public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos,
                                                            BlockState state, FluidState fluid) {
            if (inCrater(pos, state)) return Optional.of(.25F);
            return super.getBlockExplosionResistance(explosion, level, pos, state, fluid);
        }

        @Override
        public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos,
                                          BlockState state, float strength) {
            return inCrater(pos, state);
        }
    }

    private static ListTag floats(float... values) {
        ListTag result = new ListTag();
        for (float value : values) result.add(FloatTag.valueOf(value));
        return result;
    }
}
