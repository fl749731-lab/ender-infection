package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.IndustryApi;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(IndustryApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MineralGameTests {
    @GameTest(template = "empty_test", timeoutTicks = 140, batch = "mineral_veins")
    public static void veinsAndMiningTiersUseNativeRules(GameTestHelper helper) {
        var level = helper.getLevel();
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        var plains = biomes.getHolderOrThrow(Biomes.PLAINS).value();
        var nether = biomes.getHolderOrThrow(Biomes.NETHER_WASTES).value();
        var ores = new String[]{"cassiterite_ore", "sphalerite_ore", "sulfur_ore"};
        var drops = new String[]{"raw_tin", "raw_zinc", "sulfur"};
        for (int index = 0; index < ores.length; index++) {
            var key = IndustryContent.id(ores[index]);
            var placement = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(key);
            helper.assertTrue(placement != null && plains.getGenerationSettings().hasFeature(placement)
                    && !nether.getGenerationSettings().hasFeature(placement), "Wrong biome injection for " + key);
            var position = new BlockPos(1_000_008 + index * 32, 20, 1_000_008);
            boolean iron = ores[index].equals("sphalerite_ore");
            Item pick = iron ? Items.IRON_PICKAXE : Items.STONE_PICKAXE;
            Item weak = iron ? Items.STONE_PICKAXE : Items.WOODEN_PICKAXE;
            checkVein(helper, ores[index], position, Blocks.STONE, ores[index], drops[index], pick, weak);
            checkVein(helper, ores[index], position.south(16), Blocks.DEEPSLATE, "deepslate_" + ores[index],
                    drops[index], pick, weak);
        }
        helper.succeed();
    }

    private static void checkVein(GameTestHelper helper, String feature, BlockPos origin, Block substrate,
                                  String ore, String mineral, Item correctPick, Item weakPick) {
        var level = helper.getLevel();
        var from = origin.offset(-5, -4, -5);
        var to = origin.offset(5, 5, 5);
        var chunk = level.getChunkAt(origin);
        for (var pos : BlockPos.betweenClosed(from, to)) level.setBlock(pos, substrate.defaultBlockState(), Block.UPDATE_CLIENTS);
        // FULL chunks drop the worldgen heightmap, so prime it for the configured feature.
        Heightmap.primeHeightmaps(chunk, Set.of(Heightmap.Types.OCEAN_FLOOR_WG));
        var configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(IndustryContent.id(feature));
        helper.assertTrue(configured != null, "Missing configured feature " + feature);
        var random = RandomSource.create(9637);
        boolean placed = false;
        for (int attempt = 0; attempt < 8 && !placed; attempt++) {
            placed = configured.place(level, level.getChunkSource().getGenerator(), random, origin);
        }
        var block = IndustryContent.block(ore);
        var pos = BlockPos.betweenClosedStream(from, to).filter(candidate -> level.getBlockState(candidate).is(block))
                .findFirst().map(BlockPos::immutable).orElse(null);
        helper.assertTrue(placed && pos != null, feature + " did not place " + ore);
        var state = level.getBlockState(pos);
        var tool = new ItemStack(correctPick);
        helper.assertTrue(state.requiresCorrectToolForDrops() && tool.isCorrectToolForDrops(state)
                && !new ItemStack(weakPick).isCorrectToolForDrops(state), "Wrong mining tier for " + ore);

        var normal = Block.getDrops(state, level, pos, null, null, tool);
        helper.assertTrue(normal.size() == 1 && normal.getFirst().is(IndustryContent.item(mineral))
                && normal.getFirst().getCount() == 1, "Wrong ore drop for " + ore);
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var silk = tool.copy();
        silk.enchant(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
        var silkDrops = Block.getDrops(state, level, pos, null, null, silk);
        helper.assertTrue(silkDrops.size() == 1 && silkDrops.getFirst().is(IndustryContent.item(ore)),
                "Silk Touch did not preserve " + ore);
        var fortune = tool.copy();
        fortune.enchant(enchantments.getOrThrow(Enchantments.FORTUNE), 3);
        boolean bonus = false;
        for (int i = 0; i < 32; i++) {
            var drops = Block.getDrops(state, level, pos, null, null, fortune);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(IndustryContent.item(mineral)),
                    "Wrong Fortune drop for " + ore);
            bonus |= drops.getFirst().getCount() > 1;
        }
        helper.assertTrue(bonus, "Fortune did not increase " + ore + " drops");
    }

    @GameTest(template = "empty_test", timeoutTicks = 500, batch = "mineral_processing")
    public static void oresHaveSurvivalUsesInTheWorkshopAndLab(GameTestHelper helper) {
        var level = helper.getLevel();
        for (String element : new String[]{"tin", "zinc"}) {
            for (String method : new String[]{"smelting", "blasting"}) {
                var recipe = (AbstractCookingRecipe) level.getRecipeManager()
                        .byKey(IndustryContent.id(element + "_ingot_from_" + method)).orElseThrow().value();
                var input = new SingleRecipeInput(stack("raw_" + element));
                helper.assertTrue(recipe.matches(input, level) && recipe.assemble(input, level.registryAccess())
                        .is(IndustryContent.item(element + "_ingot")), "Bad " + method + " recipe for " + element);
            }
            var plate = IndustryApi.recipes().get(IndustryApi.MOD_ID + ":" + element + "_plate");
            helper.assertTrue(plate.machine().equals(WorkshopContent.PRESS)
                    && plate.input().equals(IndustryApi.MOD_ID + ":" + element + "_ingot")
                    && plate.output().equals(IndustryApi.MOD_ID + ":" + element + "_plate"), "Missing press recipe for " + element);
        }
        helper.assertTrue(craft(helper, "basic_motor", 3, 3,
                "", "copper_wire", "", "copper_wire", "zinc_plate", "minecraft:redstone", "", "copper_plate", "")
                .is(IndustryContent.item("basic_motor")), "Motor rejected zinc plate");
        helper.assertTrue(craft(helper, "metering_pump", 3, 3,
                "", "minecraft:glass_pane", "", "tin_plate", "basic_motor", "tin_plate", "", "iron_plate", "")
                .is(IndustryContent.item("metering_pump")), "Pump rejected tin plates");
        var compound = craft(helper, "sulfur_rubber_mix", 2, 1, "sulfur", "minecraft:slime_ball");
        helper.assertTrue(compound.is(IndustryContent.item("sulfur_rubber_mix")) && compound.getCount() == 2,
                "Sulfur did not make two rubber blanks");

        var position = helper.absolutePos(new BlockPos(0, 1, 0));
        level.setBlockAndUpdate(position, IndustryContent.block("hot_plate").defaultBlockState());
        var heater = (LabBenchEntity) level.getBlockEntity(position);
        heater.setItem(0, stack("test_tube"));
        heater.chemistry().setItem(ChemistryStation.INGREDIENT, compound);
        heater.chemistry().setItem(ChemistryStation.FUEL, stack("minecraft:coal"));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(position));
        helper.assertTrue(heater.button(player, 14), "Heater did not start curing");

        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(seals(heater) == 1, "First seal did not cure");
        }).thenExecute(() -> {
            helper.assertTrue(heater.button(player, 14), "Heater did not start second seal");
        }).thenWaitUntil(() -> {
            helper.assertTrue(seals(heater) == 2, "Second seal did not cure");
        }).thenExecute(() -> {
            var tanks = craft(helper, "tank_from_seals", 3, 3,
                    "minecraft:glass", "vulcanized_seal", "minecraft:glass", "casing", "", "casing", "", "vulcanized_seal", "");
            helper.assertTrue(tanks.is(IndustryContent.item("tank")) && tanks.getCount() == 2
                    && heater.chemistry().getItem(ChemistryStation.INGREDIENT).isEmpty(), "Seals did not yield two tanks");
        }).thenSucceed();
    }

    private static ItemStack craft(GameTestHelper helper, String name, int width, int height, String... cells) {
        var level = helper.getLevel();
        var recipe = (CraftingRecipe) level.getRecipeManager().byKey(IndustryContent.id(name)).orElseThrow().value();
        var grid = CraftingInput.of(width, height, Arrays.stream(cells).map(MineralGameTests::stack).toList());
        helper.assertTrue(recipe.matches(grid, level), "Survival ingredients did not match " + name);
        return recipe.assemble(grid, level.registryAccess());
    }

    private static ItemStack stack(String id) {
        return id.isEmpty() ? ItemStack.EMPTY : new ItemStack(IndustryContent.item(id));
    }

    private static int seals(LabBenchEntity heater) {
        var output = heater.chemistry().getItem(ChemistryStation.PRODUCT);
        return output.is(IndustryContent.item("vulcanized_seal")) ? output.getCount() : 0;
    }
}
