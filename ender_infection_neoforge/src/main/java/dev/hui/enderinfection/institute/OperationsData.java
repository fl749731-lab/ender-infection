package dev.hui.enderinfection.institute;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Dimension-local facility progress. No worldgen callback may create or mutate it. */
public final class OperationsData extends SavedData {
    public static final String NAME = "ender_institute_operations";
    final Map<Long, Facility> facilities = new LinkedHashMap<>();
    final Map<Long, Integer> pendingRefresh = new LinkedHashMap<>();
    final Set<Long> refreshAgain = new LinkedHashSet<>();
    public static final class Facility {
        public final BlockPos origin;
        boolean repaired, servicePowered, containmentPowered, analyzed, separated, archiveClaimed;
        final Set<UUID> keyRecipients = new LinkedHashSet<>();
        Facility(BlockPos origin) { this.origin = origin.immutable(); }
        public boolean repaired() { return repaired; }
        public boolean servicePowered() { return servicePowered; }
        public boolean containmentPowered() { return containmentPowered; }
        public boolean analyzed() { return analyzed; }
        public boolean separated() { return separated; }
        public boolean archiveClaimed() { return archiveClaimed; }
        public boolean receivedKey(UUID player) { return keyRecipients.contains(player); }
    }
    public static OperationsData get(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Facility progress requires the server thread");
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(OperationsData::new,(tag, registries)->OperationsData.load(tag),null),NAME);
    }
    public Facility find(BlockPos origin) { return facilities.get(origin.asLong()); }
    void refresh(BlockPos origin) {
        if (pendingRefresh.putIfAbsent(origin.asLong(), 0) != null) refreshAgain.add(origin.asLong());
    }
    void restartRefresh(BlockPos origin) { pendingRefresh.put(origin.asLong(), 0); refreshAgain.remove(origin.asLong()); }
    public static OperationsData load(CompoundTag root) {
        var data = new OperationsData();
        for (var value : root.getList("Facilities", 10)) {
            var tag = (CompoundTag)value;
            if (!tag.contains("Origin", 4)) continue;
            var facility = new Facility(BlockPos.of(tag.getLong("Origin")));
            facility.repaired = tag.getBoolean("Repaired");
            facility.servicePowered = facility.repaired && tag.getBoolean("ServicePowered");
            facility.containmentPowered = facility.repaired && tag.getBoolean("ContainmentPowered");
            facility.analyzed = tag.getBoolean("Analyzed"); facility.separated = tag.getBoolean("Separated");
            facility.archiveClaimed = tag.getBoolean("ArchiveClaimed");
            for (var recipient : tag.getList("KeyRecipients", 10)) {
                var player = (CompoundTag)recipient;
                if (player.hasUUID("Player")) facility.keyRecipients.add(player.getUUID("Player"));
            }
            data.facilities.put(facility.origin.asLong(), facility);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root, net.minecraft.core.HolderLookup.Provider registries) { return save(root); }
    public CompoundTag save(CompoundTag root) {
        root.putInt("Version", 1); var list = new ListTag();
        for (var facility : facilities.values()) {
            var tag = new CompoundTag(); tag.putLong("Origin", facility.origin.asLong());
            tag.putBoolean("Repaired", facility.repaired); tag.putBoolean("ServicePowered", facility.servicePowered);
            tag.putBoolean("ContainmentPowered", facility.containmentPowered); tag.putBoolean("Analyzed", facility.analyzed);
            tag.putBoolean("Separated", facility.separated); tag.putBoolean("ArchiveClaimed", facility.archiveClaimed);
            var recipients = new ListTag();
            for (var uuid : facility.keyRecipients) { var player = new CompoundTag(); player.putUUID("Player", uuid); recipients.add(player); }
            tag.put("KeyRecipients", recipients); list.add(tag);
        }
        root.put("Facilities", list); return root;
    }
}
