package dev.hui.enderinfection.particle;

import dev.hui.enderinfection.EnderInfection;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FactionParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES=DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES,EnderInfection.MOD_ID);
    public static final RegistryObject<SimpleParticleType> SHARD=TYPES.register("ender_shard",()->new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> SPORE=TYPES.register("infection_spore",()->new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> MOTE=TYPES.register("core_mote",()->new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> RUNE=TYPES.register("rupture_rune",()->new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> SIEGE_SHOCK=TYPES.register("siege_shockwave",()->new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> SIEGE_SPARK=TYPES.register("siege_spark",()->new SimpleParticleType(false));
    private FactionParticles(){}
    public static void register(IEventBus bus){TYPES.register(bus);}
}
