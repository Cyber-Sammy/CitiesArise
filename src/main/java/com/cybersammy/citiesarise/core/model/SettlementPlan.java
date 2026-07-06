package com.cybersammy.citiesarise.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record SettlementPlan(
        PlanElementId id,
        RoadGraph roadGraph,
        List<Parcel> parcels,
        List<BuildingSlot> buildingSlots,
        Set<PlanTag> tags,
        PlanProperties properties
) implements PlanElement {
    public SettlementPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(roadGraph, "roadGraph");
        parcels = PlanCollections.immutableList(parcels, "parcels");
        buildingSlots = PlanCollections.immutableList(buildingSlots, "buildingSlots");
        tags = PlanCollections.immutableSet(tags, "tags");
        properties = Objects.requireNonNull(properties, "properties");
    }
}
