package com.cybersammy.citiesarise.core.model;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import java.util.Objects;
import java.util.Set;

public record Parcel(
        PlanElementId id,
        GridBounds bounds,
        Set<PlanTag> tags,
        PlanProperties properties
) implements PlanElement {
    public Parcel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bounds, "bounds");
        tags = PlanCollections.immutableSet(tags, "tags");
        properties = Objects.requireNonNull(properties, "properties");
    }
}
