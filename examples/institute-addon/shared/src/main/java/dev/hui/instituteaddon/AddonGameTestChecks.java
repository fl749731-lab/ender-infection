package dev.hui.instituteaddon;

import dev.hui.enderinfection.api.institute.DeviceContext;
import dev.hui.enderinfection.api.institute.InstituteApi;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Rotation;

/** Native integration checks, run identically through each loader's test entry point. */
public final class AddonGameTestChecks {
    private AddonGameTestChecks() {}

    public static void placeRegisteredRoom(GameTestHelper helper) {
        helper.assertTrue(InstituteApi.isFrozen(), "Core lifecycle must close registration before tests");
        helper.assertTrue(InstituteApi.rooms().containsKey(AddonRegistration.ROOM_ID), "Addon room must be discoverable");
        helper.assertTrue(InstituteApi.devices().containsKey(AddonRegistration.CONSOLE_ID), "Addon device must be discoverable");
        var recipe = InstituteApi.processingRecipes().get(AddonRegistration.RECIPE_ID);
        helper.assertTrue(recipe != null, "Addon processing recipe must be discoverable");
        helper.assertTrue(recipe.id().equals(AddonRegistration.RECIPE_ID)
                        && recipe.devices().equals(Set.of("ender_infection:lab_microscope"))
                        && recipe.inputId().equals("minecraft:paper") && recipe.inputCount() == 2
                        && recipe.reagentId().equals("minecraft:redstone") && recipe.reagentCount() == 1
                        && recipe.outputId().equals("minecraft:book") && recipe.outputCount() == 1
                        && recipe.ticks() == 80,
                "Archive-copy recipe fields must match the addon registration");
        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
        var preview = InstituteApi.preview(helper.getLevel(), AddonRegistration.ROOM_ID, origin, Rotation.NONE);
        helper.assertTrue(preview.success(), "Room preview failed: " + preview.message());
        helper.assertTrue(preview.connectors().size() == 2, "Both entrances must be exposed");
        helper.assertTrue(helper.getLevel().getBlockState(origin).isAir(), "Preview must not write a floor");
        var placed = InstituteApi.place(helper.getLevel(), AddonRegistration.ROOM_ID, origin, Rotation.NONE);
        helper.assertTrue(placed.success(), "Room placement failed: " + placed.message());
        var console = helper.getLevel().getBlockState(origin.offset(4, 1, 4));
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(console.getBlock()).toString().equals(AddonRegistration.CONSOLE_ID),
                "The shipped template must contain the addon console");
        helper.assertTrue(!console.getValue(ResearchConsoleBlock.POWERED), "The template starts with its console off");
        helper.succeed();
    }

    public static void toggleDevice(GameTestHelper helper, ServerPlayer player) {
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        var block = BuiltInRegistries.BLOCK.stream().filter(value -> value instanceof ResearchConsoleBlock).findFirst().orElseThrow();
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        var initial = helper.getLevel().getBlockState(pos);
        var offhand = new DeviceContext(helper.getLevel(), player, pos, initial, InteractionHand.OFF_HAND, Direction.UP);
        helper.assertTrue(InstituteApi.dispatchDevice(AddonRegistration.CONSOLE_ID, offhand) == InteractionResult.PASS,
                "Offhand must pass through without toggling");
        helper.assertTrue(!helper.getLevel().getBlockState(pos).getValue(ResearchConsoleBlock.POWERED), "Offhand must not change state");
        var main = new DeviceContext(helper.getLevel(), player, pos, initial, InteractionHand.MAIN_HAND, Direction.UP);
        helper.assertTrue(InstituteApi.dispatchDevice(AddonRegistration.CONSOLE_ID, main) == InteractionResult.CONSUME,
                "Registered device must consume its main-hand interaction");
        var active = helper.getLevel().getBlockState(pos);
        helper.assertTrue(active.getValue(ResearchConsoleBlock.POWERED) && active.getLightEmission() == 12,
                "Active console must emit light level 12");
        InstituteApi.dispatchDevice(AddonRegistration.CONSOLE_ID,
                new DeviceContext(helper.getLevel(), player, pos, active, InteractionHand.MAIN_HAND, Direction.UP));
        var inactive = helper.getLevel().getBlockState(pos);
        helper.assertTrue(!inactive.getValue(ResearchConsoleBlock.POWERED) && inactive.getLightEmission() == 0,
                "A second interaction must stop analysis and turn the light off");
        helper.succeed();
    }
}
