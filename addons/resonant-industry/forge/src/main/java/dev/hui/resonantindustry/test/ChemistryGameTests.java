package dev.hui.resonantindustry.test;

import dev.hui.resonantindustry.*;
import dev.hui.resonantindustry.api.IndustryApi;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(IndustryApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemistryGameTests {
    static LabBenchEntity station(IndustryGameTests.Fixture f, BlockPos pos, String name) {
        f.put(pos, name);
        return (LabBenchEntity) f.level.getBlockEntity(pos);
    }

    static ItemStack item(String id) { return new ItemStack(IndustryContent.item(id)); }

    static ItemStack vessel(String type, String fluid, int amount) {
        var stack = item(type);
        if (amount > 0) {
            var material = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(IndustryContent.id(fluid));
            var sample = new net.minecraftforge.fluids.FluidStack(material, amount);
            if (LabPlatform.capability(stack).fill(sample, EXECUTE) != amount) throw new AssertionError("Cannot prepare sample " + fluid);
        }
        return stack;
    }

    static int volume(ItemStack stack) { return LabPlatform.data(stack).getInt("LabAmount"); }
    private static String fluid(ItemStack stack) { return LabPlatform.data(stack).getString("LabFluid"); }
    static boolean isFluid(ItemStack stack, String id, int amount) {
        return volume(stack) == amount && fluid(stack).equals(IndustryContent.id(id).toString());
    }

    static LabBenchMenu menu(LabBenchEntity station, Player player) {
        player.setPos(Vec3.atCenterOf(station.getBlockPos()));
        return (LabBenchMenu) station.createMenu(1, player.getInventory(), player);
    }

    static void putItem(LabBenchMenu menu, int slot, ItemStack item) { menu.getSlot(42 + slot).set(item); }
    static ItemStack getItem(LabBenchMenu menu, int slot) { return menu.getSlot(42 + slot).getItem(); }
    static boolean complete(LabBenchMenu menu) { return menu.chemistry(1) == ChemistryStation.Status.COMPLETE.ordinal(); }

    static void start(GameTestHelper h, LabBenchEntity station, Player player, LabBenchMenu menu) {
        player.setPos(Vec3.atCenterOf(station.getBlockPos()));
        h.assertTrue(menu.clickMenuButton(player, 14), "Native experiment button did not start");
    }

    private static CompoundTag save(LabBenchEntity n) { return n.saveWithoutMetadata(); }
    private static void load(LabBenchEntity n, CompoundTag tag) { n.load(tag); }

    @GameTest(template = "empty_test", timeoutTicks = 2050, batch = "chemistry_chain")
    public static void starterExperimentsRunFromWaterToCrystalsAndPurifiedTissue(GameTestHelper h) {
        var f = new IndustryGameTests.Fixture(h);
        var heater = station(f, f.origin, "hot_plate");
        var stirrer = station(f, f.origin.east(3), "magnetic_stirrer");
        f.put(f.origin.east(4), Blocks.REDSTONE_BLOCK.defaultBlockState());
        var player = h.makeMockSurvivalPlayer();
        var heat = menu(heater, player);
        var stir = menu(stirrer, player);
        var step = new int[1];
        f.each(1930, fixture -> {
            switch (step[0]) {
                case 0 -> {
                    if (!f.ready()) return;
                    heater.setItem(0, item("beaker"));
                    putItem(heat, ChemistryStation.INGREDIENT, new ItemStack(Items.DRIED_KELP));
                    putItem(heat, ChemistryStation.FUEL, new ItemStack(Items.COAL, 2));
                    start(h, heater, player, heat);
                    stirrer.setItem(0, vessel("beaker", "minecraft:water", 250));
                    putItem(stir, ChemistryStation.INGREDIENT, new ItemStack(Items.APPLE));
                    start(h, stirrer, player, stir);
                    step[0]++;
                }
                case 1 -> {
                    if (!complete(heat) || !complete(stir)) return;
                    h.assertTrue(heat.chemistry(2) >= 400, "Ash formed without reaching the heating threshold");
                    h.assertTrue(isFluid(stirrer.getItem(0), "acid_extract", 250), "Fruit extraction did not transform 250 mB");
                    var ash = heat.getSlot(44).remove(1);
                    h.assertTrue(ash.is(IndustryContent.item("alkali_ash")), "Dry kelp produced no alkali ash");
                    stirrer.setItem(2, stirrer.removeItemNoUpdate(0));
                    stirrer.setItem(0, vessel("beaker", "minecraft:water", 250));
                    putItem(stir, ChemistryStation.INGREDIENT, ash);
                    start(h, stirrer, player, stir);
                    step[0]++;
                }
                case 2 -> {
                    if (!complete(stir)) return;
                    h.assertTrue(isFluid(stirrer.getItem(0), "alkaline_extract", 250), "Alkali extraction failed");
                    stirrer.setItem(3, stirrer.removeItemNoUpdate(0));
                    stirrer.setItem(0, item("test_tube"));
                    stirrer.setItem(1, item("dropper_bottle"));
                    // Dose through the same six-position menu used by players.
                    stir.clickMenuButton(player, 2);
                    stir.clickMenuButton(player, 6);
                    stir.clickMenuButton(player, 13);
                    stir.clickMenuButton(player, 3);
                    stir.clickMenuButton(player, 7);
                    stir.clickMenuButton(player, 13);
                    stir.clickMenuButton(player, 13);
                    h.assertTrue(isFluid(stirrer.getItem(0), "acid_extract", 25) && isFluid(stirrer.getItem(1), "alkaline_extract", 50), "Measured doses did not reach their vessels");
                    start(h, stirrer, player, stir);
                    step[0]++;
                }
                case 3 -> {
                    if (!complete(stir)) return;
                    h.assertTrue(isFluid(stirrer.getItem(0), "neutral_solution", 50) && isFluid(stirrer.getItem(1), "alkaline_extract", 25), "Neutralization lost liquid or used the full reagent bottle");
                    heater.setItem(0, stirrer.removeItemNoUpdate(0));
                    start(h, heater, player, heat);
                    stirrer.setItem(0, item("test_tube"));
                    player.setPos(Vec3.atCenterOf(stirrer.getBlockPos()));
                    stir.clickMenuButton(player, 2);
                    stir.clickMenuButton(player, 6);
                    stir.clickMenuButton(player, 13);
                    start(h, stirrer, player, stir);
                    step[0]++;
                }
                case 4 -> {
                    if (!complete(stir)) return;
                    putItem(stir, ChemistryStation.INGREDIENT, item("ender_infection:raw_tissue_sample"));
                    start(h, stirrer, player, stir);
                    step[0]++;
                }
                case 5 -> {
                    if (!complete(stir) || !complete(heat)) return;
                    h.assertTrue(getItem(heat, ChemistryStation.PRODUCT).is(IndustryContent.item("neutral_salt")) && volume(heater.getItem(0)) == 0 && heat.chemistry(8) == 50, "Crystallization failed to account for 50 mB evaporation");
                    h.assertTrue(getItem(stir, ChemistryStation.PRODUCT).is(IndustryContent.item("purified_tissue")) && isFluid(stirrer.getItem(0), "nutrient_slurry", 50), "Tissue wash did not produce tissue and recoverable slurry");
                    int remaining = 0;
                    for (int slot = 0; slot < 6; slot++) remaining += volume(heater.getItem(slot)) + volume(stirrer.getItem(slot));
                    h.assertTrue(remaining == 450, "Expected 500 mB input minus 50 mB evaporation, got " + remaining);
                    h.assertTrue(getItem(stir, ChemistryStation.INGREDIENT).isEmpty(), "Raw tissue was not consumed");
                    f.success();
                }
            }
        });
    }

    @GameTest(template = "empty_test", timeoutTicks = 1400, batch = "chemistry_controls")
    public static void redstonePauseAndInterruptedDoseDoNotConsumeAnything(GameTestHelper h) {
        var f = new IndustryGameTests.Fixture(h);
        var stirrer = station(f, f.origin, "magnetic_stirrer");
        var player = h.makeMockSurvivalPlayer();
        var menu = menu(stirrer, player);
        stirrer.setItem(0, vessel("test_tube", "acid_extract", 25));
        stirrer.setItem(1, vessel("dropper_bottle", "alkaline_extract", 25));
        f.whenReady(() -> {
            start(h, stirrer, player, menu);
            f.after(10, () -> {
                h.assertTrue(menu.chemistry(3) == 0 && menu.chemistry(1) == ChemistryStation.Status.SIGNAL.ordinal(), "Unpowered stirrer advanced");
                f.put(f.origin.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                f.after(30, () -> {
                    int saved = menu.chemistry(3);
                    h.assertTrue(saved >= 20 && saved < 100, "Powered stirring did not advance");
                    f.put(f.origin.east(), "air");
                    f.after(15, () -> {
                        h.assertTrue(menu.chemistry(3) == saved && volume(stirrer.getItem(0)) == 25 && volume(stirrer.getItem(1)) == 25, "Unpowered experiment consumed or advanced");
                        LabPlatform.capability(stirrer.getItem(1)).drain(1, EXECUTE);
                        f.put(f.origin.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                        f.after(120, () -> {
                            h.assertTrue(menu.chemistry(1) == ChemistryStation.Status.INPUTS.ordinal() && menu.chemistry(3) == 0, "Changed dose did not cancel the experiment");
                            h.assertTrue(isFluid(stirrer.getItem(0), "acid_extract", 25) && isFluid(stirrer.getItem(1), "alkaline_extract", 24), "Interrupted batch changed chemical stocks");
                            f.success();
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = "empty_test", timeoutTicks = 1450, batch = "chemistry_heat")
    public static void heatAndFuelProgressSurviveNativeSerialization(GameTestHelper h) {
        var f = new IndustryGameTests.Fixture(h);
        var heater = station(f, f.origin, "hot_plate");
        var player = h.makeMockSurvivalPlayer();
        var menu = menu(heater, player);
        heater.setItem(0, item("beaker"));
        putItem(menu, ChemistryStation.INGREDIENT, new ItemStack(Items.DRIED_KELP));
        f.whenReady(() -> {
            start(h, heater, player, menu);
            f.after(15, () -> {
                h.assertTrue(menu.chemistry(1) == ChemistryStation.Status.FUEL.ordinal() && menu.chemistry(3) == 0, "Fuel-free heating advanced");
                putItem(menu, ChemistryStation.FUEL, new ItemStack(Items.CHARCOAL));
                f.after(115, () -> {
                    int elapsed = menu.chemistry(3), temperature = menu.chemistry(2), fuel = menu.chemistry(6);
                    h.assertTrue(elapsed > 0 && elapsed < 100 && temperature >= 400 && fuel > 0, "No in-progress heated experiment to save");
                    var saved = save(heater);
                    var reloaded = station(f, f.origin.east(2), "hot_plate");
                    load(reloaded, saved);
                    var reloadedMenu = menu(reloaded, player);
                    h.assertTrue(reloadedMenu.chemistry(3) == elapsed && reloadedMenu.chemistry(2) == temperature && reloadedMenu.chemistry(6) == fuel, "Reaction timing or thermal state was lost on load");
                    f.after(105, () -> {
                        h.assertTrue(complete(reloadedMenu) && getItem(reloadedMenu, ChemistryStation.PRODUCT).is(IndustryContent.item("alkali_ash")) && getItem(reloadedMenu, ChemistryStation.PRODUCT).getCount() == 1, "Reloaded batch did not finish exactly once");
                        h.assertTrue(getItem(reloadedMenu, ChemistryStation.INGREDIENT).isEmpty() && getItem(reloadedMenu, ChemistryStation.FUEL).isEmpty(), "Reloaded batch duplicated fuel or reagents");
                        f.success();
                    });
                });
            });
        });
    }

    @GameTest(template = "empty_test", timeoutTicks = 1400, batch = "chemistry_controls")
    public static void blockedProductCannotConsumeReagentsAndCanResume(GameTestHelper h) {
        var f = new IndustryGameTests.Fixture(h);
        var stirrer = station(f, f.origin, "magnetic_stirrer");
        f.put(f.origin.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        var player = h.makeMockSurvivalPlayer();
        var menu = menu(stirrer, player);
        stirrer.setItem(0, vessel("test_tube", "neutral_solution", 50));
        putItem(menu, ChemistryStation.INGREDIENT, item("ender_infection:raw_tissue_sample"));
        f.whenReady(() -> {
            start(h, stirrer, player, menu);
            f.after(45, () -> {
                var full = item("purified_tissue");
                full.setCount(full.getMaxStackSize());
                putItem(menu, ChemistryStation.PRODUCT, full);
                int progress = menu.chemistry(3);
                f.after(25, () -> {
                    h.assertTrue(menu.chemistry(1) == ChemistryStation.Status.OUTPUT.ordinal() && menu.chemistry(3) == progress && isFluid(stirrer.getItem(0), "neutral_solution", 50), "Blocked output advanced or consumed reagent");
                    putItem(menu, ChemistryStation.PRODUCT, ItemStack.EMPTY);
                    f.after(120, () -> {
                        h.assertTrue(complete(menu) && getItem(menu, ChemistryStation.PRODUCT).getCount() == 1 && isFluid(stirrer.getItem(0), "nutrient_slurry", 50), "Unblocked experiment failed to commit one batch");
                        f.success();
                    });
                });
            });
        });
    }

    @GameTest(template = "empty_test", timeoutTicks = 1400, batch = "chemistry_controls")
    public static void wrongVesselAndDoseAreRejectedAndStopIsFinal(GameTestHelper h) {
        var f = new IndustryGameTests.Fixture(h);
        var stirrer = station(f, f.origin, "magnetic_stirrer");
        f.put(f.origin.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        var player = h.makeMockSurvivalPlayer();
        var menu = menu(stirrer, player);
        f.whenReady(() -> {
            stirrer.setItem(0, vessel("pipette", "acid_extract", 25));
            stirrer.setItem(1, vessel("dropper_bottle", "alkaline_extract", 25));
            start(h, stirrer, player, menu);
            h.assertTrue(menu.chemistry(1) == ChemistryStation.Status.INPUTS.ordinal(), "Pipette incorrectly accepted as reaction vessel");
            stirrer.setItem(0, vessel("test_tube", "acid_extract", 26));
            start(h, stirrer, player, menu);
            h.assertTrue(menu.chemistry(1) == ChemistryStation.Status.INPUTS.ordinal(), "Extra acid accepted for the measured batch");
            stirrer.setItem(0, vessel("test_tube", "acid_extract", 25));
            start(h, stirrer, player, menu);
            f.after(25, () -> {
                h.assertTrue(menu.chemistry(3) > 0 && !menu.clickMenuButton(player, 14), "Start reset an active experiment");
                menu.clickMenuButton(player, 15);
                f.after(110, () -> {
                    h.assertTrue(menu.chemistry(1) == ChemistryStation.Status.STOPPED.ordinal() && isFluid(stirrer.getItem(0), "acid_extract", 25) && isFluid(stirrer.getItem(1), "alkaline_extract", 25), "Stop did not preserve samples");
                    f.success();
                });
            });
        });
    }

    @GameTest(template = "empty_test", timeoutTicks = 1200, batch = "chemistry_crafting")
    public static void devicesCraftAndDropTheirVesselsAndSolidInventory(GameTestHelper h) {
        for (String id : List.of("hot_plate", "magnetic_stirrer")) {
            var output = h.getLevel().getRecipeManager().byKey(IndustryContent.id(id)).orElseThrow().getResultItem(h.getLevel().registryAccess());
            h.assertTrue(output.is(IndustryContent.item(id)) && output.getCount() == 1, "Missing device crafting recipe " + id);
        }
        var f = new IndustryGameTests.Fixture(h);
        var heater = station(f, f.origin, "hot_plate");
        var player = h.makeMockSurvivalPlayer();
        var menu = menu(heater, player);
        heater.setItem(0, vessel("beaker", "neutral_solution", 50));
        putItem(menu, ChemistryStation.INGREDIENT, new ItemStack(Items.DRIED_KELP, 3));
        putItem(menu, ChemistryStation.FUEL, new ItemStack(Items.COAL, 2));
        putItem(menu, ChemistryStation.PRODUCT, item("neutral_salt"));
        f.whenReady(() -> {
            f.level.destroyBlock(f.origin, true);
            f.after(5, () -> {
                var drops = f.level.getEntitiesOfClass(ItemEntity.class, new AABB(f.origin).inflate(3));
                int salt = 0, coal = 0, kelp = 0, liquid = 0, blocks = 0;
                for (var entity : drops) {
                    var stack = entity.getItem();
                    if (stack.is(Items.COAL)) coal += stack.getCount();
                    if (stack.is(Items.DRIED_KELP)) kelp += stack.getCount();
                    if (stack.is(IndustryContent.item("neutral_salt"))) salt += stack.getCount();
                    if (stack.is(IndustryContent.item("hot_plate"))) blocks += stack.getCount();
                    liquid += volume(stack);
                }
                h.assertTrue(salt == 1 && coal == 2 && kelp == 3 && liquid == 50 && blocks == 1, "Breaking the device duplicated or lost its inventory");
                f.success();
            });
        });
    }
}
