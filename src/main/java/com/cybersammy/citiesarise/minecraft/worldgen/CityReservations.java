package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Authoritative, per-dimension exclusion zones. Failed/cancelled searches never reserve. */
public final class CityReservations extends SavedData {
    private final List<CityExclusionZone> zones = new ArrayList<>();
    public static CityReservations get(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("City reservations require server thread");
        return level.getDataStorage().computeIfAbsent(new Factory<>(CityReservations::new,
                (tag, lookup) -> load(tag)), "cities_arise_city_reservations");
    }
    public List<CityExclusionZone> zones() { return List.copyOf(zones); }
    public boolean reserve(int x, int z, int radius) {
        if (zones.stream().anyMatch(zone -> zone.excludes(x,z,radius))) return false;
        zones.add(new CityExclusionZone(x,z,radius)); setDirty(); return true;
    }
    static List<CityExclusionZone> exclusions(ServerLevel level, int radius) {
        var result = new ArrayList<>(get(level).zones());
        // Existing cities/pending owners predate on-demand mode and must also block new sites.
        for (var entry : SettlementRegistry.get(level).entries())
            result.add(new CityExclusionZone(entry.metadata().centerX(),entry.metadata().centerZ(),radius));
        result.addAll(PendingStructuralPlacement.get(level).cityCenters(radius));
        return List.copyOf(result);
    }
    public static CityReservations load(CompoundTag tag) {
        if (tag.getInt("Version") != 1) throw new IllegalArgumentException("Unsupported city reservations version");
        var result = new CityReservations();
        for (var item : tag.getList("Zones", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag)item;
            result.zones.add(new CityExclusionZone(row.getInt("X"),row.getInt("Z"),row.getInt("Radius")));
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("Version",1); var list = new ListTag();
        for (var zone : zones) {
            var row = new CompoundTag(); row.putInt("X",zone.x()); row.putInt("Z",zone.z());
            row.putInt("Radius",zone.radius()); list.add(row);
        }
        tag.put("Zones",list); return tag;
    }
}
