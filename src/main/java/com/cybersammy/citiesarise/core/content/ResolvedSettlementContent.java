package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.building.BuildingContent;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Immutable provider output keyed by semantic element identity, separate from the city map. */
public record ResolvedSettlementContent(Map<PlanElementId, BuildingContent> buildings,
        Map<String, String> placementMaterials, List<ResolvedProp> props,
        Map<String, SurfaceTemplate> surfaceTemplates, Map<PlanElementId,com.cybersammy.citiesarise.core.building.BuildingSelection> selections,
        Map<PlanElementId,ResolvedParcelContent> parcels, com.cybersammy.citiesarise.core.parcel.ParcelBoundaryPlan boundaries) {
    public ResolvedSettlementContent(Map<PlanElementId,BuildingContent> buildings,Map<String,String> materials,
            List<ResolvedProp> props,Map<String,SurfaceTemplate> templates,
            Map<PlanElementId,com.cybersammy.citiesarise.core.building.BuildingSelection> selections,
            Map<PlanElementId,ResolvedParcelContent> parcels) {
        this(buildings,materials,props,templates,selections,parcels,com.cybersammy.citiesarise.core.parcel.ParcelBoundaryPlan.EMPTY);
    }
    public ResolvedSettlementContent(Map<PlanElementId,BuildingContent> buildings,Map<String,String> materials,
            List<ResolvedProp> props,Map<String,SurfaceTemplate> templates,
            Map<PlanElementId,com.cybersammy.citiesarise.core.building.BuildingSelection> selections) {
        this(buildings,materials,props,templates,selections,Map.of());
    }
    public ResolvedSettlementContent(Map<PlanElementId,BuildingContent> buildings,Map<String,String> materials,
            List<ResolvedProp> props,Map<String,SurfaceTemplate> templates) {
        this(buildings,materials,props,templates,Map.of());
    }
    public static final ResolvedSettlementContent EMPTY = new ResolvedSettlementContent(Map.of(), Map.of(), List.of(), Map.of());
    public ResolvedSettlementContent {
        java.util.Objects.requireNonNull(boundaries);
        parcels = Map.copyOf(parcels);
        selections = Map.copyOf(selections);
        buildings = Map.copyOf(buildings);
        placementMaterials = Map.copyOf(placementMaterials);
        props = List.copyOf(props);
        surfaceTemplates = Map.copyOf(surfaceTemplates);
    }
    public void validateAgainst(SettlementPlan plan) {
        var ids = new HashSet<PlanElementId>();
        ids.add(plan.id());
        plan.parcels().forEach(p -> ids.add(p.id()));
        plan.roadGraph().segments().forEach(r -> ids.add(r.id()));
        plan.roadGraph().bridges().forEach(b -> ids.add(b.id()));
        var parcelIds=plan.parcels().stream().map(Parcel::id).collect(java.util.stream.Collectors.toSet());
        if(!parcelIds.containsAll(parcels.keySet())) throw new IllegalArgumentException("Unknown parcel content source");
        for(var parcel:plan.parcels()) {
            var local=parcels.get(parcel.id());if(local==null) continue;
            if(local.outdoor().areas().stream().anyMatch(a -> !parcel.bounds().contains(a.bounds().origin())
                    || !parcel.bounds().contains(new com.cybersammy.citiesarise.core.geometry.GridPoint(a.bounds().maxXExclusive()-1,a.bounds().maxZExclusive()-1)))
                    || local.outdoor().free().stream().anyMatch(p -> !parcel.bounds().contains(p))
                    || local.outdoor().paths().stream().anyMatch(p -> !parcel.bounds().contains(p.point())))
                throw new IllegalArgumentException("Outdoor layout exceeds parcel bounds");
        }
        for(var edge:boundaries.edges()) if(!parcelIds.containsAll(edge.owners())) throw new IllegalArgumentException("Unknown fence owner");
        for(var cell:boundaries.fences()) if(!parcelIds.contains(cell.source())) throw new IllegalArgumentException("Unknown fence source");
        for(var cell:boundaries.alleys()) if(!plan.id().equals(cell.source())) throw new IllegalArgumentException("Unknown alley source");
        var slots = new HashMap<PlanElementId, BuildingSlot>();
        plan.buildingSlots().forEach(slot -> { slots.put(slot.id(), slot); ids.add(slot.id()); });
        if(!slots.keySet().containsAll(selections.keySet())) throw new IllegalArgumentException("Unknown selection slot");
        buildings.forEach((id, content) -> {
            var slot = slots.get(id);
            if (slot == null || !content.asset().fits(slot.bounds().size()))
                throw new IllegalArgumentException("Building content does not fit semantic slot: " + id.value());
            content.resolved().ifPresent(composition -> {
                if (composition.cells().stream().anyMatch(c -> c.position().x()<0 || c.position().z()<0
                        || c.position().x()>=slot.bounds().size().width() || c.position().z()>=slot.bounds().size().depth()))
                    throw new IllegalArgumentException("Resolved building exceeds semantic slot: " + id.value());
            });
        });
        for (var prop : props) if (!ids.contains(prop.source()))
            throw new IllegalArgumentException("Unknown prop source: " + prop.source().value());
        var deck = surfaceTemplates.get("BRIDGE_DECK");
        if (deck != null && plan.roadGraph().bridges().stream().anyMatch(b -> deck.size().y() > b.deckDepth()))
            throw new IllegalArgumentException("Bridge deck template exceeds reserved deckDepth");
    }
}
