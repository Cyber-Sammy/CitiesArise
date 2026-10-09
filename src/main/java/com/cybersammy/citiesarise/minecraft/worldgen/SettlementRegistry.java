package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.registry.SettlementIndex;
import com.cybersammy.citiesarise.core.registry.SettlementIndex.Metadata;
import com.cybersammy.citiesarise.core.registry.SettlementIndex.Entry;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension discovery index. Geometry remains in structure snapshots. Server-thread only. */
public final class SettlementRegistry extends SavedData {
    private static final int VERSION = 1;
    private final SettlementIndex index = new SettlementIndex();

    public static SettlementRegistry get(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Registry requires server thread");
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(SettlementRegistry::new, (tag, lookup) -> load(tag)), "cities_arise_settlements");
    }

    public void observe(Metadata metadata) {
        if (index.observe(metadata)) setDirty();
    }

    public void placed(Metadata metadata, long chunk, long nanos) {
        if (index.placed(metadata, chunk, nanos)) setDirty();
    }

    public Optional<Entry> nearest(int x, int z) { return index.nearest(x, z); }
    public java.util.Collection<Entry> entries() { return index.entries(); }
    public int size() { return index.size(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("Version", VERSION);
        ListTag list = new ListTag();
        index.entries().stream().sorted(Comparator.comparing(e -> e.metadata().id())).forEach(entry -> {
            CompoundTag row = writeMetadata(entry.metadata());
            row.putLongArray("Placed", entry.placedChunks().stream().sorted().mapToLong(Long::longValue).toArray());
            row.putLong("PlacementNanos", entry.placementNanos());
            list.add(row);
        });
        tag.put("Settlements", list);
        return tag;
    }

    public static SettlementRegistry load(CompoundTag tag) {
        if (tag.getInt("Version") != VERSION) throw new IllegalArgumentException("Unsupported settlement registry version");
        SettlementRegistry registry = new SettlementRegistry();
        for (Tag item : tag.getList("Settlements", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag)item;
            Metadata metadata = readMetadata(row);
            Entry entry = new Entry(metadata, longSet(row.getLongArray("Placed")), row.getLong("PlacementNanos"));
            registry.index.restore(entry);
        }
        return registry;
    }

    static CompoundTag writeMetadata(Metadata metadata) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MetadataVersion", VERSION);
        tag.putString("Dimension", metadata.dimension()); tag.putString("Profile", metadata.profile());
        tag.putString("PlanId", metadata.planId());
        tag.putIntArray("Bounds", new int[]{metadata.minX(), metadata.minZ(), metadata.maxX(), metadata.maxZ()});
        tag.putIntArray("Center", new int[]{metadata.centerX(), metadata.centerZ()});
        tag.putLongArray("Required", metadata.placementChunks().stream().sorted().mapToLong(Long::longValue).toArray());
        return tag;
    }

    static Metadata readMetadata(CompoundTag tag) {
        if (tag.getInt("MetadataVersion") != VERSION) throw new IllegalArgumentException("Unsupported settlement metadata version");
        int[] bounds = tag.getIntArray("Bounds"), center = tag.getIntArray("Center");
        if (bounds.length != 4 || center.length != 2) throw new IllegalArgumentException("Invalid settlement bounds");
        return new Metadata(tag.getString("Dimension"), tag.getString("Profile"), tag.getString("PlanId"),
                center[0], center[1], bounds[0], bounds[1], bounds[2], bounds[3], longSet(tag.getLongArray("Required")));
    }

    private static Set<Long> longSet(long[] values) {
        Set<Long> set = new HashSet<>();
        for (long value : values) set.add(value);
        return set;
    }
}
