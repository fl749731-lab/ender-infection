package dev.hui.enderinfection;

import dev.hui.enderinfection.entity.MutantZombie;
import dev.hui.enderinfection.particle.FactionParticles;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import software.bernie.geckolib.GeckoLib;

@Mod(EnderInfection.MOD_ID)
public final class EnderInfection {
    public static final String MOD_ID = "ender_infection";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MOD_ID);
    public static final DeferredHolder<EntityType<?>,EntityType<dev.hui.enderinfection.entity.CrystalSpike>> CRYSTAL_SPIKE=ENTITIES.register("crystal_spike",()->EntityType.Builder.<dev.hui.enderinfection.entity.CrystalSpike>of(dev.hui.enderinfection.entity.CrystalSpike::new,MobCategory.MISC).sized(.8F,1.8F).clientTrackingRange(8).updateInterval(1).build(MOD_ID+":crystal_spike"));
    public static final DeferredHolder<EntityType<?>,EntityType<MutantZombie>> MUTANT_ZOMBIE = ENTITIES.register("mutant_zombie",
        () -> EntityType.Builder.of(MutantZombie::new, MobCategory.MONSTER).sized(1.3F, 2.2F)
            .clientTrackingRange(10).updateInterval(1).build(MOD_ID + ":mutant_zombie"));
    public static final DeferredHolder<Item,Item> SPAWN_EGG = ITEMS.register("mutant_zombie_spawn_egg",
        () -> new DeferredSpawnEggItem(MUTANT_ZOMBIE, 0x111019, 0xB658EF, new Item.Properties()));

    public EnderInfection(IEventBus bus) {
        // GeckoLib initializes from its own NeoForge entry point.
        ENTITIES.register(bus);
        ITEMS.register(bus);
        InfectedContent.register(bus);
        dev.hui.enderinfection.institute.InstitutePlatform.register(bus);
        dev.hui.enderinfection.rift.RiftContent.register(bus);
        dev.hui.enderinfection.item.InfectionItems.register(bus);
        dev.hui.enderinfection.item.CrystalEquipment.register(bus);
        dev.hui.enderinfection.design.DesignContent.register(bus);
        FactionParticles.register(bus);
        dev.hui.enderinfection.worldgen.SurfaceBuildingStructure.TYPES.register(bus);
        bus.addListener(this::attributes);
        bus.addListener(this::creativeTab);
    }
    private void attributes(EntityAttributeCreationEvent event) {
        event.put(MUTANT_ZOMBIE.get(), MutantZombie.createAttributes().build());
    }
    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(SPAWN_EGG.get());
    }
}
