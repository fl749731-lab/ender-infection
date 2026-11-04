package dev.hui.instituteaddon.forge;

import dev.hui.instituteaddon.AddonRegistration;
import dev.hui.instituteaddon.ResearchConsoleBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(AddonRegistration.MOD_ID)
public final class InstituteAddonForge {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AddonRegistration.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AddonRegistration.MOD_ID);
    public static final RegistryObject<Block> CONSOLE = BLOCKS.register("research_console", ResearchConsoleBlock::new);
    public static final RegistryObject<Item> CONSOLE_ITEM = ITEMS.register("research_console", () -> new BlockItem(CONSOLE.get(), new Item.Properties()));

    public InstituteAddonForge() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(this::creativeTab);
        AddonRegistration.register();
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(CONSOLE_ITEM);
    }
}
