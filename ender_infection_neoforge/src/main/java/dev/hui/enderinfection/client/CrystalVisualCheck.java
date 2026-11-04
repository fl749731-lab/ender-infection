package dev.hui.enderinfection.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.WorldIntroduction;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Opt-in client fixture. Every frame comes from the running Forge renderer. */
@EventBusSubscriber(modid = EnderInfection.MOD_ID, value = Dist.CLIENT)
public final class CrystalVisualCheck {
    private static final boolean GLOW_CHECK = Boolean.getBoolean("ender_infection.crystalGlowCheck");
    private static final String WORLD = (GLOW_CHECK ? "CrystalGlowReview-" : "CrystalGeometryReview-")
        + Long.toUnsignedString(System.nanoTime());
    private static final int FLOOR = -60;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final Map<String, List<BlockPos>> DARK_SAMPLES = new HashMap<>();
    private static JsonObject conceptBounds;
    private static boolean launched, finished;
    private static volatile boolean ready;
    private static boolean lightRefreshRequested;
    private static volatile boolean lightRefreshSent;
    private static int lightRefreshWait;
    private static int frame, waitTicks;
    private static long start;

    private record Shot(String name, Vec3 eye, Vec3 focus) {
        float yaw() {
            var d = focus.subtract(eye);
            return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        }

        float pitch() {
            var d = focus.subtract(eye);
            return (float) Math.toDegrees(Math.atan2(-d.y, d.horizontalDistance()));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(GLOW_CHECK || Boolean.getBoolean("ender_infection.crystalCheck")) || !(event.getEntity() instanceof ServerPlayer player)
            || !player.server.getWorldData().getLevelName().equals(WORLD)) return;
        var level = player.serverLevel();
        level.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(() -> WorldIntroduction.Data.forWorldAge(200), (tag, registries) -> WorldIntroduction.Data.load(tag), null), "ender_infection_world_introduction").complete();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
        if (GLOW_CHECK) {
            prepareGlowCheck(player);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            teleport(player, SHOTS.get(0));
            ready = true;
            return;
        }
        level.setDayTime(4200);
        for (int x = -9; x <= 60; x++) {
            for (int z = -13; z <= 41; z++) {
                var block = x % 7 == 0 || z % 7 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE;
                level.setBlockAndUpdate(new BlockPos(x, FLOOR - 1, z), block.defaultBlockState());
            }
        }
        int slot = 0;
        for (String material : List.of("resonant", "ember", "void")) {
            double x = 2 + slot++ * 5;
            var stand = Objects.requireNonNull(EntityType.ARMOR_STAND.create(level));
            stand.moveTo(x, FLOOR, 0, 0, 0);
            stand.setShowArms(true);
            stand.setNoBasePlate(true);
            stand.setRightArmPose(new Rotations(-36, 0, 12));
            stand.setLeftArmPose(new Rotations(-12, 0, -13));
            for (EquipmentSlot armor : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                String piece = switch (armor) {
                    case HEAD -> "helmet";
                    case CHEST -> "chestplate";
                    case LEGS -> "leggings";
                    case FEET -> "boots";
                    default -> throw new IllegalStateException();
                };
                var item = Objects.requireNonNull(BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(EnderInfection.MOD_ID, material + "_crystal_" + piece)));
                stand.setItemSlot(armor, new ItemStack(item));
            }
            level.addFreshEntity(stand);
            shot("armor_" + material + "_front", x, 0, 1.1, 2.65, 1.4, 3.5);
            shot("armor_" + material + "_back", x, 0, 1.1, 2.55, 1.8, -3.4);
        }
        int index = 0;
        for (InfectedKind kind : InfectedKind.values()) {
            if (!kind.designed()) continue;
            double x = 2 + (index % 5) * 12;
            double z = 10 + (index++ / 5) * 12;
            var mob = Objects.requireNonNull(InfectedContent.TYPES.get(kind).get().create(level));
            double elevation = kind == InfectedKind.SPOREWING_SCOUT ? .75 : 0;
            mob.moveTo(x, FLOOR + elevation, z, 0, 0);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
            double distance = Math.max(2.5, Math.max(kind.width * 2.2, kind.height * 1.65));
            shot(kind.key, x, z, kind.height * .53 + elevation, distance * .70,
                 kind.height * .44 + .65, distance * .89);
        }
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        teleport(player, SHOTS.get(0));
        ready = true;
    }

    /** A fresh enclosed, unlit scene; never opens or edits a player's saved world. */
    private static void prepareGlowCheck(ServerPlayer player) {
        var level = player.serverLevel();
        level.setDayTime(18000);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, player.server);
        level.setWeatherParameters(6000, 0, false, false);
        int minX = -20, maxX = 104, minZ = -12, maxZ = 152, ceiling = FLOOR + 25;
        var shell = Blocks.GRAY_CONCRETE.defaultBlockState();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, FLOOR - 1, z), Blocks.DEEPSLATE_TILES.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, ceiling, z), shell, 2);
            }
        }
        for (int y = FLOOR; y < ceiling; y++) {
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), shell, 2);
                level.setBlock(new BlockPos(x, y, maxZ), shell, 2);
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), shell, 2);
                level.setBlock(new BlockPos(maxX, y, z), shell, 2);
            }
        }
        int index = 0;
        for (InfectedKind kind : InfectedKind.values()) {
            // Every non-core kind has crystals; mothers are captured separately at all five stages.
            if (kind == InfectedKind.CORE) continue;
            var mob = Objects.requireNonNull(InfectedContent.TYPES.get(kind).get().create(level));
            glowSubject(level, mob, kind.key, index++, kind == InfectedKind.SPOREWING_SCOUT ? .75 : 0);
        }
        glowSubject(level, Objects.requireNonNull(EnderInfection.MUTANT_ZOMBIE.get().create(level)),
                    "mutant_zombie", index++, 0);
        for (int stage = 1; stage <= 5; stage++) {
            var mother = Objects.requireNonNull(InfectedContent.TYPES.get(InfectedKind.CORE).get().create(level));
            var tag = new CompoundTag();
            mother.addAdditionalSaveData(tag);
            tag.putInt("MotherStage", stage);
            tag.putInt("ColonyBiomass", 0);
            tag.putBoolean("ExpansionNodeCreated", true);
            mother.readAdditionalSaveData(tag);
            glowSubject(level, mother, "mother_stage" + stage, index++, 0);
        }
        System.out.println("CRYSTAL_GLOW_SCENE subjects=" + SHOTS.size() / 2 + " shots=" + SHOTS.size()
            + " world=" + WORLD + " time=18000 gamma=0.0 enclosed=true");
    }

    private static void glowSubject(ServerLevel level, Mob mob, String name, int index, double elevation) {
        String selected = System.getProperty("ender_infection.crystalGlowSubjects", "");
        if (!selected.isBlank() && !List.of(selected.split(",")).contains(name)) return;
        var tag = new CompoundTag();
        mob.addAdditionalSaveData(tag);
        tag.putInt("EmergenceTicks", 0);
        mob.readAdditionalSaveData(tag);
        double x = 2 + (index % 5) * 20;
        double z = 10 + (index / 5) * 20;
        mob.moveTo(x, FLOOR + elevation, z, 0, 0);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        double width = mob.getBbWidth(), height = mob.getBbHeight();
        double distance = Math.max(2.5, Math.max(width * 2.2, height * 1.65));
        double centerX = x, centerZ = z;
        if (conceptBounds == null) {
            try (var stream = Objects.requireNonNull(CrystalVisualCheck.class.getResourceAsStream(
                     "/assets/ender_infection/concept_visual_bounds.json"));
                 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                conceptBounds = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception error) { throw new IllegalStateException("Missing concept geometry bounds", error); }
        }
        if (conceptBounds.has(name)) {
            var shape = conceptBounds.getAsJsonObject(name);
            width = shape.get("width").getAsDouble();
            height = shape.get("height").getAsDouble();
            distance = Math.max(2.8, Math.max(height * 1.8,
                Math.hypot(width, shape.get("depth").getAsDouble()) * .95));
            centerX += shape.get("center_x").getAsDouble();
            centerZ -= shape.get("center_z").getAsDouble();
        }
        double focus = height * .53 + elevation;
        for (boolean front : List.of(true, false)) {
            String shotName = name + (front ? "_front" : "_back");
            shot(shotName, centerX, centerZ, focus, (front ? 1 : -1) * distance * .70,
                 height * .44 + .65, (front ? 1 : -1) * distance * .89);
            var samples = new ArrayList<BlockPos>();
            samples.add(BlockPos.containing(SHOTS.get(SHOTS.size() - 1).eye));
            for (double y : new double[] {elevation + .1, focus, elevation + height}) {
                samples.add(BlockPos.containing(x, FLOOR + y, z));
                samples.add(BlockPos.containing(x + width * .5, FLOOR + y, z));
                samples.add(BlockPos.containing(x - width * .5, FLOOR + y, z));
            }
            DARK_SAMPLES.put(shotName, samples);
        }
    }

    private static void shot(String name, double x, double z, double focus, double dx, double dy, double dz) {
        SHOTS.add(new Shot(name, new Vec3(x + dx, FLOOR + focus + dy, z + dz), new Vec3(x, FLOOR + focus, z)));
    }

    private static void teleport(ServerPlayer player, Shot shot) {
        player.teleportTo(player.serverLevel(), shot.eye.x, shot.eye.y - player.getEyeHeight(), shot.eye.z,
                          shot.yaw(), shot.pitch());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!(GLOW_CHECK || Boolean.getBoolean("ender_infection.crystalCheck")) || finished) return;
        var client = Minecraft.getInstance();
        if (!launched && client.screen != null && client.getOverlay() == null) {
            launched = true;
            start = System.currentTimeMillis();
            client.options.hideGui = true;
            client.options.pauseOnLostFocus = false;
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.options.fov().set(48);
            client.options.gamma().set(GLOW_CHECK ? 0.0 : .7);
            if (GLOW_CHECK) org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow());
            client.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(),
                                  WorldDataConfiguration.DEFAULT), new WorldOptions(3172026L, false, false),
                r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), null);
        }
        if (launched && System.currentTimeMillis() - start > (GLOW_CHECK ? 360000 : 240000)) {
            throw new IllegalStateException("Timed out after " + frame + " crystal captures");
        }
        if (!ready || client.player == null || client.screen != null || client.getOverlay() != null) return;
        var player = client.player;
        var shot = SHOTS.get(frame);
        var pos = shot.eye.subtract(0, player.getEyeHeight(), 0);
        client.setCameraEntity(player);
        player.setPos(pos.x, pos.y, pos.z);
        player.setYRot(shot.yaw());
        player.setXRot(shot.pitch());
        player.yRotO = shot.yaw();
        player.xRotO = shot.pitch();
        player.setDeltaMovement(Vec3.ZERO);
        player.getAbilities().flying = true;
        if (++waitTicks < (GLOW_CHECK ? (frame == 0 ? 100 : 25) : (frame == 0 ? 32 : 17))) return;
        if (GLOW_CHECK) {
            // The room is built during login, before initial chunk/light packets settle.
            // Resend authoritative light data after the camera's chunks have arrived.
            if (!lightRefreshRequested) {
                lightRefreshRequested = true;
                var server = Objects.requireNonNull(client.getSingleplayerServer());
                var id = player.getUUID();
                server.execute(() -> {
                    var recipient = Objects.requireNonNull(server.getPlayerList().getPlayer(id));
                    var level = recipient.serverLevel();
                    var chunks = new HashSet<ChunkPos>();
                    int serverBlockLight = 0, serverSkyLight = 0;
                    for (BlockPos sample : DARK_SAMPLES.get(shot.name)) {
                        var center = new ChunkPos(sample);
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) chunks.add(new ChunkPos(center.x + dx, center.z + dz));
                        }
                        serverBlockLight = Math.max(serverBlockLight, level.getBrightness(LightLayer.BLOCK, sample));
                        serverSkyLight = Math.max(serverSkyLight, level.getBrightness(LightLayer.SKY, sample));
                    }
                    for (ChunkPos chunk : chunks) {
                        recipient.connection.send(new ClientboundLightUpdatePacket(chunk, level.getLightEngine(), null, null));
                    }
                    System.out.println("CRYSTAL_GLOW_LIGHT_SYNC " + shot.name + " chunks=" + chunks.size()
                        + " serverBlock=" + serverBlockLight + " serverSky=" + serverSkyLight);
                    lightRefreshSent = true;
                });
                return;
            }
            if (!lightRefreshSent || ++lightRefreshWait < 20) return;
            int blockLight = 0, skyLight = 0;
            for (BlockPos sample : DARK_SAMPLES.get(shot.name)) {
                if (!client.level.hasChunkAt(sample)) return;
                blockLight = Math.max(blockLight, client.level.getBrightness(LightLayer.BLOCK, sample));
                skyLight = Math.max(skyLight, client.level.getBrightness(LightLayer.SKY, sample));
            }
            if (blockLight != 0 || skyLight != 0) {
                if (waitTicks < 300) return;
                throw new IllegalStateException("Unlit scene check failed for " + shot.name
                    + ": block=" + blockLight + " sky=" + skyLight);
            }
            System.out.println("CRYSTAL_GLOW_LIGHT " + shot.name + " block=" + blockLight + " sky=" + skyLight
                + " probes=" + DARK_SAMPLES.get(shot.name).size());
        }
        try {
            var directory = client.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(directory);
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(directory.resolve(shot.name + ".png"));
            }
        } catch (IOException error) {
            throw new IllegalStateException("Capture failed: " + shot.name, error);
        }
        System.out.println((GLOW_CHECK ? "CRYSTAL_GLOW_CAPTURE " : "CRYSTAL_CAPTURE ") + shot.name);
        if (++frame == SHOTS.size()) {
            finished = true;
            System.out.println((GLOW_CHECK ? "CRYSTAL_GLOW_VISUAL_PASS screenshots=" : "CRYSTAL_VISUAL_PASS screenshots=") + frame);
            client.stop();
        } else {
            waitTicks = 0;
            lightRefreshRequested = false;
            lightRefreshSent = false;
            lightRefreshWait = 0;
            var server = client.getSingleplayerServer();
            var id = player.getUUID();
            var next = SHOTS.get(frame);
            server.execute(() -> teleport(Objects.requireNonNull(server.getPlayerList().getPlayer(id)), next));
        }
    }
}
