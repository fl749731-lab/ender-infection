package dev.hui.enderinfection.worldgen;

import com.mojang.serialization.Codec;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.block.ExpansionNodeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Laboratory generator. RESEARCH is the white tiled station, HOSPITAL the ward, PRISON the cell block. */
public final class InfectedRuinFeature extends Feature<NoneFeatureConfiguration> {
    public enum Style { RESEARCH, HOSPITAL, PRISON }

    private static final int HALF_WIDTH = 6;
    private static final int HALF_DEPTH = 4;
    private static final int WALL_HEIGHT = 7;

    private final Style style;

    public InfectedRuinFeature(Codec<NoneFeatureConfiguration> codec) { this(codec, Style.RESEARCH); }
    public InfectedRuinFeature(Codec<NoneFeatureConfiguration> codec, Style style) { super(codec); this.style = style; }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos.MutableBlockPos cursor = context.origin().mutable();
        int minY = level.getMinBuildHeight();
        while (cursor.getY() > minY && level.getBlockState(cursor).isAir()) cursor.move(0, -1, 0);
        BlockPos base = cursor.getY() <= minY + 6 ? context.origin() : cursor.immutable().above();

        BlockState wall = wallState();
        BlockState accent = accentState();
        BlockState floorA = floorState(true);
        BlockState floorB = floorState(false);
        BlockState glass = glassState();
        BlockState trim = trimState();

        int width = HALF_WIDTH, depth = HALF_DEPTH;
        for (int dx = -width - 2; dx <= width + 2; dx++) {
            for (int dz = -depth - 2; dz <= depth + 2; dz++) {
                for (int dy = -1; dy <= WALL_HEIGHT + 3; dy++) {
                    level.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (int dx = -width - 1; dx <= width + 1; dx++) {
            for (int dz = -depth - 1; dz <= depth + 1; dz++) {
                boolean edge = Math.abs(dx) == width + 1 || Math.abs(dz) == depth + 1;
                level.setBlock(base.offset(dx, -1, dz), edge ? trim : (Math.abs(dx) % 2 == Math.abs(dz) % 2 ? floorA : floorB), 2);
                if (Math.abs(dx) <= width && Math.abs(dz) <= depth) level.setBlock(base.offset(dx, -2, dz), trim, 2);
            }
        }
        for (int dx = -width; dx <= width; dx++) {
            for (int dz = -depth; dz <= depth; dz++) {
                boolean edge = Math.abs(dx) == width || Math.abs(dz) == depth;
                boolean corner = Math.abs(dx) == width && Math.abs(dz) == depth;
                boolean door = dz == -depth && Math.abs(dx) <= 1;
                if (!edge) continue;
                for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    if (door && dy < 3) { level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); continue; }
                    if (corner) { level.setBlock(pos, dy == WALL_HEIGHT - 1 ? trim : accent, 2); continue; }
                    boolean window = (dy >= 2 && dy <= 5) && (Math.abs(dx) % 3 == 1 || Math.abs(dz) % 3 == 1);
                    if (window) level.setBlock(pos, glass, 2);
                    else if (dy == WALL_HEIGHT - 1) level.setBlock(pos, trim, 2);
                    else level.setBlock(pos, wall, 2);
                }
            }
        }
        for (int dx = -width; dx <= width; dx++) {
            for (int dz = -depth; dz <= depth; dz++) {
                boolean edge = Math.abs(dx) == width || Math.abs(dz) == depth;
                boolean rib = (Math.abs(dx) + Math.abs(dz)) % 4 == 0;
                level.setBlock(base.offset(dx, WALL_HEIGHT, dz), edge || rib ? trim : accent, 2);
            }
        }
        for (int dx = -width - 1; dx <= width + 1; dx++) {
            for (int dz = -depth - 1; dz <= depth + 1; dz++) {
                boolean rim = Math.abs(dx) == width + 1 || Math.abs(dz) == depth + 1;
                if (rim) level.setBlock(base.offset(dx, WALL_HEIGHT + 1, dz), trim, 2);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                    level.setBlock(base.offset(sx * (width + 1), dy, sz * (depth + 1)), dy >= WALL_HEIGHT - 1 ? trim : accent, 2);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                level.setBlock(base.offset(dx, WALL_HEIGHT + 1, dz), trim, 2);
            }
        }
        level.setBlock(base.offset(0, WALL_HEIGHT + 2, 0), trim, 2);
        for (int dx = -1; dx <= 1; dx++) {
            level.setBlock(base.offset(dx, 0, -depth - 1), trim, 2);
            level.setBlock(base.offset(dx, 0, -depth - 2), trim, 2);
        }
        for (int side = -1; side <= 1; side += 2) {
            int px = side * 3;
            for (int dz = -depth + 1; dz <= depth - 1; dz++) {
                if (Math.abs(dz) <= 1) continue;
                for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                    level.setBlock(base.offset(px, dy, dz), dy == WALL_HEIGHT - 1 ? trim : wall, 2);
                }
            }
        }
        decorate(level, base, random, wall, accent, trim, glass);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            level.setBlock(base.offset(direction.getStepX() * (width - 1), WALL_HEIGHT - 1, direction.getStepZ() * (depth - 1)), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
        }
        level.setBlock(base.offset(0, 0, 0), InfectedContent.NEST.get().defaultBlockState(), 2);
        for (int dy = 1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (random.nextFloat() < .45F) level.setBlock(base.offset(dx, dy, dz), trim, 2);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            BlockPos spot = base.offset(random.nextInt(width * 2 + 1) - width, 0, random.nextInt(depth * 2 + 1) - depth);
            if (!level.getBlockState(spot).isAir()) continue;
            level.setBlock(spot, i % 2 == 0
                ? InfectedContent.PLANTS.get("infected_flower").get().defaultBlockState()
                : InfectedContent.PLANTS.get("infected_grass").get().defaultBlockState(), 2);
        }
        if (level.getBlockEntity(base) instanceof ExpansionNodeEntity node) node.markWild();
        return true;
    }

    private void decorate(WorldGenLevel level, BlockPos base, RandomSource random, BlockState wall, BlockState accent, BlockState trim, BlockState glass) {
        switch (style) {
            case RESEARCH -> {
                for (int side = -1; side <= 1; side += 2) {
                    BlockPos bench = base.offset(side * 4, 0, -2);
                    level.setBlock(bench, Blocks.BREWING_STAND.defaultBlockState(), 2);
                    level.setBlock(bench.offset(1, 0, 0), Blocks.CAULDRON.defaultBlockState(), 2);
                    level.setBlock(bench.offset(0, 0, 1), Blocks.BOOKSHELF.defaultBlockState(), 2);
                    BlockPos tank = base.offset(side * 5, 0, 1);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int dy = 0; dy < 3; dy++) {
                                boolean shell = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == 2;
                                level.setBlock(tank.offset(dx, dy, dz), shell ? (dy == 2 ? trim : glass)
                                    : (dy == 0 ? InfectedContent.TERRAIN.get("mycelial_mat").get().defaultBlockState()
                                    : InfectedContent.TERRAIN.get("living_tissue").get().defaultBlockState()), 2);
                            }
                        }
                    }
                    level.setBlock(tank.offset(0, 1, 0), InfectedContent.PLANTS.get("infected_fungus").get().defaultBlockState(), 2);
                    chest(level, base.offset(side * 4, 0, 3), random);
                }
                level.setBlock(base.offset(0, 3, 0), Blocks.AMETHYST_BLOCK.defaultBlockState(), 2);
            }
            case HOSPITAL -> {
                for (int side = -1; side <= 1; side += 2) {
                    for (int i = 0; i < 2; i++) {
                        BlockPos bed = base.offset(side * 4, 0, -3 + i * 4);
                        level.setBlock(bed, Blocks.WHITE_BED.defaultBlockState(), 2);
                        level.setBlock(bed.offset(0, 0, 1), Blocks.WHITE_BED.defaultBlockState(), 2);
                    }
                    level.setBlock(base.offset(side * 4, 0, 0), Blocks.CAULDRON.defaultBlockState(), 2);
                    for (int dy = 1; dy <= 2; dy++) {
                        level.setBlock(base.offset(side * 2, dy, 3), Blocks.IRON_BARS.defaultBlockState(), 2);
                        level.setBlock(base.offset(side * 2, dy, -3), Blocks.IRON_BARS.defaultBlockState(), 2);
                    }
                    chest(level, base.offset(side * 5, 0, 3), random);
                }
                level.setBlock(base.offset(0, 3, 0), Blocks.WHITE_CONCRETE.defaultBlockState(), 2);
            }
            case PRISON -> {
                for (int side = -1; side <= 1; side += 2) {
                    BlockPos cell = base.offset(side * 5, 0, 2);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int dy = 0; dy < 3; dy++) {
                                boolean front = dx == 0 && dz == 1;
                                boolean shell = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == 2;
                                if (!shell) continue;
                                level.setBlock(cell.offset(dx, dy, dz), front && dy < 2 ? Blocks.IRON_BARS.defaultBlockState() : wall, 2);
                            }
                        }
                    }
                    level.setBlock(cell.offset(0, 0, 0), Blocks.CHAIN.defaultBlockState(), 2);
                    chest(level, base.offset(side * 5, 0, -3), random);
                }
                level.setBlock(base.offset(0, 3, 0), Blocks.CHAIN.defaultBlockState(), 2);
            }
        }
    }

    private void chest(WorldGenLevel level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
        if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,ResourceLocation.fromNamespaceAndPath("ender_infection", "chests/infected_lab")), random.nextLong());
        }
    }

    private BlockState wallState() {
        return switch (style) {
            case RESEARCH -> InfectedContent.LAB_TILE_LIGHT.get().defaultBlockState();
            case HOSPITAL -> Blocks.QUARTZ_BLOCK.defaultBlockState();
            case PRISON -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    private BlockState accentState() {
        return switch (style) {
            case RESEARCH -> InfectedContent.LAB_TILE.get().defaultBlockState();
            case HOSPITAL -> Blocks.SMOOTH_STONE.defaultBlockState();
            case PRISON -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        };
    }

    private BlockState floorState(boolean odd) {
        return switch (style) {
            case RESEARCH -> odd ? InfectedContent.LAB_TILE_LIGHT.get().defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState();
            case HOSPITAL -> odd ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            case PRISON -> odd ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        };
    }

    private BlockState glassState() {
        return switch (style) {
            case RESEARCH -> Blocks.TINTED_GLASS.defaultBlockState();
            case HOSPITAL -> Blocks.GLASS.defaultBlockState();
            case PRISON -> Blocks.IRON_BARS.defaultBlockState();
        };
    }

    private BlockState trimState() {
        return switch (style) {
            case RESEARCH -> InfectedContent.TERRAIN.get("crystal_crust").get().defaultBlockState();
            case HOSPITAL -> Blocks.SMOOTH_STONE.defaultBlockState();
            case PRISON -> Blocks.DEEPSLATE_TILES.defaultBlockState();
        };
    }
}