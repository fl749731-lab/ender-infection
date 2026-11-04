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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Opt-in native front/back, attack windup, and impact captures of revised creatures. */
@EventBusSubscriber(modid = EnderInfection.MOD_ID, value = Dist.CLIENT)
public final class RiftVisualCheck {
    private static final String WORLD = "RiftPrototypeReview-" + Long.toUnsignedString(System.nanoTime());
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
        if (!Boolean.getBoolean("ender_infection.riftVisualCheck") || !(event.getEntity() instanceof ServerPlayer player)
            || !player.server.getWorldData().getLevelName().equals(WORLD)) return;
        var level = player.serverLevel();
        level.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(() -> WorldIntroduction.Data.forWorldAge(200), (tag, registries) -> WorldIntroduction.Data.load(tag), null), "ender_infection_world_introduction").complete();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, player.server);
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        for (int x = -24; x <= 128; x++) for (int z = -20; z <= 92; z++)
            level.setBlock(new BlockPos(x, FLOOR - 1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        dev.hui.enderinfection.rift.RiftOutpost.place(level,new BlockPos(16,FLOOR,16));
        level.getEntitiesOfClass(InfectedMob.class,new net.minecraft.world.phys.AABB(-16,FLOOR-2,-16,64,FLOOR+12,64)).forEach(m->m.setNoAi(true));
        var anchor=(dev.hui.enderinfection.rift.RiftAnchorEntity)level.getBlockEntity(new BlockPos(16,FLOOR,16));
        var overviewFocus=new Vec3(16,FLOOR+1.5,16);
        SHOTS.add(new Shot("outpost_active",player.getUUID(),player.getId(),overviewFocus.add(17,13,21),overviewFocus,35,false));
        int index=0;
        for(InfectedKind kind:List.of(InfectedKind.EARLY_ENDERMAN,InfectedKind.ENDERMAN,InfectedKind.ANCHOR_GUARD)){
            double x=72+index++*18,z=12;
            var mob=Objects.requireNonNull(InfectedContent.TYPES.get(kind).get().create(level));
            var tag=new CompoundTag();mob.addAdditionalSaveData(tag);tag.putInt("EmergenceTicks",0);mob.readAdditionalSaveData(tag);
            mob.moveTo(x,FLOOR,z,0,0);mob.setNoAi(true);mob.setNoGravity(true);mob.setPersistenceRequired();level.addFreshEntity(mob);
            var focus=new Vec3(x,FLOOR+kind.height*.5,z);double distance=kind.height*2;
            for(String view:List.of("front","back","attack","exposed")){
                if(view.equals("exposed")&&kind!=InfectedKind.ANCHOR_GUARD)continue;
                var eye=focus.add((view.equals("back")?-1:1)*distance*.60,kind.height*.23,distance*(view.equals("back")?-1:1)*.9);
                SHOTS.add(new Shot(kind.key+"_"+view,mob.getUUID(),mob.getId(),eye,focus,view.equals("attack")?kind.hitTick:25,view.equals("attack")));
            }
        }
        if (SHOTS.isEmpty()) throw new IllegalStateException("No selected concept subjects");
        player.getAbilities().flying = true; player.onUpdateAbilities();
        activate(player, SHOTS.get(0)); ready = true;
        System.out.println("RIFT_VISUAL_SCENE subjects=" + index + " shots=" + SHOTS.size());
    }

    private static void activate(ServerPlayer player, Shot shot) {
        player.teleportTo(player.serverLevel(), shot.eye.x, shot.eye.y - player.getEyeHeight(), shot.eye.z, shot.yaw(), shot.pitch());
        if(player.serverLevel().getEntity(shot.subject) instanceof InfectedMob mob){
            if(shot.startAttack)mob.triggerAnim("action","special");
            if(shot.name.endsWith("exposed"))mob.setRiftExposed(true);
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ender_infection.riftVisualCheck") || finished) return;
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
                r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), null);
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
        System.out.println("RIFT_CAPTURE " + shot.name);
        if (++frame == SHOTS.size()) {
            finished = true; System.out.println("RIFT_VISUAL_PASS screenshots=" + frame); client.stop();
        } else {
            ticks = 0; var server = Objects.requireNonNull(client.getSingleplayerServer()); var id = player.getUUID(); var next = SHOTS.get(frame);
            server.execute(() -> activate(Objects.requireNonNull(server.getPlayerList().getPlayer(id)), next));
        }
    }
}
