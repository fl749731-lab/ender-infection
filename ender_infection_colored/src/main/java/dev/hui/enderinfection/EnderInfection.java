package dev.hui.enderinfection;

import dev.hui.enderinfection.entity.MutantZombie;
import dev.hui.enderinfection.particle.FactionParticles;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import software.bernie.geckolib.GeckoLib;

@Mod(EnderInfection.MOD_ID)
public final class EnderInfection {
    public static final String MOD_ID = "ender_infection";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<EntityType<dev.hui.enderinfection.entity.CrystalSpike>> CRYSTAL_SPIKE=ENTITIES.register("crystal_spike",()->EntityType.Builder.<dev.hui.enderinfection.entity.CrystalSpike>of(dev.hui.enderinfection.entity.CrystalSpike::new,MobCategory.MISC).sized(.8F,1.8F).clientTrackingRange(8).updateInterval(1).build(MOD_ID+":crystal_spike"));
    public static final RegistryObject<EntityType<MutantZombie>> MUTANT_ZOMBIE = ENTITIES.register("mutant_zombie",
        () -> EntityType.Builder.of(MutantZombie::new, MobCategory.MONSTER).sized(1.3F, 2.2F)
            .clientTrackingRange(10).updateInterval(1).build(MOD_ID + ":mutant_zombie"));
    public static final RegistryObject<Item> SPAWN_EGG = ITEMS.register("mutant_zombie_spawn_egg",
        () -> new ForgeSpawnEggItem(MUTANT_ZOMBIE, 0x111019, 0xB658EF, new Item.Properties()));

    public EnderInfection() {
        GeckoLib.initialize();
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        ITEMS.register(bus);
        InfectedContent.register(bus);
        dev.hui.enderinfection.item.InfectionItems.register(bus);
        FactionParticles.register(bus);
        bus.addListener(this::attributes);
        bus.addListener(this::creativeTab);
    }
    private void attributes(EntityAttributeCreationEvent event) {
        event.put(MUTANT_ZOMBIE.get(), MutantZombie.createAttributes().build());
    }
    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(SPAWN_EGG);
    }
}
