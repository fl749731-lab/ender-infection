package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.IndustryApi;
import dev.hui.resonantindustry.api.IndustryRecipe;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

final class MineralContent {
    private static final String MOD = IndustryApi.MOD_ID;
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MOD);
    private static final DeferredRegister<Item> ORES = DeferredRegister.create(Registries.ITEM, MOD);
    private static final DeferredRegister<Item> MATERIALS = DeferredRegister.create(Registries.ITEM, MOD);

    static void register(IEventBus bus) {
        registerMetal("cassiterite", "tin");
        registerMetal("sphalerite", "zinc");
        registerOrePair("sulfur");
        MATERIALS.register("sulfur", () -> new Item(identified("sulfur")));
        MATERIALS.register("sulfur_rubber_mix", () -> new Item(new Item.Properties()));
        MATERIALS.register("vulcanized_seal", () -> new Item(new Item.Properties()));

        BLOCKS.register(bus);
        ORES.register(bus);
        MATERIALS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
                ORES.getEntries().forEach(item -> event.accept(item.get()));
            } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
                MATERIALS.getEntries().forEach(item -> event.accept(item.get()));
            }
        });
    }

    private static void registerMetal(String mineral, String metal) {
        registerOrePair(mineral);
        MATERIALS.register("raw_" + metal, () -> new Item(identified(mineral)));
        for (String shape : List.of("ingot", "plate")) {
            MATERIALS.register(metal + "_" + shape, () -> new Item(identified(metal)));
        }
        IndustryApi.registerRecipe(new IndustryRecipe(MOD + ":" + metal + "_plate", WorkshopContent.PRESS,
                MOD + ":" + metal + "_ingot", 1, "", 0, MOD + ":" + metal + "_plate", 1,
                "", 0, "", 0, 80, 12));
    }

    private static void registerOrePair(String mineral) {
        registerOre(mineral + "_ore", mineral, Blocks.STONE);
        registerOre("deepslate_" + mineral + "_ore", mineral, Blocks.DEEPSLATE);
    }

    private static void registerOre(String id, String mineral, Block substrate) {
        var ore = BLOCKS.register(id, () -> new DropExperienceBlock(UniformInt.of(0, 2),
                BlockBehaviour.Properties.ofFullCopy(substrate)
                        .strength(substrate == Blocks.DEEPSLATE ? 4.5F : 3F, 3F)
                        .requiresCorrectToolForDrops()));
        ORES.register(id, () -> new BlockItem(ore.get(), identified(mineral)));
    }

    private static Item.Properties identified(String material) {
        var description = Component.translatable("material." + MOD + "." + material).withStyle(ChatFormatting.GRAY);
        return new Item.Properties().component(DataComponents.LORE, new ItemLore(List.of(description)));
    }

    private MineralContent() {}
}
