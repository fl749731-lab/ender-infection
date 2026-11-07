package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.IndustryApi;
import dev.hui.resonantindustry.api.MachineDefinition;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(IndustryApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkshopGameTests {
    private static final String ACID = "resonant_industry:acid_extract";
    private static final String ALKALI = "resonant_industry:alkaline_extract";
    private static final String PRODUCT = "resonant_industry:neutral_solution";

    @GameTest(template = "empty_test", timeoutTicks = 1100, batch = "workshop_press")
    public static void buildsPumpFromPoweredPlatePressAndExistingDistillate(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = forceTestChunk(level, 0);
        var generator = machine(level, origin.offset(2, 0, 3), IndustryApi.GENERATOR);
        var press = machine(level, origin.offset(7, 0, 3), WorkshopContent.PRESS);
        wire(level, generator, press.getBlockPos().west().north());
        generator.setItem(0, item("minecraft:coal"));
        press.setItem(0, new ItemStack(IndustryContent.item("minecraft:copper_ingot"), 5));
        var stirrerPos = origin.offset(10, 0, 9);
        place(level, stirrerPos, "magnetic_stirrer");
        var stirrer = (LabBenchEntity) level.getBlockEntity(stirrerPos);

        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(generator.burn() > 0 && press.getItem(2).is(IndustryContent.item("copper_plate"))
                    && press.getItem(2).getCount() == 5, "Coal power did not press copper");
        }).thenExecute(() -> {
            var plate = press.removeItem(2, 5);
            helper.assertTrue(plate.getCount() == 5, "Press output was unavailable");
            press.setItem(0, new ItemStack(IndustryContent.item("minecraft:iron_ingot"), 2));
        }).thenWaitUntil(() -> {
            helper.assertTrue(press.getItem(2).is(IndustryContent.item("iron_plate")) && press.getItem(2).getCount() == 2,
                    "Press did not process iron");
        }).thenExecute(() -> {
            craft(helper, "plate_press_blueprint", new String[]{" I ", "CPC", " R "},
                    Map.of('I', "minecraft:iron_ingot", 'C', "minecraft:copper_ingot", 'P', "minecraft:paper", 'R', "minecraft:redstone"));
            craft(helper, "copper_wire", new String[]{"C"}, Map.of('C', "copper_plate"));
            craft(helper, "basic_motor", new String[]{" W ", "WIR", " C "},
                    Map.of('W', "copper_wire", 'I', "iron_plate", 'R', "minecraft:redstone", 'C', "copper_plate"));
            craft(helper, "metering_pump", new String[]{" G ", "PMP", " I "},
                    Map.of('G', "minecraft:glass_pane", 'P', "copper_plate", 'M', "basic_motor", 'I', "iron_plate"));
            var tube = item("test_tube");
            LabFluids.write(tube, new Chemistry.Portion("distilled_water", 25).stack(), level);
            stirrer.setItem(0, tube);
            stirrer.chemistry().setItem(ChemistryStation.INGREDIENT, item("copper_plate"));
            level.setBlock(stirrerPos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(Vec3.atCenterOf(stirrerPos));
            helper.assertTrue(stirrer.button(player, 14), "Stirrer did not accept the rinse");
        }).thenWaitUntil(() -> {
            helper.assertTrue(stirrer.chemistry().getItem(ChemistryStation.PRODUCT).is(IndustryContent.item("precision_pump_head")),
                    "Distilled water did not produce a precision head");
            helper.assertTrue(new Chemistry.Portion("clear_filtrate", 25).matches(LabFluids.read(stirrer.getItem(0), level), true),
                    "Rinse did not preserve liquid as filtrate");
        }).thenExecute(() -> {
            craft(helper, "precision_metering_pump", new String[]{"PH"},
                    Map.of('P', "metering_pump", 'H', "precision_pump_head"));
            unforce(level, origin);
        }).thenSucceed();
    }

    @GameTest(template = "empty_test", timeoutTicks = 1250, batch = "workshop_mixing")
    public static void metersTwoBatchesAndWaitsForPowerAndReceiver(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = forceTestChunk(level, 1);
        var rig = new MixRig(level, origin.offset(9, 0, 9));
        rig.place("metering_pump", helper.makeMockPlayer(GameType.SURVIVAL), 50, 25);
        var generator = machine(level, origin.offset(2, 0, 3), IndustryApi.GENERATOR);
        var cable = rig.pos().above();
        wire(level, generator, cable);
        generator.setItem(0, item("minecraft:coal"));
        rig.signal(true);
        var held = new int[1];

        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(rig.progress() >= 10, "Pump did not charge and start the first batch");
            helper.assertTrue(rig.acid().amount() == 25 && rig.alkali().amount() == 0,
                    "Pump did not meter exactly 25 mB of each reagent");
        }).thenExecute(() -> {
            level.setBlock(cable, Blocks.AIR.defaultBlockState(), 3);
            rig.output().fill("minecraft:water", 16000, false);
            held[0] = rig.progress();
        }).thenExecuteAfter(12, () -> {
            helper.assertTrue(rig.progress() == held[0], "Blocked receiver advanced the reaction");
            rig.output().drain(16000, false);
        }).thenWaitUntil(() -> {
            helper.assertTrue(rig.pump().energy().getEnergyStored() < 4, "Disconnected pump still has buffered FE");
        }).thenExecute(() -> {
            helper.assertTrue(rig.bench().chemistry().running(), "The test batch did not pause before completion");
            held[0] = rig.progress();
        }).thenExecuteAfter(12, () -> {
            helper.assertTrue(rig.progress() == held[0] && rig.output().amount() == 0, "Unpowered pump advanced or dispensed");
            place(level, cable, "cable");
        }).thenWaitUntil(() -> {
            helper.assertTrue(rig.output().amount() == 50 && PRODUCT.equals(rig.output().fluid()), "First batch did not reach the output tank");
        }).thenExecuteAfter(14, () -> {
            helper.assertTrue(rig.output().amount() == 50 && rig.acid().amount() == 25
                    && LabFluids.read(rig.bench().getItem(0), level).isEmpty(), "Missing alkali partially loaded the next batch");
            rig.alkali().fill(ALKALI, 25, false);
        }).thenWaitUntil(() -> {
            helper.assertTrue(rig.output().amount() == 100 && rig.acid().amount() == 0 && rig.alkali().amount() == 0,
                    "Second batch did not complete without manual transfer");
        }).thenExecute(() -> unforce(level, origin)).thenSucceed();
    }

    @GameTest(template = "empty_test", timeoutTicks = 270, batch = "workshop_precision")
    public static void precisionPumpDeliversOneMillibucketPerSignal(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = forceTestChunk(level, 2);
        var pos = origin.offset(8, 0, 8);
        place(level, pos, "precision_metering_pump");
        place(level, pos.north(), "lab_bench");
        place(level, pos.south(), "tank");
        var bench = (LabBenchEntity) level.getBlockEntity(pos.north());
        var pump = (MeteringPumpEntity) level.getBlockEntity(pos);
        var stock = IndustryNode.at(level, pos.south()).fluidAccess(null);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        bench.setItem(0, item("test_tube"));
        stock.fill("minecraft:water", 3, false);
        var wrench = item("wrench");
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        for (int i = 0; i < 4; i++) {
            level.getBlockState(pos).useItemOn(wrench, level, player, InteractionHand.MAIN_HAND, hit(pos));
        }
        Platform.energy(level, pos, Direction.UP).receive(128, false);
        level.setBlock(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(stock.amount() == 2 && LabFluids.read(bench.getItem(0), level).getAmount() == 1,
                    "Precision setting did not meter one mB");
        }).thenExecute(() -> {
            var tag = pump.saveWithoutMetadata(level.registryAccess());
            helper.assertTrue(tag.getInt("Dose") == 1, "Precision setting was not persisted");
            pump.loadWithComponents(tag, level.registryAccess());
        }).thenExecuteAfter(15, () -> {
            helper.assertTrue(stock.amount() == 2, "A held signal dispensed again after reload");
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
        }).thenExecuteAfter(3, () -> {
            level.setBlock(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        }).thenWaitUntil(() -> {
            helper.assertTrue(stock.amount() == 1 && LabFluids.read(bench.getItem(0), level).getAmount() == 2,
                    "A second signal did not dispense exactly one more mB");
        }).thenExecute(() -> unforce(level, origin)).thenSucceed();
    }

    @GameTest(template = "empty_test", timeoutTicks = 950, batch = "workshop_panel")
    public static void panelSyncsLiveVesselsAndGuardsMachineActions(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = forceTestChunk(level, 3);
        var rig = new MixRig(level, origin.offset(8, 0, 8));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        rig.place("metering_pump", player, 50, 50);
        var pump = rig.pump();
        var menu = pump.createMenu(1, player.getInventory(), player);
        player.getInventory().setItem(1, item("vulcanized_seal"));
        player.setPos(Vec3.atCenterOf(rig.pos().east(10)));
        helper.assertTrue(!menu.clickMenuButton(player, MeteringPumpMenu.INSTALL_SEAL)
                && player.getInventory().getItem(1).getCount() == 1, "Distant player installed a seal");
        player.setPos(Vec3.atCenterOf(rig.pos()));
        helper.assertTrue(menu.automatic() && menu.gauge(MeteringPumpMenu.STOCK).amount() == 50
                && menu.gauge(MeteringPumpMenu.REAGENT).amount() == 50, "Panel did not read its two real tanks");
        helper.assertTrue(menu.clickMenuButton(player, MeteringPumpMenu.INSTALL_SEAL)
                && player.getInventory().getItem(1).isEmpty()
                && !menu.clickMenuButton(player, MeteringPumpMenu.INSTALL_SEAL), "Menu did not consume exactly one seal");
        helper.assertTrue(menu.clickMenuButton(player, MeteringPumpMenu.SET_DOSE_MODE)
                && !menu.clickMenuButton(player, MeteringPumpMenu.SET_DOSE_MODE)
                && menu.clickMenuButton(player, MeteringPumpMenu.CYCLE_DOSE), "Program or dose button failed");
        menu.broadcastChanges();
        helper.assertTrue(!menu.automatic() && menu.dose() == 50 && menu.sealed()
                && menu.gauge(MeteringPumpMenu.OUTPUT).capacity() == 50, "Dose panel lost the selected vessel or settings");
        helper.assertTrue(menu.clickMenuButton(player, MeteringPumpMenu.SET_AUTO_MODE), "Could not restore automatic mode");
        pump.energy().receiveEnergy(128, false);
        rig.signal(true);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(rig.bench().chemistry().metered(), "Metered batch did not start");
        }).thenExecute(() -> {
            menu.broadcastChanges();
            var sample = menu.getSlot(0);
            helper.assertTrue(menu.automatic() && menu.gauge(MeteringPumpMenu.STOCK).amount() == 25
                    && LabFluids.read(sample.getItem(), level).getAmount() == 25, "Panel showed stale levels or samples");
            helper.assertTrue(!sample.mayPickup(player) && !sample.mayPlace(item("test_tube"))
                    && !menu.canTakeItemForPickAll(sample.getItem(), sample)
                    && menu.quickMoveStack(player, 0).isEmpty(), "Read-only vessel preview was movable");
            helper.assertTrue(!menu.clickMenuButton(player, MeteringPumpMenu.SET_DOSE_MODE)
                    && !menu.clickMenuButton(player, 99), "Menu accepted an invalid action on an active batch");
            unforce(level, origin);
        }).thenSucceed();
    }

    private static void craft(GameTestHelper helper, String name, String[] rows, Map<Character, String> items) {
        var grid = new ArrayList<ItemStack>();
        for (String row : rows) for (char key : row.toCharArray()) grid.add(key == ' ' ? ItemStack.EMPTY : item(items.get(key)));
        var recipe = (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(IndustryContent.id(name)).orElseThrow().value();
        var input = CraftingInput.of(rows[0].length(), rows.length, grid);
        helper.assertTrue(recipe.matches(input, helper.getLevel())
                && recipe.assemble(input, helper.getLevel().registryAccess()).is(IndustryContent.item(name)),
                "Cannot craft " + name + " from survival ingredients");
    }

    private static BlockPos forceTestChunk(ServerLevel level, int index) {
        var chunk = new ChunkPos(62510 + index * 2, 62510);
        level.setChunkForced(chunk.x, chunk.z, true);
        level.getChunk(chunk.x, chunk.z);
        return new BlockPos(chunk.getMinBlockX(), 64, chunk.getMinBlockZ());
    }

    private static void unforce(ServerLevel level, BlockPos pos) {
        var chunk = new ChunkPos(pos);
        level.setChunkForced(chunk.x, chunk.z, false);
    }

    private static IndustryNode machine(ServerLevel level, BlockPos pos, String id) {
        place(level, pos, "controller");
        var node = IndustryNode.at(level, pos);
        if (!node.configure(id)) throw new IllegalStateException("Cannot configure " + id);
        for (var cell : node.definition().cells().entrySet()) {
            if (!cell.getValue().equals("controller")) place(level, node.world(cell.getKey()), cell.getValue());
        }
        var result = node.form();
        if (!result.valid()) throw new IllegalStateException("Cannot form " + id + ": " + result);
        return node;
    }

    private static void wire(ServerLevel level, IndustryNode generator, BlockPos endpoint) {
        var origin = generator.world(new MachineDefinition.Cell(-1, 0, 1)).west();
        place(level, origin, "cable");
        place(level, origin.above(), "cable");
        int y = origin.getY() + 2;
        for (int x = origin.getX(); x <= endpoint.getX(); x++) {
            place(level, new BlockPos(x, y, origin.getZ()), "cable");
        }
        for (int z = Math.min(origin.getZ(), endpoint.getZ()); z <= Math.max(origin.getZ(), endpoint.getZ()); z++) {
            place(level, new BlockPos(endpoint.getX(), y, z), "cable");
        }
        for (int down = y - 1; down >= endpoint.getY(); down--) {
            place(level, new BlockPos(endpoint.getX(), down, endpoint.getZ()), "cable");
        }
    }

    private static ItemStack item(String id) { return new ItemStack(IndustryContent.item(id)); }
    private static BlockHitResult hit(BlockPos pos) { return new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false); }

    private static void place(ServerLevel level, BlockPos pos, String block) {
        level.setBlock(pos, block.equals("air") ? Blocks.AIR.defaultBlockState() : IndustryContent.block(block).defaultBlockState(), 3);
    }

    record MixRig(ServerLevel level, BlockPos pos) {
        void place(String pump, Player player, int acid, int alkali) {
            WorkshopGameTests.place(level, pos, pump);
            WorkshopGameTests.place(level, pos.north(), "magnetic_stirrer");
            WorkshopGameTests.place(level, pos.south(), "tank");
            WorkshopGameTests.place(level, pos.west(), "tank");
            WorkshopGameTests.place(level, pos.east(), "tank");
            bench().setItem(0, item("test_tube"));
            bench().setItem(1, item("test_tube"));
            acid().fill(ACID, acid, false);
            alkali().fill(ALKALI, alkali, false);
            player.setShiftKeyDown(true);
            level.getBlockState(pos).useWithoutItem(level, player, hit(pos));
            player.setShiftKeyDown(false);
        }

        void signal(boolean enabled) {
            level.setBlock(pos.below(), (enabled ? Blocks.REDSTONE_BLOCK : Blocks.AIR).defaultBlockState(), 3);
        }

        LabBenchEntity bench() { return (LabBenchEntity) level.getBlockEntity(pos.north()); }
        MeteringPumpEntity pump() { return (MeteringPumpEntity) level.getBlockEntity(pos); }
        dev.hui.resonantindustry.api.FluidAccess acid() { return tank(pos.south()); }
        dev.hui.resonantindustry.api.FluidAccess alkali() { return tank(pos.west()); }
        dev.hui.resonantindustry.api.FluidAccess output() { return tank(pos.east()); }
        private dev.hui.resonantindustry.api.FluidAccess tank(BlockPos pos) { return IndustryNode.at(level, pos).fluidAccess(null); }
        int progress() {
            return bench().saveWithoutMetadata(level.registryAccess()).getCompound("Chemistry").getInt("Progress");
        }
    }
}
