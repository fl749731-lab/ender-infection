package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.IndustryApi;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(IndustryApi.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PumpOxidationGameTests {
    @GameTest(template = "empty_test", timeoutTicks = 1100, batch = "pump_oxidation")
    public static void exposedPumpAgesAndSealsSurvivePickup(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = new BlockPos(1_000_264, 64, 1_000_264);
        var chunk = new ChunkPos(pos);
        level.setChunkForced(chunk.x, chunk.z, true);
        level.getChunk(chunk.x, chunk.z);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, IndustryContent.block("metering_pump").defaultBlockState());
        level.setBlockAndUpdate(pos.north(), IndustryContent.block("lab_bench").defaultBlockState());
        level.setBlockAndUpdate(pos.south(), IndustryContent.block("tank").defaultBlockState());
        var pump = new MeteringPumpEntity[]{(MeteringPumpEntity) level.getBlockEntity(pos)};
        var bench = (LabBenchEntity) level.getBlockEntity(pos.north());
        var stock = IndustryNode.at(level, pos.south()).fluidAccess(null);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(pos.east(2)));
        player.setYRot(0);
        bench.setItem(0, stack("test_tube"));
        stock.fill("resonant_industry:acid_extract", 50, false);
        helper.startSequence().thenWaitUntil(() -> {
            if (!level.areEntitiesLoaded(chunk.toLong())) LockSupport.parkNanos(2_000_000);
            helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "Pump fixture chunk was not ticking");
        }).thenExecute(() -> setWear(pump[0], 596, false)).thenWaitUntil(() -> {
            var stage = level.getBlockState(pos).getValue(MeteringPumpBlock.OXIDATION);
            helper.assertTrue(stage == 1 && age(pump[0]) >= 600,
                    "Exposed acid did not advance oxidation: age=" + age(pump[0]) + ", stage=" + stage
                            + ", stock=" + stock.fluid() + "/" + stock.amount());
        }).thenExecute(() -> {
            helper.assertTrue(age(pump[0]) == 600, "Acid did not advance the expected four steps");
            setWear(pump[0], 1196, false);
            var seals = new ItemStack(IndustryContent.item("vulcanized_seal"), 2);
            usePart(player, pos, seals);
            usePart(player, pos, seals);
            helper.assertTrue(seals.getCount() == 1, "Installing a seal consumed the wrong number of parts");
        }).thenExecuteAfter(45, () -> {
            helper.assertTrue(age(pump[0]) == 1196 && sealed(pump[0]), "Acid aged the sealed pump");
            var drops = Block.getDrops(level.getBlockState(pos), level, pos, pump[0], player, new ItemStack(Items.IRON_PICKAXE));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(IndustryContent.item("metering_pump")),
                    "Pump did not drop its placeable item");
            var item = drops.getFirst();
            var blockItem = (BlockItem) item.getItem();
            level.removeBlock(pos, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, item);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
            var result = blockItem.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, item, hit));
            helper.assertTrue(result.consumesAction() && level.getBlockEntity(pos) instanceof MeteringPumpEntity,
                    "Could not place the picked-up pump");
            pump[0] = (MeteringPumpEntity) level.getBlockEntity(pos);
            helper.assertTrue(age(pump[0]) == 1196 && sealed(pump[0]), "Picking up the pump reset wear or lost its seal");
            setWear(pump[0], 1796, false);
            pump[0].energy().receiveEnergy(128, false);
        }).thenWaitUntil(() -> {
            helper.assertTrue(level.getBlockState(pos).getValue(MeteringPumpBlock.OXIDATION) == 3,
                    "Severe oxidation did not change the model state");
        }).thenExecute(() -> {
            level.setBlockAndUpdate(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        }).thenExecuteAfter(12, () -> {
            helper.assertTrue(stock.amount() == 50 && LabFluids.read(bench.getItem(0), level).isEmpty(),
                    "Oxidized pump dispensed despite being jammed");
            usePart(player, pos, stack("vulcanized_seal"));
            var head = stack("precision_pump_head");
            usePart(player, pos, head);
            helper.assertTrue(head.isEmpty() && age(pump[0]) == 0 && sealed(pump[0]),
                    "Repair did not clear oxidation while preserving the seal");
        }).thenWaitUntil(() -> {
            helper.assertTrue(stock.amount() == 25 && LabFluids.read(bench.getItem(0), level).getAmount() == 25,
                    "The repaired pump did not resume the pending dose");
        }).thenExecute(() -> level.setChunkForced(chunk.x, chunk.z, false)).thenSucceed();
    }

    private static void setWear(MeteringPumpEntity pump, int age, boolean sealed) {
        var registries = pump.getLevel().registryAccess();
        var tag = pump.saveWithoutMetadata(registries);
        tag.putInt("OxidationAge", age);
        tag.putBoolean("Sealed", sealed);
        pump.loadWithComponents(tag, registries);
    }

    private static int age(MeteringPumpEntity pump) {
        return pump.saveWithoutMetadata(pump.getLevel().registryAccess()).getInt("OxidationAge");
    }

    private static boolean sealed(MeteringPumpEntity pump) {
        return pump.saveWithoutMetadata(pump.getLevel().registryAccess()).getBoolean("Sealed");
    }

    private static void usePart(Player player, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.EAST, pos, false);
        player.level().getBlockState(pos).useItemOn(stack, player.level(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static ItemStack stack(String name) { return new ItemStack(IndustryContent.item(name)); }
}
