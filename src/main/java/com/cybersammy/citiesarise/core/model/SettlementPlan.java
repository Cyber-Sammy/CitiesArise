package com.cybersammy.citiesarise.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Material-independent city metadata. Resolved content belongs to a separate assembly result. */
public record SettlementPlan(PlanElementId id, RoadGraph roadGraph, List<Parcel> parcels,
        List<BuildingSlot> buildingSlots, Set<PlanTag> tags, PlanProperties properties,
        List<DistrictPlan> districts) implements PlanElement {
    public SettlementPlan(PlanElementId id, RoadGraph roadGraph, List<Parcel> parcels,
            List<BuildingSlot> buildingSlots, Set<PlanTag> tags, PlanProperties properties) {
        this(id, roadGraph, parcels, buildingSlots, tags, properties, List.of());
    }
    public SettlementPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(roadGraph, "roadGraph");
        parcels = PlanCollections.immutableList(parcels, "parcels");
        buildingSlots = PlanCollections.immutableList(buildingSlots, "buildingSlots");
        tags = PlanCollections.immutableSet(tags, "tags");
        properties = Objects.requireNonNull(properties, "properties");
        districts = List.copyOf(districts);
    }
}
