package com.cybersammy.citiesarise.core.debug;

import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.*;

/** Presence filters over an accepted semantic plan, never over unverified placement anchors. */
public record PlanElementFilter(Set<String> required) {
    public static final Set<String> SUPPORTED = Set.of("settlement", "bridge", "road_step", "access_step",
            "retaining_wall", "earthworks", "props", "modular_building");
    public PlanElementFilter {
        required = Set.copyOf(required);
        if (required.isEmpty() || !SUPPORTED.containsAll(required))
            throw new IllegalArgumentException("Choose one or more elements from " + new TreeSet<>(SUPPORTED));
    }
    public boolean matches(Map<String,Integer> counts) {
        return required.stream().allMatch(element -> counts.getOrDefault(element,0)>0);
    }
    public static Map<String,Integer> counts(SettlementPlan plan, TerrainPreparationPlan preparation) {
        var counts = new TreeMap<String,Integer>();
        counts.put("settlement",1);
        counts.put("bridge",plan.roadGraph().bridges().size());
        counts.put("props",plan.props().size());
        counts.put("modular_building",(int)plan.buildingSlots().stream().filter(b -> b.content().flatMap(c -> c.resolved()).isPresent()).count());
        counts.put("earthworks",preparation.requiresEarthworks()?1:0);
        for (var entry : Map.of("road_step",TerrainPreparationColumnType.ROAD_TRANSITION_STEP,
                "access_step",TerrainPreparationColumnType.BUILDING_ACCESS_STEP,
                "retaining_wall",TerrainPreparationColumnType.RETAINING_WALL).entrySet())
            counts.put(entry.getKey(),(int)preparation.columns().stream().filter(c -> c.type()==entry.getValue()).count());
        return Collections.unmodifiableMap(counts);
    }
}
