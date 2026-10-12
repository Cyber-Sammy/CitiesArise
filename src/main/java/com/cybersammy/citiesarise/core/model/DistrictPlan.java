package com.cybersammy.citiesarise.core.model;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import java.util.List;
import java.util.Objects;

/** Semantic local development footprint, independent of block assets or architectural style. */
public record DistrictPlan(PlanElementId id, GridBounds bounds, List<PlanElementId> parcels, List<GridBounds> footprint, java.util.Set<PlanTag> tags, PlanProperties properties) {
    public DistrictPlan(PlanElementId id, GridBounds bounds, List<PlanElementId> parcels, List<GridBounds> footprint) {
        this(id,bounds,parcels,footprint,java.util.Set.of(),PlanProperties.empty());
    }
    public DistrictPlan(PlanElementId id, GridBounds bounds, List<PlanElementId> parcels) {
        this(id, bounds, parcels, List.of(bounds));
    }

    public DistrictPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bounds, "bounds");
        tags = java.util.Set.copyOf(tags);
        Objects.requireNonNull(properties);
        parcels = List.copyOf(parcels);
        footprint = List.copyOf(footprint);
        if (footprint.isEmpty() || footprint.stream().anyMatch(part -> !bounds.contains(part)))
            throw new IllegalArgumentException("district footprint must be non-empty and inside bounds");
        if (parcels.isEmpty() || parcels.stream().distinct().count() != parcels.size())
            throw new IllegalArgumentException("district must have unique parcel members");
    }

    public boolean contains(GridBounds area) {
        if (!bounds.contains(area)) return false;
        for (int z=area.minZ(); z<area.maxZExclusive(); z++) for (int x=area.minX(); x<area.maxXExclusive(); x++) {
            var point = new com.cybersammy.citiesarise.core.geometry.GridPoint(x,z);
            if (footprint.stream().noneMatch(part -> part.contains(point))) return false;
        }
        return true;
    }
}
