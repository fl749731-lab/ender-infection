package dev.hui.enderinfection.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.entity.WorldIntroduction;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Opt-in native front/back, attack windup, and impact captures of revised creatures. */
@Mod.EventBusSubscriber(modid = EnderInfection.MOD_ID, value = Dist.CLIENT)
public final class CreatureConceptCheck {
    private static final String WORLD = "CreatureConceptReview-" + Long.toUnsignedString(System.nanoTime());
    private static final int FLOOR = -60;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static volatile boolean ready;
    private static boolean launched, finished;
    private static int frame, ticks;
    private static long start;

    private record Shot(String name, UUID subject, int entityId, Vec3 eye, Vec3 focus, int captureTicks, boolean startAttack) {
        float yaw() { var d = focus.subtract(eye); return (float)Math.toDegrees(Math.atan2(-d.x, d.z)); }
        float pitch() { var d = focus.subtract(eye); return (float)Math.toDegrees(Math.atan2(-d.y, d.horizontalDistance())); }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("ender_infection.conceptCheck") || !(event.getEntity() instanceof ServerPlayer player)
            || !player.server.getWorldData().getLevelName().equals(WORLD)) return;
        var level = player.serverLevel();
        level.getDataStorage().computeIfAbsent(WorldIntroduction.Data::load, () -> WorldIntroduction.Data.forWorldAge(200),
            "ender_infection_world_introduction").complete();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, player.server);
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        for (int x = -24; x <= 128; x++) for (int z = -20; z <= 92; z++)
            level.setBlock(new BlockPos(x, FLOOR - 1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        JsonObject bounds;
        try (var stream = Objects.requireNonNull(CreatureConceptCheck.class.getResourceAsStream(
                 "/assets/ender_infection/concept_visual_bounds.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            bounds = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception error) { throw new IllegalStateException("Missing concept geometry bounds", error); }
        Set<String> selected = selectedSubjects();
        int index = 0;
        for (InfectedKind kind : InfectedKind.values()) {
            if (!kind.designed() || (!selected.isEmpty() && !selected.contains(kind.key))) continue;
            double x = 3 + (index % 5) * 24, z = 12 + (index++ / 5) * 24;
            double elevation = kind == InfectedKind.SPOREWING_SCOUT ? .8 : 0;
            var mob = Objects.requireNonNull(InfectedContent.TYPES.get(kind).get().create(level));
            var tag = new CompoundTag(); mob.addAdditionalSaveData(tag); tag.putInt("EmergenceTicks", 0); mob.readAdditionalSaveData(tag);
            mob.moveTo(x, FLOOR + elevation, z, 0, 0);
            mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired(); level.addFreshEntity(mob);
            System.out.println("CONCEPT_VISUAL_SUBJECT " + kind.key + " hit_tick=" + kind.hitTick
                + " duration_ticks=" + kind.duration + " no_ai=" + mob.isNoAi());
            var shape = bounds.getAsJsonObject(kind.key);
            double height = shape.get("height").getAsDouble();
            double distance = Math.max(2.8, Math.max(height * 1.8,
                Math.hypot(shape.get("width").getAsDouble(), shape.get("depth").getAsDouble()) * .95));
            var focus = new Vec3(x + shape.get("center_x").getAsDouble(), FLOOR + elevation + height * .48,
                                z - shape.get("center_z").getAsDouble());
            for (String view : List.of("front", "back", "windup", "attack")) {
                double side = view.equals("back") ? -1 : 1;
                var eye = focus.add(side * distance * .68, height * .30 + .35, side * distance * .90);
                int windupTicks = Math.max(1, kind.hitTick * 5 / 8);
                int captureTicks = view.equals("attack") ? Math.max(1, kind.hitTick - windupTicks)
                    : view.equals("windup") ? windupTicks + 2 : 22;
                SHOTS.add(new Shot(kind.key + "_" + view, mob.getUUID(), mob.getId(), eye, focus,
                                   captureTicks, view.equals("windup")));
            }
        }
        if (SHOTS.isEmpty()) throw new IllegalStateException("No selected concept subjects");
        player.getAbilities().flying = true; player.onUpdateAbilities();
        activate(player, SHOTS.get(0)); ready = true;
        System.out.println("CONCEPT_VISUAL_SCENE subjects=" + index + " shots=" + SHOTS.size());
    }

    private static Set<String> selectedSubjects() {
        String requested = System.getProperty("ender_infection.conceptSubjects", "");
        Set<String> selected = new HashSet<>();
        if (requested.isBlank()) return selected;
        Set<String> valid = new HashSet<>();
        for (InfectedKind kind : InfectedKind.values()) if (kind.designed()) valid.add(kind.key);
        for (String value : requested.split(",", -1)) {
            String key = value.trim();
            if (!valid.contains(key)) throw new IllegalArgumentException("Unknown concept subject: " + key);
            selected.add(key);
        }
        return selected;
    }

    private static void activate(ServerPlayer player, Shot shot) {
        player.teleportTo(player.serverLevel(), shot.eye.x, shot.eye.y - player.getEyeHeight(), shot.eye.z, shot.yaw(), shot.pitch());
        if (shot.startAttack && player.serverLevel().getEntity(shot.subject) instanceof InfectedMob mob)
            mob.triggerAnim("action", "special");
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("ender_infection.conceptCheck") || finished || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (!launched && client.screen != null && client.getOverlay() == null) {
            launched = true; start = System.currentTimeMillis();
            client.options.hideGui = true; client.options.pauseOnLostFocus = false;
            client.options.setCameraType(CameraType.FIRST_PERSON); client.options.fov().set(48); client.options.gamma().set(.7);
            GLFW.glfwRestoreWindow(client.getWindow().getWindow());
            GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 800);
            GLFW.glfwHideWindow(client.getWindow().getWindow());
            client.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                new WorldOptions(3172026L, false, false),
                r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
        if (launched && System.currentTimeMillis() - start > 300000) throw new IllegalStateException("Concept capture timed out at " + frame);
        if (!ready || client.player == null || client.level == null || client.screen != null || client.getOverlay() != null) return;
        var shot = SHOTS.get(frame); var player = client.player;
        if (client.level.getEntity(shot.entityId) == null) return;
        client.setCameraEntity(player); var pos = shot.eye.subtract(0, player.getEyeHeight(), 0);
        player.setPos(pos.x, pos.y, pos.z); player.setYRot(shot.yaw()); player.setXRot(shot.pitch());
        player.yRotO = shot.yaw(); player.xRotO = shot.pitch(); player.setDeltaMovement(Vec3.ZERO); player.getAbilities().flying = true;
        if (++ticks < (frame == 0 ? 70 : shot.captureTicks)) return;
        try {
            var directory = client.gameDirectory.toPath().resolve("screenshots"); Files.createDirectories(directory);
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) { image.writeToFile(directory.resolve(shot.name + ".png")); }
        } catch (Exception error) { throw new IllegalStateException("Concept capture failed: " + shot.name, error); }
        System.out.println("CONCEPT_CAPTURE " + shot.name);
        if (++frame == SHOTS.size()) {
            finished = true; System.out.println("CONCEPT_VISUAL_PASS screenshots=" + frame); client.stop();
        } else {
            ticks = 0; var server = Objects.requireNonNull(client.getSingleplayerServer()); var id = player.getUUID(); var next = SHOTS.get(frame);
            server.execute(() -> activate(Objects.requireNonNull(server.getPlayerList().getPlayer(id)), next));
        }
    }
}
