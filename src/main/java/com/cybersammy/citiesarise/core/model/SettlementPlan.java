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
        PlanProperties properties,
        java.util.Map<String,String> placementMaterials,
        List<com.cybersammy.citiesarise.core.content.ResolvedProp> props,
        java.util.Map<String,com.cybersammy.citiesarise.core.content.SurfaceTemplate> surfaceTemplates
) implements PlanElement {
    public SettlementPlan(PlanElementId id,RoadGraph roadGraph,List<Parcel> parcels,List<BuildingSlot> buildingSlots,
            Set<PlanTag> tags,PlanProperties properties,java.util.Map<String,String> placementMaterials,
            List<com.cybersammy.citiesarise.core.content.ResolvedProp> props) {
        this(id,roadGraph,parcels,buildingSlots,tags,properties,placementMaterials,props,java.util.Map.of());
    }
    public SettlementPlan(PlanElementId id, RoadGraph roadGraph, List<Parcel> parcels, List<BuildingSlot> buildingSlots,
            Set<PlanTag> tags, PlanProperties properties) {
        this(id,roadGraph,parcels,buildingSlots,tags,properties,java.util.Map.of());
    }
    public SettlementPlan(PlanElementId id,RoadGraph roadGraph,List<Parcel> parcels,List<BuildingSlot> buildingSlots,
            Set<PlanTag> tags,PlanProperties properties,java.util.Map<String,String> placementMaterials) {
        this(id,roadGraph,parcels,buildingSlots,tags,properties,placementMaterials,List.of());
    }
    public SettlementPlan {
        surfaceTemplates=java.util.Map.copyOf(surfaceTemplates);
        props=List.copyOf(props);
        placementMaterials=java.util.Map.copyOf(placementMaterials);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(roadGraph, "roadGraph");
        parcels = PlanCollections.immutableList(parcels, "parcels");
        buildingSlots = PlanCollections.immutableList(buildingSlots, "buildingSlots");
        tags = PlanCollections.immutableSet(tags, "tags");
        properties = Objects.requireNonNull(properties, "properties");
    }
}
