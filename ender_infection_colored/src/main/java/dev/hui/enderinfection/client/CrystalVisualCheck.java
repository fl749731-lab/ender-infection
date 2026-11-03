package dev.hui.enderinfection.client;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Opt-in client fixture. Every frame comes from the running Forge renderer. */
@Mod.EventBusSubscriber(modid = EnderInfection.MOD_ID, value = Dist.CLIENT)
public final class CrystalVisualCheck {
    private static final String WORLD = "CrystalGeometryReview-" + Long.toUnsignedString(System.nanoTime());
    private static final int FLOOR = -60;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static boolean launched, finished;
    private static volatile boolean ready;
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
        if (!Boolean.getBoolean("ender_infection.crystalCheck") || !(event.getEntity() instanceof ServerPlayer player)
            || !player.server.getWorldData().getLevelName().equals(WORLD)) return;
        var level = player.serverLevel();
        level.getDataStorage().computeIfAbsent(WorldIntroduction.Data::load, () -> WorldIntroduction.Data.forWorldAge(200),
            "ender_infection_world_introduction").complete();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
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
                var item = Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation(EnderInfection.MOD_ID, material + "_crystal_" + piece)));
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

    private static void shot(String name, double x, double z, double focus, double dx, double dy, double dz) {
        SHOTS.add(new Shot(name, new Vec3(x + dx, FLOOR + focus + dy, z + dz), new Vec3(x, FLOOR + focus, z)));
    }

    private static void teleport(ServerPlayer player, Shot shot) {
        player.teleportTo(player.serverLevel(), shot.eye.x, shot.eye.y - player.getEyeHeight(), shot.eye.z,
                          shot.yaw(), shot.pitch());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("ender_infection.crystalCheck") || finished || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (!launched && client.screen != null && client.getOverlay() == null) {
            launched = true;
            start = System.currentTimeMillis();
            client.options.hideGui = true;
            client.options.pauseOnLostFocus = false;
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.options.fov().set(48);
            client.options.gamma().set(.7);
            client.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(),
                                  WorldDataConfiguration.DEFAULT), new WorldOptions(3172026L, false, false),
                r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
        if (launched && System.currentTimeMillis() - start > 240000) {
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
        if (++waitTicks < (frame == 0 ? 32 : 17)) return;
        try {
            var directory = client.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(directory);
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(directory.resolve(shot.name + ".png"));
            }
        } catch (IOException error) {
            throw new IllegalStateException("Capture failed: " + shot.name, error);
        }
        System.out.println("CRYSTAL_CAPTURE " + shot.name);
        if (++frame == SHOTS.size()) {
            finished = true;
            System.out.println("CRYSTAL_VISUAL_PASS screenshots=" + frame);
            client.stop();
        } else {
            waitTicks = 0;
            var server = client.getSingleplayerServer();
            var id = player.getUUID();
            var next = SHOTS.get(frame);
            server.execute(() -> teleport(Objects.requireNonNull(server.getPlayerList().getPlayer(id)), next));
        }
    }
}
