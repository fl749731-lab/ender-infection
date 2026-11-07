package dev.hui.resonantindustry.test;

import dev.hui.resonantindustry.*;
import dev.hui.resonantindustry.api.IndustryApi;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import static dev.hui.resonantindustry.test.ChemistryGameTests.*;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(IndustryApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeparationGameTests {
    private static boolean status(LabBenchMenu menu, ChemistryStation.Status status) {
        return menu.chemistry(1) == status.ordinal();
    }

    private static void distill(GameTestHelper h, LabBenchEntity heater, Player player, LabBenchMenu menu) {
        player.setPos(Vec3.atCenterOf(heater.getBlockPos()));
        h.assertTrue(menu.clickMenuButton(player, 16), "Distill button rejected the heated sample");
    }

    @GameTest(template = "empty_test", timeoutTicks = 1900, batch = "separation_chain")
    public static void precipitateIsFilteredAndItsLiquidIsRecovered(GameTestHelper h) {
        var f = new IndustryGameTests.Fixture(h);
        var mixer = station(f, f.origin, "magnetic_stirrer");
        var filter = station(f, f.origin.east(3), "filter_funnel");
        var heater = station(f, f.origin.east(6), "hot_plate");
        var condenser = station(f, f.origin.east(5), "condenser");
        f.put(f.origin.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        var player = h.makeMockSurvivalPlayer();
        var stir = menu(mixer, player);
        var funnel = menu(filter, player);
        var still = menu(heater, player);
        var step = new int[1];
        f.each(1820, fixture -> {
            switch (step[0]) {
                case 0 -> {
                    if (!f.ready()) return;
                    mixer.setItem(0, vessel("test_tube", "acid_extract", 25));
                    putItem(stir, ChemistryStation.INGREDIENT, new ItemStack(Items.CALCITE));
                    start(h, mixer, player, stir);
                    step[0]++;
                }
                case 1 -> {
                    if (!complete(stir)) return;
                    h.assertTrue(isFluid(mixer.getItem(0), "calcium_extract", 25), "Calcite did not dissolve into calcium extract");
                    mixer.setItem(1, vessel("dropper_bottle", "alkaline_extract", 35));
                    start(h, mixer, player, stir);
                    step[0]++;
                }
                case 2 -> {
                    if (!complete(stir)) return;
                    h.assertTrue(isFluid(mixer.getItem(0), "calcium_suspension", 50)
                            && isFluid(mixer.getItem(1), "alkaline_extract", 10), "Wrong precipitation amounts");
                    filter.setItem(0, mixer.removeItemNoUpdate(0));
                    filter.setItem(1, item("test_tube"));
                    putItem(funnel, ChemistryStation.INGREDIENT, item("filter_paper"));
                    start(h, filter, player, funnel);
                    step[0]++;
                }
                case 3 -> {
                    if (!complete(funnel)) return;
                    h.assertTrue(volume(filter.getItem(0)) == 0 && isFluid(filter.getItem(1), "clear_filtrate", 50)
                            && getItem(funnel, ChemistryStation.PRODUCT).is(Items.CALCITE)
                            && getItem(funnel, ChemistryStation.PRODUCT).getCount() == 1
                            && getItem(funnel, ChemistryStation.INGREDIENT).isEmpty(), "Filtration failed to split solid and filtrate");
                    heater.setItem(0, filter.removeItemNoUpdate(1));
                    condenser.setItem(0, item("test_tube"));
                    putItem(still, ChemistryStation.FUEL, new ItemStack(Items.COAL));
                    distill(h, heater, player, still);
                    step[0]++;
                }
                case 4 -> {
                    if (!complete(still)) return;
                    h.assertTrue(isFluid(heater.getItem(0), "filtrate_concentrate", 10)
                            && isFluid(condenser.getItem(0), "distilled_water", 40)
                            && still.chemistry(8) == 0 && still.chemistry(9) == 40, "Still did not account for 40 + 10 mB");
                    var recovered = LabPlatform.capability(condenser.getItem(0)).drain(40, EXECUTE);
                    var bottle = item("reagent_bottle");
                    h.assertTrue(recovered.getAmount() == 40 && LabPlatform.capability(bottle).fill(recovered, EXECUTE) == 40
                            && isFluid(bottle, "distilled_water", 40) && volume(condenser.getItem(0)) == 0,
                            "Distillate could not be recovered through the normal vessel capability");
                    f.success();
                }
            }
        });
    }

    @GameTest(template = "empty_test", timeoutTicks = 1400, batch = "separation_filter")
    public static void filterPaperAndBothOutputsAreValidatedBeforeConsumption(GameTestHelper h) {
        var f = new IndustryGameTests.Fixture(h);
        var filter = station(f, f.origin, "filter_funnel");
        var player = h.makeMockSurvivalPlayer();
        var menu = menu(filter, player);
        filter.setItem(0, vessel("dropper_bottle", "calcium_suspension", 50));
        filter.setItem(1, vessel("beaker", "minecraft:water", 10));
        f.whenReady(() -> {
            h.assertTrue(!menu.getSlot(42).mayPlace(new ItemStack(Items.PAPER)), "Plain paper accepted in the filter slot");
            start(h, filter, player, menu);
            h.assertTrue(status(menu, ChemistryStation.Status.INPUTS), "Filter accepted a missing filter paper");
            var papers = item("filter_paper");
            papers.setCount(2);
            putItem(menu, ChemistryStation.INGREDIENT, papers);
            start(h, filter, player, menu);
            f.after(15, () -> {
                h.assertTrue(status(menu, ChemistryStation.Status.RECEIVER) && menu.chemistry(3) == 0
                        && isFluid(filter.getItem(1), "minecraft:water", 10), "Filter mixed incompatible receiver contents");
                filter.setItem(1, item("pipette"));
                f.after(15, () -> {
                    h.assertTrue(status(menu, ChemistryStation.Status.RECEIVER) && menu.chemistry(3) == 0,
                            "Filter accepted a receiver smaller than the batch");
                    filter.setItem(1, vessel("beaker", "clear_filtrate", 950));
                    putItem(menu, ChemistryStation.PRODUCT, new ItemStack(Items.CALCITE, 64));
                    f.after(15, () -> {
                        h.assertTrue(status(menu, ChemistryStation.Status.OUTPUT) && volume(filter.getItem(0)) == 50
                                && getItem(menu, ChemistryStation.INGREDIENT).getCount() == 2, "Blocked solid output consumed the batch");
                        putItem(menu, ChemistryStation.PRODUCT, ItemStack.EMPTY);
                        f.after(80, () -> {
                            h.assertTrue(complete(menu) && isFluid(filter.getItem(1), "clear_filtrate", 1000)
                                    && volume(filter.getItem(0)) == 0 && getItem(menu, ChemistryStation.PRODUCT).getCount() == 1
                                    && getItem(menu, ChemistryStation.INGREDIENT).getCount() == 1, "Unblocked filter did not commit exactly once");
                            f.success();
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = "empty_test", timeoutTicks = 1630, batch = "separation_still")
    public static void distillationWaitsForAConnectedCondenserAndKeepsItsCharge(GameTestHelper h) {
        var f = new IndustryGameTests.Fixture(h);
        var heater = station(f, f.origin.east(2), "hot_plate");
        var connection = f.origin.east();
        var condenserState = IndustryContent.block("condenser").defaultBlockState();
        var player = h.makeMockSurvivalPlayer();
        var menu = menu(heater, player);
        heater.setItem(0, vessel("test_tube", "neutral_solution", 50));
        putItem(menu, ChemistryStation.FUEL, new ItemStack(Items.COAL));
        f.whenReady(() -> {
            distill(h, heater, player, menu);
            f.after(15, () -> {
                h.assertTrue(status(menu, ChemistryStation.Status.CONDENSER) && getItem(menu, ChemistryStation.FUEL).getCount() == 1
                        && isFluid(heater.getItem(0), "neutral_solution", 50), "Unconnected distillation burned fuel or vented the charge");
                f.put(connection, condenserState.setValue(LabBenchBlock.FACING, Direction.SOUTH));
                f.after(10, () -> {
                    h.assertTrue(menu.chemistry(10) == 0 && menu.chemistry(3) == 0, "A misoriented condenser accepted vapor");
                    f.put(connection, condenserState);
                    f.after(15, () -> {
                        h.assertTrue(status(menu, ChemistryStation.Status.RECEIVER) && getItem(menu, ChemistryStation.FUEL).getCount() == 1,
                                "Heater ignited without a receiver");
                        var condenser = (LabBenchEntity) f.level.getBlockEntity(connection);
                        condenser.setItem(0, vessel("test_tube", "distilled_water", 11));
                        f.after(15, () -> {
                            h.assertTrue(status(menu, ChemistryStation.Status.RECEIVER) && menu.chemistry(3) == 0,
                                    "Heater advanced with less than 40 mB of receiver space");
                            condenser.setItem(0, item("test_tube"));
                            f.after(50, () -> {
                                int progress = menu.chemistry(3);
                                h.assertTrue(progress > 0 && progress < 140, "Still never progressed after connecting its receiver");
                                f.put(connection, "air");
                                f.after(30, () -> {
                                    h.assertTrue(status(menu, ChemistryStation.Status.CONDENSER) && menu.chemistry(3) == progress
                                            && volume(heater.getItem(0)) == 50, "Detached condenser lost the original charge or progress");
                                    var reconnected = station(f, connection, "condenser");
                                    reconnected.setItem(0, vessel("test_tube", "distilled_water", 10));
                                    f.after(150, () -> {
                                        h.assertTrue(complete(menu) && isFluid(heater.getItem(0), "neutral_concentrate", 10)
                                                && isFluid(reconnected.getItem(0), "distilled_water", 50)
                                                && getItem(menu, ChemistryStation.PRODUCT).isEmpty(), "Reconnection did not resume distillation or fell back to crystallization");
                                        f.success();
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = "empty_test", timeoutTicks = 1350, batch = "separation_water")
    public static void distilledWaterCanReplaceWaterInLabExtraction(GameTestHelper h) {
        var f = new IndustryGameTests.Fixture(h);
        var mixer = station(f, f.origin, "magnetic_stirrer");
        f.put(f.origin.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        var player = h.makeMockSurvivalPlayer();
        var menu = menu(mixer, player);
        mixer.setItem(0, vessel("beaker", "distilled_water", 250));
        putItem(menu, ChemistryStation.INGREDIENT, new ItemStack(Items.APPLE));
        f.whenReady(() -> {
            h.assertTrue(!menu.clickMenuButton(player, 16), "Stirrer accepted the heater-only distillation action");
            start(h, mixer, player, menu);
            f.after(95, () -> {
                h.assertTrue(complete(menu) && isFluid(mixer.getItem(0), "acid_extract", 250)
                        && getItem(menu, ChemistryStation.INGREDIENT).isEmpty(), "Recovered water could not be reused in a real experiment");
                f.success();
            });
        });
    }

    @GameTest(template = "empty_test", timeoutTicks = 1250, batch = "separation_crafting")
    public static void survivalRecipesAndEveryCondenserFacingResolve(GameTestHelper h) {
        for (var entry : Map.of("filter_funnel", 1, "condenser", 1, "filter_paper", 2).entrySet()) {
            var result = h.getLevel().getRecipeManager().byKey(IndustryContent.id(entry.getKey())).orElseThrow().getResultItem(h.getLevel().registryAccess());
            h.assertTrue(result.is(IndustryContent.item(entry.getKey())) && result.getCount() == entry.getValue(), "Wrong survival recipe " + entry.getKey());
        }
        try (var f = new IndustryGameTests.Fixture(h)) {
            var player = h.makeMockSurvivalPlayer();
            int offset = 0;
            for (var facing : Direction.Plane.HORIZONTAL) {
                var pos = f.origin.east(offset + 4);
                f.put(pos, IndustryContent.block("hot_plate").defaultBlockState().setValue(LabBenchBlock.FACING, facing));
                var heater = (LabBenchEntity) f.level.getBlockEntity(pos);
                var menu = menu(heater, player);
                var position = pos.relative(facing.getCounterClockWise());
                f.put(position, IndustryContent.block("condenser").defaultBlockState().setValue(LabBenchBlock.FACING, facing));
                h.assertTrue(menu.chemistry(10) == 1, "Condenser link failed for " + facing);
                f.put(position, "air");
                h.assertTrue(menu.chemistry(10) == 0, "Removed condenser retained a live connection for " + facing);
                offset += 4;
            }
            f.success();
        }
    }
}
