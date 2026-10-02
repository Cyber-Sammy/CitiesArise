package com.cybersammy.citiesarise.core.model;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import java.util.Objects;
import java.util.Set;

public record BuildingSlot(
        PlanElementId id,
        PlanElementId parcelId,
        GridBounds bounds,
        Set<PlanTag> tags,
        PlanProperties properties,
        java.util.Optional<com.cybersammy.citiesarise.core.building.BuildingContent> content
) implements PlanElement {
    public BuildingSlot(PlanElementId id, PlanElementId parcelId, GridBounds bounds, Set<PlanTag> tags, PlanProperties properties) {
        this(id,parcelId,bounds,tags,properties,java.util.Optional.empty());
    }
    public BuildingSlot {
        Objects.requireNonNull(content,"content");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(bounds, "bounds");
        tags = PlanCollections.immutableSet(tags, "tags");
        properties = Objects.requireNonNull(properties, "properties");
    }
}
