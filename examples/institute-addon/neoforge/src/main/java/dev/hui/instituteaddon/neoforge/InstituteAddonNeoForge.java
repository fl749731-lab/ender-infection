package dev.hui.instituteaddon.neoforge;

import dev.hui.instituteaddon.AddonRegistration;
import dev.hui.instituteaddon.ResearchConsoleBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(AddonRegistration.MOD_ID)
public final class InstituteAddonNeoForge {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, AddonRegistration.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, AddonRegistration.MOD_ID);
    public static final DeferredHolder<Block, Block> CONSOLE = BLOCKS.register("research_console", ResearchConsoleBlock::new);
    public static final DeferredHolder<Item, Item> CONSOLE_ITEM = ITEMS.register("research_console", () -> new BlockItem(CONSOLE.get(), new Item.Properties()));

    public InstituteAddonNeoForge(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(this::creativeTab);
        AddonRegistration.register();
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(CONSOLE_ITEM.get());
    }
}
