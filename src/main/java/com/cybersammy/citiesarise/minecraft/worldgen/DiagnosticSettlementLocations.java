package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Saved search results, deliberately separate from observed structure placement. Server-thread only. */
public final class DiagnosticSettlementLocations extends SavedData {
    private final Map<SettlementRegion,Entry> entries=new LinkedHashMap<>();
    public record Entry(SettlementRegion region,int x,int z,String profile) {
        public Entry { Objects.requireNonNull(region);Objects.requireNonNull(profile);
            if(profile.isBlank()) throw new IllegalArgumentException("Missing diagnostic profile"); }
    }
    public static DiagnosticSettlementLocations get(ServerLevel level) {
        if(!level.getServer().isSameThread()) throw new IllegalStateException("Diagnostic locations require server thread");
        return level.getDataStorage().computeIfAbsent(new Factory<>(DiagnosticSettlementLocations::new,
                (tag,lookup)->load(tag)),"cities_arise_diagnostic_locations");
    }
    public void remember(Entry entry) {
        if(!entry.equals(entries.put(entry.region(),entry))) setDirty();
    }
    public Set<SettlementRegion> regions() {return Set.copyOf(entries.keySet());}
    public List<Entry> entries() {return List.copyOf(entries.values());}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        tag.putInt("Version",1);var list=new ListTag();
        for(var e:entries.values()) {
            var row=new CompoundTag();row.putInt("RegionX",e.region().x());row.putInt("RegionZ",e.region().z());
            row.putInt("X",e.x());row.putInt("Z",e.z());row.putString("Profile",e.profile());list.add(row);
        }
        tag.put("Candidates",list);return tag;
    }
    public static DiagnosticSettlementLocations load(CompoundTag tag) {
        if(tag.getInt("Version")!=1) throw new IllegalArgumentException("Unsupported diagnostic locations version");
        var result=new DiagnosticSettlementLocations();
        for(var item:tag.getList("Candidates",Tag.TAG_COMPOUND)) {
            var row=(CompoundTag)item;
            var entry=new Entry(new SettlementRegion(row.getInt("RegionX"),row.getInt("RegionZ")),row.getInt("X"),row.getInt("Z"),row.getString("Profile"));
            result.entries.put(entry.region(),entry);
        }
        return result;
    }
}
