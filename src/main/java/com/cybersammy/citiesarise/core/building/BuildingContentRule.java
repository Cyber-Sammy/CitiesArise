package com.cybersammy.citiesarise.core.building;

import com.cybersammy.citiesarise.core.model.MetadataSelector;
import com.cybersammy.citiesarise.core.model.PlanTag;
import java.util.*;

/** A complete content binding. First matching rule wins; fallback stays inside that binding. */
public record BuildingContentRule(String id, MetadataSelector match, List<BuildingContentSettings.Entry> pool,
        List<String> palettes, BuildingAsset fallback) {
    public BuildingContentRule {
        new PlanTag(id); Objects.requireNonNull(match); Objects.requireNonNull(fallback);
        pool=List.copyOf(pool); palettes=List.copyOf(palettes);
        if (pool.isEmpty() || pool.size()>16 || pool.stream().map(BuildingContentSettings.Entry::asset).distinct().count()!=pool.size())
            throw new IllegalArgumentException("Rule pool requires 1..16 unique assets");
        if (palettes.isEmpty() || palettes.size()>64 || palettes.stream().distinct().count()!=palettes.size())
            throw new IllegalArgumentException("Rule palettes require 1..64 unique ids");
    }
}
