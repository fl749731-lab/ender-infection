package dev.hui.enderinfection.test;

import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.item.InfectionItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ender_ore_checks")
@PrefixGameTestTemplate(false)
public final class OreGameTests {
    @GameTest(template = "empty64", templateNamespace = "ender_ore_checks", timeoutTicks = 100)
    public static void crystalVeinsGenerateAndDropTheRightMinerals(GameTestHelper helper) {
        checkBiomePlacement(helper, "resonant_ore", "plains", "nether_wastes", "end_highlands");
        checkBiomePlacement(helper, "ember_crystal_ore", "nether_wastes", "plains", "end_highlands");
        checkBiomePlacement(helper, "void_crystal_ore", "end_highlands", "plains", "nether_wastes", "the_end");
        checkBiomePlacement(helper, "void_crystal_ore", "end_midlands", "end_barrens");

        checkVein(helper, "resonant_ore", 7, Blocks.STONE, InfectedContent.RESONANT_ORE.get(),
            InfectionItems.SHARD.get(), Items.IRON_PICKAXE, Items.STONE_PICKAXE);
        checkVein(helper, "resonant_ore", 21, Blocks.DEEPSLATE, InfectedContent.DEEPSLATE_RESONANT_ORE.get(),
            InfectionItems.SHARD.get(), Items.IRON_PICKAXE, Items.STONE_PICKAXE);
        checkVein(helper, "ember_crystal_ore", 35, Blocks.NETHERRACK, InfectedContent.EMBER_CRYSTAL_ORE.get(),
            InfectedContent.EMBER_CRYSTAL.get(), Items.IRON_PICKAXE, Items.STONE_PICKAXE);
        checkVein(helper, "void_crystal_ore", 49, Blocks.END_STONE, InfectedContent.VOID_CRYSTAL_ORE.get(),
            InfectedContent.VOID_CRYSTAL.get(), Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE);

        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.byKey(id("lab_light_panel_from_ember_crystal")).isPresent(), "Ember crystal recipe did not load");
        helper.assertTrue(recipes.byKey(id("suppressing_dust_from_void_crystal")).isPresent(), "Void crystal recipe did not load");
        helper.succeed();
    }

    private static void checkBiomePlacement(GameTestHelper helper, String key, String expectedBiome, String... excludedBiomes) {
        var registry = helper.getLevel().registryAccess();
        var feature = registry.registryOrThrow(Registries.PLACED_FEATURE).get(id(key));
        var biomes = registry.registryOrThrow(Registries.BIOME);
        helper.assertTrue(feature != null, "Missing placed feature " + key);
        helper.assertTrue(biomes.get(new ResourceLocation("minecraft", expectedBiome)).getGenerationSettings().hasFeature(feature),
            key + " is missing from " + expectedBiome);
        for (String excluded : excludedBiomes) {
            helper.assertTrue(!biomes.get(new ResourceLocation("minecraft", excluded)).getGenerationSettings().hasFeature(feature),
                key + " should not generate in " + excluded);
        }
    }

    private static void checkVein(GameTestHelper helper, String feature, int x, Block substrate,
                                  Item oreItem, Item mineral, Item correctPick, Item weakPick) {
        var level = helper.getLevel();
        var ore = ((BlockItem) oreItem).getBlock();
        var origin = helper.absolutePos(new BlockPos(x, 7, 7));
        var from = origin.offset(-5, -4, -5);
        var to = origin.offset(5, 5, 5);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlock(pos, substrate.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        var configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(id(feature));
        var random = RandomSource.create(9637);
        boolean placed = false;
        for (int attempt = 0; attempt < 8 && !placed; attempt++) {
            placed = configured.place(level, level.getChunkSource().getGenerator(), random, origin);
        }
        helper.assertTrue(placed, feature + " cannot replace " + substrate);
        var orePos = BlockPos.betweenClosedStream(from, to).filter(pos -> level.getBlockState(pos).is(ore))
            .findFirst().map(BlockPos::immutable).orElse(null);
        helper.assertTrue(orePos != null, feature + " generated the wrong block in " + substrate);
        var state = level.getBlockState(orePos);
        ItemStack pick = new ItemStack(correctPick);
        helper.assertTrue(state.requiresCorrectToolForDrops() && pick.isCorrectToolForDrops(state)
            && !new ItemStack(weakPick).isCorrectToolForDrops(state), "Incorrect mining tier for " + ore);

        var normal = Block.getDrops(state, level, orePos, null, null, pick);
        helper.assertTrue(normal.size() == 1 && normal.get(0).is(mineral) && normal.get(0).getCount() == 1,
            ore + " did not drop one crystal with an ordinary pickaxe");
        ItemStack silk = pick.copy();
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        var silkDrops = Block.getDrops(state, level, orePos, null, null, silk);
        helper.assertTrue(silkDrops.size() == 1 && silkDrops.get(0).is(oreItem), "Silk Touch did not preserve " + ore);
        ItemStack fortune = pick.copy();
        fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
        boolean gotBonus = false;
        for (int i = 0; i < 32; i++) {
            var drops = Block.getDrops(state, level, orePos, null, null, fortune);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(mineral), "Fortune dropped the wrong mineral");
            gotBonus |= drops.get(0).getCount() > 1;
        }
        helper.assertTrue(gotBonus, "Fortune did not increase drops for " + ore);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("ender_infection", path);
    }
}
