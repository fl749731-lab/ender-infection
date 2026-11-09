package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.IndustryApi;
import java.util.LinkedHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in check of naturally generated ores in a new normal Overworld. */
@EventBusSubscriber(modid = IndustryApi.MOD_ID)
public final class MineralWorldCheck {
    @SubscribeEvent
    public static void check(ServerStartedEvent event) {
        if (!Boolean.getBoolean("resonant_industry.mineralWorldCheck")) return;
        var server = event.getServer();
        try {
            var level = server.overworld();
            if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator)) {
                throw new IllegalStateException("Mineral validation requires a normal Overworld");
            }
            var counts = new LinkedHashMap<Block, Integer>();
            var names = new String[]{"cassiterite_ore", "sphalerite_ore", "sulfur_ore"};
            for (String name : names) {
                counts.put(IndustryContent.block(name), 0);
                counts.put(IndustryContent.block("deepslate_" + name), 0);
            }
            for (int x = 20; x < 24; x++) {
                for (int z = 20; z < 24; z++) {
                    for (var section : level.getChunk(x, z).getSections()) {
                        section.getStates().count((state, count) ->
                                counts.computeIfPresent(state.getBlock(), (block, total) -> total + count));
                    }
                }
            }
            for (String name : names) {
                int stone = counts.get(IndustryContent.block(name));
                int deep = counts.get(IndustryContent.block("deepslate_" + name));
                if (stone + deep == 0) throw new IllegalStateException("No naturally generated " + name);
                System.out.println("MINERAL_VEIN " + name + " stone=" + stone + " deepslate=" + deep);
            }
            System.out.println("MINERAL_WORLDGEN_PASS");
        } catch (RuntimeException error) {
            System.err.println("MINERAL_WORLDGEN_FAIL " + error);
            error.printStackTrace();
        } finally {
            server.halt(false);
        }
    }

    private MineralWorldCheck() {}
}
