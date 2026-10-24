package com.cybersammy.citiesarise.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Material-independent city metadata. Resolved content belongs to a separate assembly result. */
public record SettlementPlan(PlanElementId id, RoadGraph roadGraph, List<Parcel> parcels,
        List<BuildingSlot> buildingSlots, Set<PlanTag> tags, PlanProperties properties,
        List<DistrictPlan> districts, List<com.cybersammy.citiesarise.core.road.CityOverpassPlan> overpasses) implements PlanElement {
    public SettlementPlan(PlanElementId id, RoadGraph roadGraph, List<Parcel> parcels,
            List<BuildingSlot> buildingSlots, Set<PlanTag> tags, PlanProperties properties,List<DistrictPlan> districts) {
        this(id,roadGraph,parcels,buildingSlots,tags,properties,districts,List.of());
    }
    public SettlementPlan withOverpasses(List<com.cybersammy.citiesarise.core.road.CityOverpassPlan> value) {
        return new SettlementPlan(id,roadGraph,parcels,buildingSlots,tags,properties,districts,value);
    }
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
        overpasses=overpasses==null?List.of():List.copyOf(overpasses);
        if(overpasses.size()>1)throw new IllegalArgumentException("At most one city overpass per plan");
    }
}
