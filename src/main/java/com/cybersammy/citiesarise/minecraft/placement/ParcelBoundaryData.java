package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.*;

/** Adapter-owned operation annotations, separate from block entity NBT. */
final class ParcelBoundaryData {
    static final Set<String> KEYS=Set.of("cities_arise:boundary","cities_arise:boundary_owner_0",
            "cities_arise:boundary_owner_1","cities_arise:fence_connections");
    private ParcelBoundaryData() { }
    static int connections(String value) {
        try { int bits=Integer.parseInt(value);if(bits>=0 && bits<=15)return bits; }
        catch(NumberFormatException ignored) { }
        throw new IllegalArgumentException("Invalid fence connections");
    }
    static void validate(Map<String,String> data) {
        if(data.keySet().stream().noneMatch(KEYS::contains)) return;
        String id=data.get("cities_arise:boundary");
        if(id==null || id.isBlank() || id.length()>2048) throw new IllegalArgumentException("Invalid boundary identity");
        for(String key:List.of("cities_arise:boundary_owner_0","cities_arise:boundary_owner_1"))
            if(data.containsKey(key)) new PlanElementId(data.get(key));
        if(data.containsKey("cities_arise:fence_connections")) connections(data.get("cities_arise:fence_connections"));
    }
}
