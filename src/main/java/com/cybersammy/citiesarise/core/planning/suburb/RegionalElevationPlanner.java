package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.BuildingAccessResolver;
import com.cybersammy.citiesarise.core.earthwork.ElevationTransition;
import com.cybersammy.citiesarise.core.earthwork.ElevationTransitionType;
import com.cybersammy.citiesarise.core.earthwork.ElevationZone;
import com.cybersammy.citiesarise.core.earthwork.ElevationZoneType;
import com.cybersammy.citiesarise.core.earthwork.RegionalElevationPlan;
import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RegionalElevationPlanner {
    private RegionalElevationPlanner() {
    }

    static RegionalElevationPlanningResult plan(
            SuburbPlanningRequest request,
            SettlementPlan settlementPlan
    ) {
        RoadGraph elevatedRoadGraph = RoadElevationPlanner.apply(request, settlementPlan.roadGraph());
        ParcelElevationResult parcelElevations = elevateParcelsAndBuildings(request, settlementPlan);
        SettlementPlan elevatedPlan = new SettlementPlan(
                settlementPlan.id(),
                elevatedRoadGraph,
                parcelElevations.parcels(),
                parcelElevations.buildingSlots(),
                settlementPlan.tags(),
                settlementPlan.properties()
        );
        List<ElevationZone> zones = elevationZones(elevatedPlan);
        RegionalElevationPlan elevationPlan = new RegionalElevationPlan(zones, elevationTransitions(elevatedPlan, zones));
        return new RegionalElevationPlanningResult(elevatedPlan, elevationPlan);
    }

    private static ParcelElevationResult elevateParcelsAndBuildings(
            SuburbPlanningRequest request,
            SettlementPlan plan
    ) {
        Map<PlanElementId, Integer> elevationByParcel = parcelElevations(request, plan);
        List<Parcel> elevatedParcels = new ArrayList<>();
        for (Parcel parcel : plan.parcels()) {
            elevatedParcels.add(new Parcel(
                    parcel.id(),
                    parcel.bounds(),
                    parcel.tags(),
                    TerrainPlatform.withElevation(
                            parcel.properties(),
                            requiredParcelElevation(elevationByParcel, parcel.id())
                    )
            ));
        }

        List<BuildingSlot> elevatedBuildings = new ArrayList<>();
        for (BuildingSlot slot : plan.buildingSlots()) {
            elevatedBuildings.add(new BuildingSlot(
                    slot.id(),
                    slot.parcelId(),
                    slot.bounds(),
                    slot.tags(),
                    TerrainPlatform.withElevation(
                            slot.properties(),
                            requiredParcelElevation(elevationByParcel, slot.parcelId())
                    )
            ));
        }
        return new ParcelElevationResult(
                List.copyOf(elevatedParcels),
                List.copyOf(elevatedBuildings)
        );
    }

    private static Map<PlanElementId, Integer> parcelElevations(
            SuburbPlanningRequest request,
            SettlementPlan plan
    ) {
        Map<PlanElementId, Integer> elevations = new HashMap<>();
        for (BuildingSlot slot : plan.buildingSlots()) {
            int elevation = TerrainPlatform.highestElevation(request, slot.bounds());
            elevations.merge(slot.parcelId(), elevation, Math::max);
        }
        for (Parcel parcel : plan.parcels()) {
            elevations.computeIfAbsent(
                    parcel.id(),
                    ignored -> TerrainPlatform.highestElevation(request, parcel.bounds())
            );
        }
        return Map.copyOf(elevations);
    }

    private static int requiredParcelElevation(
            Map<PlanElementId, Integer> elevations,
            PlanElementId parcelId
    ) {
        Integer elevation = elevations.get(parcelId);
        if (elevation == null) {
            throw new IllegalStateException("parcel elevation is missing: " + parcelId.value());
        }
        return elevation;
    }

    private static List<ElevationZone> elevationZones(SettlementPlan plan) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(plan.roadGraph());
        List<ElevationZone> zones = new ArrayList<>();
        for (RoadSegment segment : sortedSegments(plan.roadGraph().segments())) {
            RoadNode start = RoadElevationPlanner.requiredNode(nodesById, segment.startNodeId());
            RoadNode end = RoadElevationPlanner.requiredNode(nodesById, segment.endNodeId());
            zones.add(new ElevationZone(
                    segment.id(),
                    ElevationZoneType.ROAD_SEGMENT,
                    AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width()),
                    TerrainPlatform.requiredElevation(segment.properties())
            ));
        }
        for (Parcel parcel : sortedParcels(plan.parcels())) {
            zones.add(new ElevationZone(
                    parcel.id(),
                    ElevationZoneType.PARCEL_PAD,
                    parcel.bounds(),
                    TerrainPlatform.requiredElevation(parcel.properties())
            ));
        }
        for (BuildingSlot slot : sortedBuildingSlots(plan.buildingSlots())) {
            zones.add(new ElevationZone(
                    slot.id(),
                    ElevationZoneType.BUILDING_PAD,
                    slot.bounds(),
                    TerrainPlatform.requiredElevation(slot.properties())
            ));
        }
        return List.copyOf(zones);
    }

    private static List<ElevationTransition> elevationTransitions(
            SettlementPlan plan,
            List<ElevationZone> zones
    ) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(plan.roadGraph());
        List<ElevationTransition> transitions = new ArrayList<>();
        addRoadTransitions(plan.roadGraph(), nodesById, transitions);
        addBuildingAccessTransitions(zones, transitions);
        return List.copyOf(transitions);
    }

    private static void addRoadTransitions(
            RoadGraph graph,
            Map<PlanElementId, RoadNode> nodesById,
            List<ElevationTransition> transitions
    ) {
        Map<PlanElementId, List<RoadSegment>> segmentsByNode = segmentsByNode(graph);
        List<PlanElementId> nodeIds = segmentsByNode.keySet().stream()
                .sorted(Comparator.comparing(PlanElementId::value))
                .toList();
        for (PlanElementId nodeId : nodeIds) {
            List<RoadSegment> segments = sortedSegments(segmentsByNode.get(nodeId));
            for (int first = 0; first < segments.size(); first++) {
                for (int second = first + 1; second < segments.size(); second++) {
                    RoadSegment source = segments.get(first);
                    RoadSegment target = segments.get(second);
                    transitions.add(transition(
                            ElevationTransitionType.ROAD_CONNECTION,
                            source,
                            target,
                            RoadElevationPlanner.requiredNode(nodesById, nodeId).point()
                    ));
                }
            }
        }
    }

    private static void addBuildingAccessTransitions(
            List<ElevationZone> zones,
            List<ElevationTransition> transitions
    ) {
        List<ElevationZone> buildingZones = zones.stream()
                .filter(zone -> zone.type() == ElevationZoneType.BUILDING_PAD)
                .sorted(Comparator.comparing(zone -> zone.sourceElementId().value()))
                .toList();
        for (ElevationZone buildingZone : buildingZones) {
            BuildingAccessResolver.BuildingAccess access = BuildingAccessResolver.resolve(zones, buildingZone);
            transitions.add(new ElevationTransition(
                    ElevationTransitionType.BUILDING_ACCESS,
                    access.roadZone().sourceElementId(),
                    buildingZone.sourceElementId(),
                    access.anchor(),
                    access.roadZone().targetElevation(),
                    buildingZone.targetElevation()
            ));
        }
    }

    private static ElevationTransition transition(
            ElevationTransitionType type,
            RoadSegment source,
            RoadSegment target,
            GridPoint anchor
    ) {
        return new ElevationTransition(
                type,
                source.id(),
                target.id(),
                anchor,
                TerrainPlatform.requiredElevation(source.properties()),
                TerrainPlatform.requiredElevation(target.properties())
        );
    }

    private static Map<PlanElementId, RoadNode> nodesById(RoadGraph graph) {
        Map<PlanElementId, RoadNode> nodes = new HashMap<>();
        for (RoadNode node : graph.nodes()) {
            nodes.put(node.id(), node);
        }
        return Map.copyOf(nodes);
    }

    private static Map<PlanElementId, List<RoadSegment>> segmentsByNode(RoadGraph graph) {
        Map<PlanElementId, List<RoadSegment>> segmentsByNode = new HashMap<>();
        for (RoadSegment segment : graph.segments()) {
            segmentsByNode.computeIfAbsent(segment.startNodeId(), ignored -> new ArrayList<>()).add(segment);
            segmentsByNode.computeIfAbsent(segment.endNodeId(), ignored -> new ArrayList<>()).add(segment);
        }
        return segmentsByNode;
    }

    private static List<RoadSegment> sortedSegments(List<RoadSegment> segments) {
        return segments.stream()
                .sorted(Comparator.comparing(segment -> segment.id().value()))
                .toList();
    }

    private static List<BuildingSlot> sortedBuildingSlots(List<BuildingSlot> slots) {
        return slots.stream()
                .sorted(Comparator.comparing(slot -> slot.id().value()))
                .toList();
    }

    private static List<Parcel> sortedParcels(List<Parcel> parcels) {
        return parcels.stream()
                .sorted(Comparator.comparing(parcel -> parcel.id().value()))
                .toList();
    }

    private record ParcelElevationResult(
            List<Parcel> parcels,
            List<BuildingSlot> buildingSlots
    ) {
    }
}
