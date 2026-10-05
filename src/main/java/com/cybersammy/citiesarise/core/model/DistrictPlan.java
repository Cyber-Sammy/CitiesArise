package com.cybersammy.citiesarise.core.model;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import java.util.List;
import java.util.Objects;

/** Semantic local development footprint, independent of block assets or architectural style. */
public record DistrictPlan(PlanElementId id, GridBounds bounds, List<PlanElementId> parcels) {
    public DistrictPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bounds, "bounds");
        parcels = List.copyOf(parcels);
        if (parcels.isEmpty() || parcels.stream().distinct().count() != parcels.size())
            throw new IllegalArgumentException("district must have unique parcel members");
    }
}
