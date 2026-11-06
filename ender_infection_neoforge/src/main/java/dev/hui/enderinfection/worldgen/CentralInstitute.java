package dev.hui.enderinfection.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.design.LabPartBlock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Fixed central institute layout and conservative, loaded-chunk-only administrator construction. */
@EventBusSubscriber(modid = EnderInfection.MOD_ID)
public final class CentralInstitute {
    public static final int WIDTH = 120, HEIGHT = 55, LENGTH = 104, GROUND_OFFSET = 28;
    public static final int TILE_SIZE = 16, TILES_X = 8, TILES_Z = 7, FORMAT_VERSION = 1;
    public static final int UPDATE_FLAGS = 18;
    private static final String DATA = "ender_central_institute_jobs";
    private static final ResourceLocation MANIFEST = id("central_institute.json");
    // Exact classes, not instanceof: subclasses may override onRemove and access adjacent chunks.
    // Representatives also follow loader/version renames (for example 1.21's ColoredFallingBlock).
    private static final Set<Class<?>> PASSIVE_CLASSES = passiveClasses();
    private CentralInstitute() {}

    private static Set<Class<?>> passiveClasses() {
        Set<Class<?>> classes = new HashSet<>();
        for (Block block : List.of(Blocks.AIR, Blocks.STONE, Blocks.OAK_LOG, Blocks.STONE_SLAB, Blocks.STONE_STAIRS,
                Blocks.COBBLESTONE_WALL, Blocks.OAK_FENCE, Blocks.IRON_BARS, Blocks.GLASS, Blocks.TINTED_GLASS,
                Blocks.WHITE_STAINED_GLASS, Blocks.GLASS_PANE, Blocks.WHITE_STAINED_GLASS_PANE,
                Blocks.WHITE_CARPET, Blocks.MOSS_CARPET, Blocks.SNOW, Blocks.GRASS_BLOCK, Blocks.MYCELIUM,
                Blocks.PODZOL, Blocks.DIRT_PATH, Blocks.FARMLAND, Blocks.SAND, Blocks.GRAVEL,
                Blocks.WHITE_CONCRETE_POWDER, Blocks.ROOTED_DIRT, Blocks.MUD, Blocks.COAL_ORE,
                Blocks.INFESTED_STONE, Blocks.INFESTED_DEEPSLATE, Blocks.OAK_LEAVES, Blocks.WATER,
                Blocks.FERN, Blocks.DANDELION, Blocks.TALL_GRASS, Blocks.DEAD_BUSH, Blocks.BROWN_MUSHROOM,
                Blocks.HANGING_ROOTS, Blocks.OAK_SAPLING, Blocks.VINE, Blocks.GLOW_LICHEN,
                Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.LILY_PAD, Blocks.SEAGRASS, Blocks.KELP)) classes.add(block.getClass());
        return Set.copyOf(classes);
    }
    private static boolean passiveRemoval(BlockState state) {
        String namespace = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace();
        return (namespace.equals("minecraft") || namespace.equals(EnderInfection.MOD_ID))
                && PASSIVE_CLASSES.contains(state.getBlock().getClass());
    }

    public record Tile(ResourceLocation template, int x, int y, int z, int width, int height, int length) {}
    public record Manifest(int width, int height, int length, int groundOffset, String hash, List<Tile> tiles) {}

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(EnderInfection.MOD_ID, path); }
    public static List<Tile> tiles() {
        List<Tile> result = new ArrayList<>();
        for (int z = 0; z < TILES_Z; z++) for (int x = 0; x < TILES_X; x++) {
            int ox = x * TILE_SIZE, oz = z * TILE_SIZE;
            result.add(new Tile(id("central_institute/tile_" + x + "_" + z), ox, 0, oz,
                    Math.min(TILE_SIZE, WIDTH - ox), HEIGHT, Math.min(TILE_SIZE, LENGTH - oz)));
        }
        return List.copyOf(result);
    }
    public static Manifest manifest(ServerLevel level) {
        try (var in = level.getServer().getResourceManager().getResourceOrThrow(MANIFEST).open()) {
            byte[] bytes = in.readAllBytes();
            JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.get("format_version").getAsInt() != FORMAT_VERSION || json.get("width").getAsInt() != WIDTH
                    || json.get("height").getAsInt() != HEIGHT || json.get("length").getAsInt() != LENGTH
                    || json.get("ground_offset").getAsInt() != GROUND_OFFSET)
                throw new IllegalArgumentException("中央研究所清单版本或尺寸不符");
            var expected = tiles();
            var entries = json.getAsJsonArray("tiles");
            if (entries.size() != expected.size()) throw new IllegalArgumentException("中央研究所应有 56 个模板");
            var remaining = new ArrayList<>(expected);
            for (var entry : entries) {
                var tile = entry.getAsJsonObject();
                Tile value = new Tile(ResourceLocation.tryParse(tile.get("template").getAsString()),
                        tile.get("x").getAsInt(), tile.get("y").getAsInt(), tile.get("z").getAsInt(),
                        tile.get("width").getAsInt(), tile.get("height").getAsInt(), tile.get("length").getAsInt());
                if (!remaining.remove(value)) throw new IllegalArgumentException("中央研究所模板坐标或尺寸不符");
            }
            return new Manifest(WIDTH, HEIGHT, LENGTH, GROUND_OFFSET, sha256(bytes), expected);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static StructureTemplate template(ServerLevel level, Tile tile) {
        var template = level.getStructureManager().get(tile.template()).orElseThrow(
                () -> new IllegalArgumentException("缺少中央研究所模板 " + tile.template()));
        var size = template.getSize();
        if (size.getX() != tile.width() || size.getY() != tile.height() || size.getZ() != tile.length())
            throw new IllegalArgumentException("中央研究所模板尺寸不符 " + tile.template());
        return template;
    }
    public static StructurePlaceSettings settings(BoundingBox clip) {
        return new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true)
                .setLiquidSettings(net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.IGNORE_WATERLOGGING).addProcessor(new BlockIgnoreProcessor(List.of(Blocks.STRUCTURE_VOID))).setBoundingBox(clip);
    }
    public static BoundingBox bounds(BlockPos origin) {
        return new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + WIDTH - 1, origin.getY() + HEIGHT - 1, origin.getZ() + LENGTH - 1);
    }
    public static String validateSite(ServerLevel level, BlockPos origin, Rotation rotation) {
        if (rotation != Rotation.NONE) return "中央研究所当前只支持 none 旋转";
        long top = (long)origin.getY() + HEIGHT;
        if (origin.getY() < level.getMinBuildHeight() || top > level.getMaxBuildHeight()) return "中央研究所超出世界高度";
        long maxX = (long)origin.getX() + WIDTH - 1, maxZ = (long)origin.getZ() + LENGTH - 1;
        if (maxX > Integer.MAX_VALUE || maxZ > Integer.MAX_VALUE
                || !level.getWorldBorder().isWithinBounds(origin)
                || !level.getWorldBorder().isWithinBounds(new BlockPos((int)maxX, origin.getY(), (int)maxZ)))
            return "中央研究所超出世界边界";
        return null;
    }
    public static Jobs jobs(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Jobs::new, (tag, registries) -> Jobs.load(tag), null), DATA);
    }
    public static boolean queue(ServerLevel level, BlockPos origin, Rotation rotation, UUID player) {
        return queue(level, origin, rotation, player, false);
    }
    public static boolean preview(ServerLevel level, BlockPos origin, UUID player) {
        return queue(level, origin, Rotation.NONE, player, true);
    }
    private static boolean queue(ServerLevel level, BlockPos origin, Rotation rotation, UUID player, boolean preview) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("中央研究所须在服务器线程创建");
        String invalid = validateSite(level, origin, rotation);
        if (invalid != null) throw new IllegalArgumentException(invalid);
        var data = jobs(level);
        if (data.active()) return false;
        var manifest = manifest(level);
        // Fail before starting the job if a data pack omits or changes a tile.
        for (var tile : manifest.tiles()) template(level, tile);
        data.job = new Job(origin.immutable(), player, level.random.nextLong(), manifest.hash(), preview);
        data.lastStatus = "";
        data.setDirty();
        return true;
    }
    public static void step(ServerLevel level) {
        var data = jobs(level);
        try { data.process(level); }
        catch (RuntimeException failure) { data.halt(level, "施工停止：" + failure.getMessage()); }
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 2 == 0) step(level);
    }
    private static final class Job {
        final BlockPos origin; final UUID player; final long seed; final String hash; final boolean preview;
        String phase = "PRECHECK", message = ""; int checked, cursor; boolean halted;
        transient Manifest manifest;
        Job(BlockPos origin, UUID player, long seed, String hash, boolean preview) {
            this.origin = origin; this.player = player; this.seed = seed; this.hash = hash; this.preview = preview;
        }
        int minChunkX() { return origin.getX() >> 4; }
        int minChunkZ() { return origin.getZ() >> 4; }
        int chunksX() { return ((origin.getX() + WIDTH - 1) >> 4) - minChunkX() + 1; }
        int chunksZ() { return ((origin.getZ() + LENGTH - 1) >> 4) - minChunkZ() + 1; }
        int total() { return chunksX() * chunksZ(); }
    }
    public static final class Jobs extends SavedData {
        private Job job; private String lastStatus = "没有中央研究所施工任务";
        public boolean active() { return job != null; }
        public int cursor() { return job == null ? 0 : job.cursor; }
        public int checked() { return job == null ? 0 : job.checked; }
        public int total() { return job == null ? 0 : job.total(); }
        public String phase() { return job == null ? "IDLE" : job.phase; }
        public boolean halted() { return job != null && job.halted; }
        public String status() {
            if (job == null) return lastStatus;
            return (job.preview ? "中央研究所预检" : "中央研究所施工") + " 120×104×55，起点 " + job.origin.toShortString()
                    + "，" + job.phase + "，预检 " + job.checked + "/" + job.total() + "，已建 " + job.cursor + "/" + job.total()
                    + (job.halted ? "，已停止；已建部分保留，使用 cancel 后重新选址" : "")
                    + (job.message.isEmpty() ? "" : "，" + job.message);
        }
        public boolean cancel() {
            if (job == null) return false;
            lastStatus = "已取消中央研究所任务；已建 " + job.cursor + " 个区块保留，不回滚";
            job = null; setDirty(); return true;
        }
        private void notifyPlayer(ServerLevel level, String message) {
            if (job == null) return;
            var player = level.getServer().getPlayerList().getPlayer(job.player);
            if (player != null) player.sendSystemMessage(Component.literal(message));
        }
        private void halt(ServerLevel level, String message) {
            if (job == null || job.halted) return;
            job.halted = true; job.message = message;
            notifyPlayer(level, message + "；已施工部分保留，现有容器不会被清空。使用 /enderinstitute status 查看。");
            setDirty();
        }
        /** At most one already-loaded target chunk per invocation. Never acquires chunk tickets. */
        public void process(ServerLevel level) {
            if (job == null || job.halted) return;
            if (!level.getServer().isSameThread()) throw new IllegalStateException("中央研究所须在服务器线程处理");
            String invalid = validateSite(level, job.origin, Rotation.NONE);
            if (invalid != null) { halt(level, invalid); return; }
            int index = job.phase.equals("PRECHECK") ? job.checked : job.cursor;
            int cx = job.minChunkX() + index % job.chunksX(), cz = job.minChunkZ() + index / job.chunksX();
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {
                String message = "等待已加载区块 " + cx + ", " + cz + "（方块 " + (cx * 16) + ", " + (cz * 16) + "），不会自动加载";
                if (!job.message.equals(message)) { job.message = message; setDirty(); }
                return;
            }
            // Recheck after data-pack reloads, and resolve every tile before any writes in this step.
            // An omitted resource must not leave this chunk half written by the preceding tile.
            job.manifest = manifest(level);
            if (!job.hash.equals(job.manifest.hash())) { halt(level, "清单已改变，拒绝用另一布局继续旧施工"); return; }
            List<StructureTemplate> templates = new ArrayList<>();
            for (var tile : job.manifest.tiles()) templates.add(template(level, tile));
            job.message = "";
            var clip = clip(job.origin, cx, cz);
            String conflict = conflict(chunk, clip);
            if (conflict != null) { halt(level, conflict + "；可能包含玩家建筑或上次崩溃留下的部分施工，拒绝覆盖"); return; }
            if (job.phase.equals("PRECHECK")) {
                job.checked++;
                if (job.checked == job.total()) {
                    if (job.preview) {
                        lastStatus = "中央研究所预检通过：120×104×55，共 " + job.total() + " 个区块；未放置方块。普通方块施工时会被替换";
                        notifyPlayer(level, lastStatus); job = null;
                    } else { job.phase = "PLACE"; notifyPlayer(level, "中央研究所预检通过，开始按区块施工；卸载区块时自动等待。"); }
                }
                setDirty(); return;
            }
            // Scan again immediately before writing; a chest added since PRECHECK remains protected.
            var random = RandomSource.create(job.seed ^ ChunkPos.asLong(cx, cz));
            for (int tileIndex = 0; tileIndex < job.manifest.tiles().size(); tileIndex++) {
                var tile = job.manifest.tiles().get(tileIndex);
                BlockPos at = job.origin.offset(tile.x(), tile.y(), tile.z());
                var tileBox = new BoundingBox(at.getX(), at.getY(), at.getZ(),
                        at.getX() + tile.width() - 1, at.getY() + tile.height() - 1, at.getZ() + tile.length() - 1);
                if (tileBox.intersects(clip) && !templates.get(tileIndex).placeInWorld(level, at, at, settings(clip), random, UPDATE_FLAGS))
                    throw new IllegalStateException("模板放置失败 " + tile.template());
            }
            dev.hui.enderinfection.institute.InstituteProtection.register(level,job.origin);
            job.cursor++; setDirty();
            if (job.cursor == job.total()) {
                lastStatus = "中央研究所已完成：120×104×55，起点 " + job.origin.toShortString()
                        + "，G1 地面 Y=" + (job.origin.getY() + GROUND_OFFSET);
                notifyPlayer(level, lastStatus); job = null;
            }
        }
        public static Jobs load(CompoundTag root) {
            var data = new Jobs();
            if (root.contains("LastStatus", 8)) data.lastStatus = root.getString("LastStatus");
            if (!root.contains("Job", 10)) return data;
            var tag = root.getCompound("Job");
            if (root.getInt("Version") != FORMAT_VERSION || !tag.hasUUID("Player") || !tag.contains("Origin", 4)
                    || !tag.contains("Seed", 4) || !tag.contains("Checked", 3) || !tag.contains("Cursor", 3)
                    || !tag.getString("Hash").matches("[0-9a-f]{64}")
                    || !tag.getString("Rotation").equals("NONE")) {
                data.lastStatus = "中央研究所存档版本或元数据不符，施工未恢复；已建部分保留"; return data;
            }
            var job = new Job(BlockPos.of(tag.getLong("Origin")), tag.getUUID("Player"), tag.getLong("Seed"), tag.getString("Hash"), tag.getBoolean("Preview"));
            job.phase = tag.getString("Phase"); job.checked = tag.getInt("Checked"); job.cursor = tag.getInt("Cursor");
            job.message = tag.getString("Message"); job.halted = tag.getBoolean("Halted");
            if ((!job.phase.equals("PRECHECK") && !job.phase.equals("PLACE")) || job.checked < 0 || job.checked > job.total()
                    || (job.phase.equals("PRECHECK") && (job.checked == job.total() || job.cursor != 0))
                    || job.cursor < 0 || job.cursor >= job.total() || (job.phase.equals("PLACE") && job.checked != job.total())) {
                data.lastStatus = "中央研究所施工游标不符，施工未恢复；已建部分保留"; return data;
            }
            data.job = job; return data;
        }
        @Override public CompoundTag save(CompoundTag root, net.minecraft.core.HolderLookup.Provider registries) {
            return save(root);
        }
        public CompoundTag save(CompoundTag root) {
            root.putInt("Version", FORMAT_VERSION); root.putString("LastStatus", lastStatus);
            root.remove("Job");
            if (job != null) {
                var tag = new CompoundTag();
                tag.putLong("Origin", job.origin.asLong()); tag.putUUID("Player", job.player); tag.putLong("Seed", job.seed);
                tag.putString("Hash", job.hash); tag.putString("Rotation", "NONE"); tag.putString("Phase", job.phase);
                tag.putBoolean("Preview", job.preview); tag.putInt("Checked", job.checked); tag.putInt("Cursor", job.cursor);
                tag.putBoolean("Halted", job.halted); tag.putString("Message", job.message); root.put("Job", tag);
            }
            return root;
        }
    }
    public static BoundingBox clip(BlockPos origin, int cx, int cz) {
        return new BoundingBox(Math.max(origin.getX(), cx * 16), origin.getY(), Math.max(origin.getZ(), cz * 16),
                Math.min(origin.getX() + WIDTH - 1, cx * 16 + 15), origin.getY() + HEIGHT - 1,
                Math.min(origin.getZ() + LENGTH - 1, cz * 16 + 15));
    }
    private static String conflict(LevelChunk chunk, BoundingBox box) {
        for (var pos : chunk.getBlockEntities().keySet()) if (box.isInside(pos)) return "发现已有方块实体/容器 " + pos.toShortString();
        var pos = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) for (int z = box.minZ(); z <= box.maxZ(); z++) for (int x = box.minX(); x <= box.maxX(); x++) {
            pos.set(x, y, z);
            var state = chunk.getBlockState(pos);
            if (state.hasBlockEntity()) return "发现已有方块实体/容器 " + pos.toShortString();
            if (state.getBlock() instanceof LabPartBlock) return "发现已有实验室多方块部件 " + pos.toShortString();
            if (!passiveRemoval(state)) return "拒绝替换活动或未知方块 " + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " " + pos.toShortString();
        }
        return null;
    }
}
