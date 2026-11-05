package dev.hui.enderinfection.institute;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.api.institute.FacilityEvent;
import dev.hui.enderinfection.api.institute.FacilityStatus;
import dev.hui.enderinfection.api.institute.InstituteApi;
import dev.hui.enderinfection.api.institute.InstituteOperationsService;
import dev.hui.enderinfection.design.LabDetailBlock;
import dev.hui.enderinfection.design.LabFixtureBlock;
import dev.hui.enderinfection.design.LabFixtureEntity;
import dev.hui.enderinfection.processing.LabProcessingEntity;
import dev.hui.enderinfection.worldgen.CentralInstitute;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Two fixed facility circuits and an exploration task; not a general energy network. */
@EventBusSubscriber(modid = EnderInfection.MOD_ID)
public final class InstituteOperations implements InstituteOperationsService {
    private static final InstituteOperations INSTANCE = new InstituteOperations();
    private static final Map<Object, Layout> LAYOUTS = new WeakHashMap<>();
    private static boolean installed;
    private record Target(BlockPos offset, String zone, String role) {}
    private record Layout(Map<BlockPos, String> zones, Map<BlockPos, String> doors, Map<BlockPos, String> machines,
                          List<Target> lights, List<Target> controls) {}
    private record Binding(OperationsData data, OperationsData.Facility facility, Layout layout, BlockPos relative) {}
    private InstituteOperations() {}
    public static synchronized void install() {
        if (!installed) { InstituteApi.installOperationsService(INSTANCE); installed = true; }
    }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(EnderInfection.MOD_ID, name); }
    private static BlockPos position(JsonElement element) {
        if (element.isJsonArray()) { var a = element.getAsJsonArray(); return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()); }
        var o = element.getAsJsonObject(); return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
    }
    private static JsonArray array(JsonObject object, String key) { return object.has(key) ? object.getAsJsonArray(key) : new JsonArray(); }
    private static String zone(JsonObject object) {
        String result = object.has("zone") ? object.get("zone").getAsString() : "service";
        if (!result.equals("service") && !result.equals("containment")) throw new IllegalArgumentException("Unknown institute circuit " + result);
        return result;
    }
    private static Layout layout(ServerLevel level) {
        Object resources = level.getServer().getResourceManager();
        var cached = LAYOUTS.get(resources); if (cached != null) return cached;
        try (var reader = level.getServer().getResourceManager().getResourceOrThrow(id("central_institute.json")).openAsReader()) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("operations")) throw new IllegalArgumentException("Institute manifest has no operations section");
            var ops = root.getAsJsonObject("operations"); var zones = new HashMap<BlockPos, String>(); var doors = new HashMap<BlockPos, String>();
            var machines = new HashMap<BlockPos, String>();
            var lights = new ArrayList<Target>(); var controls = new ArrayList<Target>();
            for (String kind : List.of("secured_doors", "managed_lights", "machines", "controls")) for (var value : array(ops, kind)) {
                var object = value.getAsJsonObject(); var pos = position(object.get("pos"));
                if (!validOffset(pos)) throw new IllegalArgumentException("Institute operation outside layout: " + pos);
                String zone = zone(object); zones.put(pos, zone);
                if (kind.equals("secured_doors")) doors.put(pos, zone);
                if (kind.equals("machines")) machines.put(pos, object.get("device").getAsString());
                if (kind.equals("managed_lights")) lights.add(new Target(pos, zone, ""));
                if (kind.equals("controls")) controls.add(new Target(pos, zone, normalizeRole(object.get("role").getAsString())));
            }
            cached = new Layout(Map.copyOf(zones), Map.copyOf(doors), Map.copyOf(machines), List.copyOf(lights), List.copyOf(controls));
            LAYOUTS.put(resources, cached); return cached;
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
    public static boolean validOffset(BlockPos offset) {
        return offset.getX() >= 0 && offset.getX() < CentralInstitute.WIDTH && offset.getY() >= 0 && offset.getY() < CentralInstitute.HEIGHT
                && offset.getZ() >= 0 && offset.getZ() < CentralInstitute.LENGTH;
    }
    public static BlockPos registerBound(ServerLevel level, BlockPos worldPos, BlockPos offset) {
        if (!validOffset(offset)) throw new IllegalArgumentException("Invalid institute binding offset");
        long x = (long)worldPos.getX() - offset.getX(), y = (long)worldPos.getY() - offset.getY(), z = (long)worldPos.getZ() - offset.getZ();
        if (Math.abs(x) > 30000000L || Math.abs(z) > 30000000L || y < level.getMinBuildHeight() || y + CentralInstitute.HEIGHT > level.getMaxBuildHeight())
            throw new IllegalArgumentException("Invalid institute binding origin");
        var origin = new BlockPos((int)x, (int)y, (int)z); registerFacility(level, origin); return origin;
    }
    public static void registerFacility(ServerLevel level, BlockPos origin) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Institute registration requires the server thread");
        if (CentralInstitute.validateSite(level, origin, net.minecraft.world.level.block.Rotation.NONE) != null)
            throw new IllegalArgumentException("Institute origin exceeds world bounds");
        layout(level); var data = OperationsData.get(level); var facility = data.find(origin);
        if (facility == null) {
            facility = new OperationsData.Facility(origin); data.facilities.put(origin.asLong(), facility); data.setDirty();
            fire(level, snapshot(facility), snapshot(facility), "registered");
        }
        data.refresh(origin);
    }
    private static Optional<Binding> binding(ServerLevel level, BlockPos pos) {
        if (!level.getServer().isSameThread()) return Optional.empty();
        var data = OperationsData.get(level);
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null) {
            var be = level.getBlockEntity(pos);
            Optional<BlockPos> origin = be instanceof OperationsControlEntity control ? control.boundOrigin()
                    : be instanceof LabFixtureEntity fixture ? fixture.boundOrigin()
                    : be instanceof LabProcessingEntity machine ? machine.boundOrigin() : Optional.empty();
            if (origin.isPresent()) {
                var facility = data.find(origin.get());
                if (facility == null) return Optional.empty();
                return Optional.of(new Binding(data, facility, layout(level), pos.subtract(facility.origin)));
            }
        }
        for (var facility : data.facilities.values()) {
            var relative = pos.subtract(facility.origin);
            if (validOffset(relative)) return Optional.of(new Binding(data, facility, layout(level), relative));
        }
        return Optional.empty();
    }
    private static FacilityStatus snapshot(OperationsData.Facility facility) {
        return new FacilityStatus(facility.origin, facility.repaired, facility.servicePowered, facility.containmentPowered,
                facility.analyzed, facility.separated, facility.archiveClaimed);
    }
    @Override public Optional<FacilityStatus> status(ServerLevel level, BlockPos pos) { return binding(level, pos).map(b -> snapshot(b.facility)); }
    private static boolean powered(OperationsData.Facility facility, String zone) {
        return facility.repaired && (zone.equals("containment") ? facility.containmentPowered : facility.servicePowered);
    }
    private static boolean validBoundMachine(ServerLevel level, BlockPos pos, Binding bound) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntity(pos) instanceof LabProcessingEntity machine)) return false;
        return machine.boundOrigin().filter(bound.facility.origin::equals).isPresent()
                && machine.deviceId().equals(bound.layout.machines.get(bound.relative));
    }
    @Override public boolean powered(ServerLevel level, BlockPos pos) {
        var found = binding(level, pos); if (found.isEmpty()) return false; var bound = found.get();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (bound.layout.machines.containsKey(bound.relative)
                || chunk != null && chunk.getBlockEntity(pos) instanceof LabProcessingEntity)
            if (!validBoundMachine(level, pos, bound)) return false;
        String zone = bound.layout.zones.get(bound.relative); return zone != null && powered(bound.facility, zone);
    }
    @Override public void completeProcess(ServerLevel level, BlockPos pos, String recipeId) {
        var found = binding(level, pos); if (found.isEmpty()) return; var bound = found.get();
        if (!validBoundMachine(level, pos, bound) || !powered(level, pos)) return;
        var previous = snapshot(bound.facility); String key = recipeId.substring(recipeId.indexOf(':') + 1);
        if (recipeId.contains(":") && !recipeId.startsWith(EnderInfection.MOD_ID + ":")) { fire(level, previous, previous, "process:" + recipeId); return; }
        String device = bound.layout.machines.get(bound.relative);
        if (key.equals("microscope_analysis") && device.equals(EnderInfection.MOD_ID + ":lab_microscope")) bound.facility.analyzed = true;
        else if (key.equals("centrifuge_separation") && device.equals(EnderInfection.MOD_ID + ":lab_centrifuge")) bound.facility.separated = true;
        else { fire(level, previous, previous, "process:" + key); return; }
        changed(level, bound, previous, "process:" + key);
    }
    private static void fire(ServerLevel level, FacilityStatus previous, FacilityStatus current, String reason) {
        try { InstituteApi.fireFacilityEvent(new FacilityEvent(level, previous, current, reason)); }
        catch (RuntimeException error) { LogUtils.getLogger().error("Institute change listener failed", error); }
    }
    private static void changed(ServerLevel level, Binding bound, FacilityStatus previous, String reason) {
        var current = snapshot(bound.facility); if (current.equals(previous)) return;
        bound.data.setDirty(); bound.data.restartRefresh(bound.facility.origin); fire(level, previous, current, reason);
    }
    private static String normalizeRole(String role) {
        return switch (role) { case "lab_power_generator", "power_generator" -> "generator";
            case "lab_distribution_panel", "distribution_panel" -> "distribution";
            case "lab_research_terminal", "research_terminal" -> "terminal";
            case "lab_archive_terminal", "archive_terminal" -> "archive"; default -> role; };
    }
    private static Item item(String key) {
        var result = BuiltInRegistries.ITEM.getOptional(id(key)).orElseThrow(() -> new IllegalStateException("Missing institute item " + key));
        if (result == Items.AIR) throw new IllegalStateException("Missing institute item " + key); return result;
    }
    private static InteractionResult message(Player player, String text, boolean success) {
        player.displayClientMessage(Component.literal(text), false); return success ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
    /** Simulate all inputs/outputs first; never drop a reward if the inventory is full. */
    private static boolean transact(ServerPlayer player, Map<Item, Integer> costs, List<ItemStack> rewards) {
        var inventory = player.getInventory(); var planned = new ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) planned.add(inventory.getItem(i).copy());
        for (var cost : costs.entrySet()) {
            int remaining = cost.getValue();
            for (int i = 0; i < planned.size() && remaining > 0; i++) if (planned.get(i).is(cost.getKey())) {
                int take = Math.min(remaining, planned.get(i).getCount()); planned.get(i).shrink(take); remaining -= take;
            }
            if (remaining != 0) return false;
        }
        for (var reward : rewards) {
            var remaining = reward.copy();
            for (int i = 0; i < 36 && !remaining.isEmpty(); i++) {
                var slot = planned.get(i);
                if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, remaining)) {
                    int count = Math.min(remaining.getCount(), Math.max(0, Math.min(64, slot.getMaxStackSize()) - slot.getCount()));
                    slot.grow(count); remaining.shrink(count);
                }
            }
            for (int i = 0; i < 36 && !remaining.isEmpty(); i++) if (planned.get(i).isEmpty()) {
                int count = Math.min(remaining.getCount(), Math.min(64, remaining.getMaxStackSize()));
                var stack = remaining.copy(); stack.setCount(count); planned.set(i, stack); remaining.shrink(count);
            }
            if (!remaining.isEmpty()) return false;
        }
        for (int i = 0; i < planned.size(); i++) inventory.setItem(i, planned.get(i));
        inventory.setChanged(); return true;
    }
    private static void synchronizeInventory(ServerPlayer player) {
        player.inventoryMenu.broadcastChanges(); if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }
    public static InteractionResult useControl(ServerLevel level, BlockPos pos, ServerPlayer player, String role) {
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                || !(level.getBlockEntity(pos) instanceof OperationsControlEntity control) || control.offset().isEmpty())
            return message(player, "此控制器未绑定研究所，无法改变设施任务。", false);
        role = normalizeRole(role);
        if (!normalizeRole(control.role()).equals(role)) return InteractionResult.FAIL;
        var origin = pos.subtract(control.offset().get());
        if (OperationsData.get(level).find(origin) == null) registerBound(level, pos, control.offset().get());
        var found = binding(level, pos);
        if (found.isEmpty()) return message(player, "此控制器未绑定研究所，无法改变设施任务。", false);
        var bound = found.get(); var state = bound.facility; var previous = snapshot(state);
        boolean validControl = false;
        for (var target : bound.layout.controls) if (target.offset.equals(bound.relative) && target.role.equals(role)) validControl = true;
        if (!validControl) return message(player, "控制器位置与研究所清单不符。", false);
        if (role.equals("generator")) {
            if (state.repaired) return message(player, "发电机已经修复；请在配电盘分配两区供电。", true);
            if (!transact(player, Map.of(item("lab_fuse"), 1, Items.COPPER_INGOT, 4, Items.REDSTONE, 4), List.of()))
                return message(player, "修复需要：保险丝×1、铜锭×4、红石×4。未消耗物品。", false);
            state.repaired = true; changed(level, bound, previous, "generator_repaired"); synchronizeInventory(player);
            return message(player, "发电机修复完成；请到配电盘开启服务区与收容区。", true);
        }
        if (role.equals("distribution")) {
            if (!state.repaired) return message(player, "先修复 B1 发电机，配电盘目前无电。", false);
            if (player.isShiftKeyDown() && !player.getMainHandItem().isEmpty())
                return message(player, "请主手空手潜行右键切换收容区供电。电区状态未改变。", false);
            if (player.isShiftKeyDown()) state.containmentPowered = !state.containmentPowered;
            else state.servicePowered = !state.servicePowered;
            changed(level, bound, previous, player.isShiftKeyDown() ? "containment_switched" : "service_switched");
            return message(player, "服务区：" + (state.servicePowered ? "供电" : "断电") + "；收容区：" + (state.containmentPowered ? "供电" : "断电") + "。普通右键切换服务区，潜行右键切换收容区。", true);
        }
        if (role.equals("terminal")) {
            if (!powered(state, "service")) return message(player, "研究终端需要服务区供电；门禁卡未领取。", false);
            String progress = "研究所：" + (state.repaired ? "电源已修复" : "需修复电源") + "，分析" + (state.analyzed ? "完成" : "未完成") + "，分离" + (state.separated ? "完成" : "未完成") + "，档案" + (state.archiveClaimed ? "已领取" : "待领取");
            if (!state.repaired || state.receivedKey(player.getUUID())) return message(player, progress, true);
            if (!transact(player, Map.of(), List.of(new ItemStack(item("research_keycard")))))
                return message(player, progress + "。背包空间不足，门禁卡未领取。", false);
            state.keyRecipients.add(player.getUUID()); bound.data.setDirty(); synchronizeInventory(player);
            fire(level, previous, snapshot(state), "keycard_issued");
            return message(player, progress + "。已领取门禁卡；手持门禁卡并恢复收容区供电后可从外侧开门。", true);
        }
        if (role.equals("archive")) {
            if (state.archiveClaimed) return message(player, "这座研究所的核心档案奖励已经领取，不会重复刷新。", false);
            if (!powered(state, "containment"))
                return message(player, "档案终端需要收容区供电；未消耗研究记录或分离试剂。", false);
            if (!state.analyzed || !state.separated) return message(player, "请先在本设施完成样本分析与分离。", false);
            if (!transact(player, Map.of(item("research_record"), 1, item("separated_reagent"), 1),
                    List.of(new ItemStack(item("core_archive")), new ItemStack(item("void_crystal"), 2))))
                return message(player, "提交需要研究记录×1、分离试剂×1，并为核心档案与虚空晶留出背包空间；没有消耗或掉落物品。", false);
            state.archiveClaimed = true; changed(level, bound, previous, "archive_claimed"); synchronizeInventory(player);
            return message(player, "已取得核心档案与虚空晶×2；本设施奖励只可领取一次。", true);
        }
        return InteractionResult.PASS;
    }
    public static boolean mayUseDoor(ServerLevel level, BlockPos pos, BlockState door, Player player) {
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                && level.getBlockEntity(pos) instanceof LabFixtureEntity fixture && fixture.boundOrigin().isPresent()
                && OperationsData.get(level).find(fixture.boundOrigin().get()) == null) registerFacility(level, fixture.boundOrigin().get());
        var found = binding(level, pos); if (found.isEmpty()) return true; var bound = found.get();
        if (!bound.layout.doors.containsKey(bound.relative) || door.getValue(LabFixtureBlock.OPEN)) return true;
        var facing = door.getValue(LabFixtureBlock.FACING);
        double inside = (player.getX() - pos.getX() - .5) * facing.getStepX() + (player.getZ() - pos.getZ() - .5) * facing.getStepZ();
        if (inside > .05) return true;
        if (!powered(bound.facility, "containment")) { message(player, "门禁断电；室内侧仍可手动应急开门。", false); return false; }
        Item card = item("research_keycard");
        if (!player.getMainHandItem().is(card) && !player.getOffhandItem().is(card)) {
            message(player, "请手持研究所门禁卡；可在研究终端领取，遗失后可合成补卡。", false); return false;
        }
        return true;
    }
    private static void refresh(ServerLevel level, OperationsData data, int budget) {
        var iterator = data.pendingRefresh.entrySet().iterator(); var definitions = layout(level);
        while (iterator.hasNext() && budget > 0) {
            var pending = iterator.next(); var facility = data.facilities.get(pending.getKey());
            if (facility == null) { iterator.remove(); continue; }
            int cursor = pending.getValue(), count = definitions.lights.size() + definitions.controls.size();
            while (cursor < count && budget-- > 0) {
                boolean light = cursor < definitions.lights.size();
                var target = light ? definitions.lights.get(cursor) : definitions.controls.get(cursor - definitions.lights.size()); cursor++;
                var pos = facility.origin.offset(target.offset);
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4); if (chunk == null) continue;
                var state = chunk.getBlockState(pos);
                boolean enabled = powered(facility, target.zone);
                if (light && state.getBlock() instanceof LabDetailBlock block && block.isLamp() && state.hasProperty(LabDetailBlock.POWERED)) {
                    if (state.getValue(LabDetailBlock.POWERED) != enabled) level.setBlock(pos, state.setValue(LabDetailBlock.POWERED, enabled), 18);
                } else if (!light && chunk.getBlockEntity(pos) instanceof OperationsControlEntity control
                        && control.boundOrigin().filter(facility.origin::equals).isPresent()) {
                    control.updatePowered(target.role.equals("generator") ? facility.repaired : enabled);
                }
            }
            if (cursor >= count) {
                if (data.refreshAgain.remove(pending.getKey())) pending.setValue(0); else iterator.remove();
            } else pending.setValue(cursor);
        }
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var data = OperationsData.get(level); if (!data.pendingRefresh.isEmpty()) refresh(level, data, 32);
        }
    }
    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) level.getServer().execute(() -> {
            var data = OperationsData.get(level); var chunk = event.getChunk().getPos();
            for (var facility : data.facilities.values()) {
                int minX = facility.origin.getX() >> 4, minZ = facility.origin.getZ() >> 4;
                if (chunk.x >= minX && chunk.x <= (facility.origin.getX() + CentralInstitute.WIDTH - 1 >> 4)
                        && chunk.z >= minZ && chunk.z <= (facility.origin.getZ() + CentralInstitute.LENGTH - 1 >> 4)) data.refresh(facility.origin);
            }
        });
    }
}
