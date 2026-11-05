package dev.hui.enderinfection.warden;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.entity.ContainmentWarden;
import dev.hui.enderinfection.item.ContainmentCoreItem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class WardenContent {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EnderInfection.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EnderInfection.MOD_ID);
    public static final RegistryObject<EntityType<ContainmentWarden>> WARDEN = ENTITIES.register("containment_warden",
        () -> EntityType.Builder.of(ContainmentWarden::new, MobCategory.MONSTER).sized(1.8F, 3.4F)
            .clientTrackingRange(12).updateInterval(1).fireImmune().build(EnderInfection.MOD_ID + ":containment_warden"));
    public static final RegistryObject<Item> CORE = ITEMS.register("containment_core", () -> new ContainmentCoreItem(new Item.Properties().stacksTo(1).fireResistant()));
    public static final RegistryObject<Item> EGG = ITEMS.register("containment_warden_spawn_egg",
        () -> new ForgeSpawnEggItem(WARDEN, 0xC8C2CE, 0xA456D6, new Item.Properties()));

    private WardenContent() {}
    public static void register(IEventBus bus) {
        ENTITIES.register(bus); ITEMS.register(bus);
        bus.addListener(WardenContent::attributes); bus.addListener(WardenContent::creative);
    }
    private static void attributes(EntityAttributeCreationEvent event) { event.put(WARDEN.get(), ContainmentWarden.createAttributes().build()); }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(EGG);
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(CORE);
    }
}
