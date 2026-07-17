package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.ElevationTransition;
import com.cybersammy.citiesarise.core.earthwork.ElevationTransitionType;
import com.cybersammy.citiesarise.core.earthwork.ElevationZone;
import com.cybersammy.citiesarise.core.earthwork.ElevationZoneType;
import com.cybersammy.citiesarise.core.earthwork.RegionalElevationPlan;
import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
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
        List<BuildingSlot> elevatedBuildingSlots = elevatedBuildingSlots(request, settlementPlan.buildingSlots());
        SettlementPlan elevatedPlan = new SettlementPlan(
                settlementPlan.id(),
                elevatedRoadGraph,
                settlementPlan.parcels(),
                elevatedBuildingSlots,
                settlementPlan.tags(),
                settlementPlan.properties()
        );
        RegionalElevationPlan elevationPlan = new RegionalElevationPlan(
                elevationZones(elevatedPlan),
                elevationTransitions(elevatedPlan)
        );
        return new RegionalElevationPlanningResult(elevatedPlan, elevationPlan);
    }

    private static List<BuildingSlot> elevatedBuildingSlots(
            SuburbPlanningRequest request,
            List<BuildingSlot> buildingSlots
    ) {
        List<BuildingSlot> elevatedSlots = new ArrayList<>();
        for (BuildingSlot slot : buildingSlots) {
            elevatedSlots.add(new BuildingSlot(
                    slot.id(),
                    slot.parcelId(),
                    slot.bounds(),
                    slot.tags(),
                    TerrainPlatform.withHighestElevation(slot.properties(), request, slot.bounds())
            ));
        }
        return List.copyOf(elevatedSlots);
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

    private static List<ElevationTransition> elevationTransitions(SettlementPlan plan) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(plan.roadGraph());
        List<ElevationTransition> transitions = new ArrayList<>();
        addRoadTransitions(plan.roadGraph(), nodesById, transitions);
        addBuildingAccessTransitions(plan, nodesById, transitions);
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
            SettlementPlan plan,
            Map<PlanElementId, RoadNode> nodesById,
            List<ElevationTransition> transitions
    ) {
        List<RoadSegment> roadSegments = sortedSegments(plan.roadGraph().segments());
        if (roadSegments.isEmpty()) {
            return;
        }
        for (BuildingSlot slot : sortedBuildingSlots(plan.buildingSlots())) {
            GridPoint accessPoint = buildingAccessPoint(slot.bounds());
            RoadSegment road = nearestRoad(accessPoint, roadSegments, nodesById);
            transitions.add(new ElevationTransition(
                    ElevationTransitionType.BUILDING_ACCESS,
                    road.id(),
                    slot.id(),
                    accessPoint,
                    TerrainPlatform.requiredElevation(road.properties()),
                    TerrainPlatform.requiredElevation(slot.properties())
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

    private static RoadSegment nearestRoad(
            GridPoint point,
            List<RoadSegment> segments,
            Map<PlanElementId, RoadNode> nodesById
    ) {
        RoadSegment nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (RoadSegment segment : segments) {
            RoadNode start = RoadElevationPlanner.requiredNode(nodesById, segment.startNodeId());
            RoadNode end = RoadElevationPlanner.requiredNode(nodesById, segment.endNodeId());
            GridBounds bounds = AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width());
            int distance = distanceToBounds(point, bounds);
            if (distance < nearestDistance) {
                nearest = segment;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            throw new IllegalStateException("building access requires at least one road segment");
        }
        return nearest;
    }

    private static int distanceToBounds(GridPoint point, GridBounds bounds) {
        int xDistance = axisDistance(point.x(), bounds.minX(), bounds.maxXExclusive());
        int zDistance = axisDistance(point.z(), bounds.minZ(), bounds.maxZExclusive());
        return Math.addExact(xDistance, zDistance);
    }

    private static int axisDistance(int coordinate, int minimum, int maximumExclusive) {
        if (coordinate < minimum) {
            return minimum - coordinate;
        }
        if (coordinate >= maximumExclusive) {
            return coordinate - (maximumExclusive - 1);
        }
        return 0;
    }

    private static GridPoint buildingAccessPoint(GridBounds bounds) {
        return new GridPoint(bounds.minX() + (bounds.size().width() / 2), bounds.minZ());
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
}
