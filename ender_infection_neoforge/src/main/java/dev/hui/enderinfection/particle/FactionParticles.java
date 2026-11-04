package dev.hui.enderinfection.particle;

import dev.hui.enderinfection.EnderInfection;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class FactionParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES=DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE,EnderInfection.MOD_ID);
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> SHARD=TYPES.register("ender_shard",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> SPORE=TYPES.register("infection_spore",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> MOTE=TYPES.register("core_mote",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> RUNE=TYPES.register("rupture_rune",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> SIEGE_SHOCK=TYPES.register("siege_shockwave",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> SIEGE_SPARK=TYPES.register("siege_spark",()->new SimpleParticleType(false));
    private FactionParticles(){}
    public static void register(IEventBus bus){TYPES.register(bus);}
}
