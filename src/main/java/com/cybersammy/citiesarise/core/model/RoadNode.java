package com.cybersammy.citiesarise.core.model;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.Objects;
import java.util.Set;

public record RoadNode(
        PlanElementId id,
        GridPoint point,
        Set<PlanTag> tags,
        PlanProperties properties
) implements PlanElement {
    public RoadNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(point, "point");
        tags = PlanCollections.immutableSet(tags, "tags");
        properties = Objects.requireNonNull(properties, "properties");
    }
}
