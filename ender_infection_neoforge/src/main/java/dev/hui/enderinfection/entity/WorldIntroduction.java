package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

@EventBusSubscriber(modid = EnderInfection.MOD_ID)
public final class WorldIntroduction {
    private static final ResourceLocation IMPACT_ADVANCEMENT =
        ResourceLocation.fromNamespaceAndPath(EnderInfection.MOD_ID, "meteor_impact");

    private WorldIntroduction() {}

    public static final class Data extends SavedData {
        private boolean eligible;
        private boolean completed;
        private UUID player;
        private long due;
        private BlockPos landing;
        private int flight;
        private UUID display;
        private boolean impacted;

        public static Data forWorldAge(long age) {
            Data data = new Data();
            data.eligible = age < 200;
            data.setDirty();
            return data;
        }

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            data.eligible = tag.getBoolean("Eligible");
            data.completed = tag.getBoolean("Completed");
            data.due = tag.getLong("Due");
            if (tag.hasUUID("Player")) data.player = tag.getUUID("Player");
            if (tag.contains("Landing")) data.landing = BlockPos.of(tag.getLong("Landing"));
            data.flight = Math.max(0, Math.min(OpeningMeteor.FLIGHT_TICKS, tag.getInt("Flight")));
            if (tag.hasUUID("Display")) data.display = tag.getUUID("Display");
            data.impacted = tag.getBoolean("Impacted");
            return data;
        }

        public boolean schedule(UUID id, long now) {
            if (!eligible || completed || player != null) return false;
            player = id;
            due = now + 100;
            setDirty();
            return true;
        }

        public boolean ready(long now) {
            return eligible && !completed && player != null && now >= due;
        }

        public void complete() {
            completed = true;
            clearFlight();
        }

        public void beginFlight(BlockPos site) {
            if (completed || landing != null) return;
            landing = site.immutable();
            setDirty();
        }

        private void clearFlight() {
            landing = null;
            flight = 0;
            display = null;
            impacted = false;
            setDirty();
        }

        public CompoundTag save(CompoundTag tag){return save(tag,net.minecraft.core.RegistryAccess.EMPTY);}
        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            tag.putBoolean("Eligible", eligible);
            tag.putBoolean("Completed", completed);
            tag.putLong("Due", due);
            tag.putInt("Flight", flight);
            tag.putBoolean("Impacted", impacted);
            if (player != null) tag.putUUID("Player", player);
            if (landing != null) tag.putLong("Landing", landing.asLong());
            if (display != null) tag.putUUID("Display", display);
            return tag;
        }
    }

    private static Data data(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(() -> Data.forWorldAge(world.getGameTime()), (tag, registries) -> Data.load(tag), null), "ender_infection_world_introduction");
    }

    @SubscribeEvent
    public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel world = player.serverLevel().getServer().overworld();
            data(world).schedule(player.getUUID(), world.getGameTime());
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel world = server.overworld();
        Data data = data(world);
        long now = world.getGameTime();
        if (!data.ready(now) || world.getDifficulty() == Difficulty.PEACEFUL || world.players().isEmpty()) return;

        if (data.landing == null) {
            if (now != data.due && now % 40 != 0) return;
            ServerPlayer player = server.getPlayerList().getPlayer(data.player);
            if (player == null || player.serverLevel() != world) player = world.players().get(0);
            BlockPos site = findLanding(world, player);
            if (site == null) return;
            data.beginFlight(site);
            player.sendSystemMessage(Component.translatable("message.ender_infection.meteor_incoming", site.getX(), site.getZ()));
        }

        if (data.impacted && now % 40 != 0) return;
        InfectedMob mother = advanceMeteor(world, data);
        if (mother == null) return;
        BlockPos site = mother.blockPosition();
        var message = Component.translatable("message.ender_infection.mother_arrived", site.getX(), site.getY(), site.getZ());
        var advancement = server.getAdvancements().get(IMPACT_ADVANCEMENT);
        for (ServerPlayer recipient : world.players()) {
            recipient.sendSystemMessage(message);
            if (advancement != null && recipient.distanceToSqr(mother) <= 96 * 96) {
                recipient.getAdvancements().award(advancement, "impact");
            }
        }
    }

    /** Advance an already scheduled flight. Unloaded landing chunks leave the saved clock paused. */
    public static InfectedMob advanceMeteor(ServerLevel world, Data data) {
        BlockPos site = data.landing;
        if (data.completed || site == null || !world.hasChunkAt(site)) return null;
        if (data.flight < OpeningMeteor.FLIGHT_TICKS) {
            UUID display = OpeningMeteor.move(world, site, data.display, data.flight);
            if (display == null) return null;
            data.display = display;
            data.flight++;
            data.setDirty();
            return null;
        }

        if (!data.impacted) {
            if (!OpeningMeteor.blastAreaLoaded(world, site)) return null;
            OpeningMeteor.remove(world, data.display);
            OpeningMeteor.impact(world, site);
            data.display = null;
            data.impacted = true;
            data.setDirty();
        }

        InfectedMob mother = findCraterSpawn(world, site);
        if (mother == null || !world.addFreshEntity(mother)) return null;
        data.complete();
        return mother;
    }

    private static InfectedMob findCraterSpawn(ServerLevel world, BlockPos site) {
        InfectedMob rim = null;
        for (int radius = 0; radius <= 4; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    BlockPos column = site.offset(x, 0, z);
                    if (!world.hasChunkAt(column)) continue;
                    InfectedMob mother = craterColumnSpawn(world, column);
                    if (mother == null) continue;
                    if (mother.getY() < site.getY()) return mother;
                    if (rim == null) rim = mother;
                }
            }
        }
        return rim;
    }

    private static InfectedMob craterColumnSpawn(ServerLevel world, BlockPos column) {
        // Scan the impact depth locally. A distant roof or overhang must not become the spawn surface.
        for (int height = 1; height >= -4; height--) {
            BlockPos floor = column.above(height);
            var state = world.getBlockState(floor);
            if (!state.getFluidState().isEmpty()) return null;
            if (!state.getCollisionShape(world, floor).isEmpty()) {
                return prepareMother(world, floor.above());
            }
        }
        return null;
    }

    private static BlockPos findLanding(ServerLevel world, ServerPlayer player) {
        BlockPos center = player.blockPosition();
        var look = player.getLookAngle();
        double heading = Math.atan2(look.z, look.x);
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = attempt < 12 ? heading + (world.random.nextDouble() - .5) * Math.PI / 2
                : world.random.nextDouble() * Math.PI * 2;
            int radius = 24 + world.random.nextInt(9);
            BlockPos column = center.offset((int) Math.round(Math.cos(angle) * radius), 0,
                (int) Math.round(Math.sin(angle) * radius));
            if (!world.hasChunkAt(column)) continue;
            BlockPos site = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (site.getY() > world.getMaxBuildHeight() - 16 || !world.canSeeSky(site)) continue;
            if (OpeningMeteor.canLand(world, site) && prepareMother(world, site) != null) return site;
        }
        return null;
    }

    private static InfectedMob prepareMother(ServerLevel world, BlockPos site) {
        // The mother can overlap adjacent chunks near a column boundary.
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                if (!world.hasChunkAt(site.offset(x, 0, z))) return null;
            }
        }
        var floor = world.getBlockState(site.below());
        if (!floor.getFluidState().isEmpty() || !floor.isFaceSturdy(world, site.below(), Direction.UP)) return null;
        var mother = InfectedContent.TYPES.get(InfectedKind.CORE).get().create(world);
        if (mother == null) return null;
        mother.moveTo(site.getX() + .5, site.getY(), site.getZ() + .5, world.random.nextFloat() * 360, 0);
        var bounds = mother.getBoundingBox();
        if (!world.getWorldBorder().isWithinBounds(bounds) || !world.noCollision(mother, bounds)
            || world.containsAnyLiquid(bounds)) return null;
        mother.setPersistenceRequired();
        return mother;
    }
}
