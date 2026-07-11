package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RoadElevationPlanner {
    private static final int MAX_CONNECTED_STEP = 1;

    private RoadElevationPlanner() {
    }

    static RoadGraph apply(SuburbPlanningRequest request, RoadGraph roadGraph) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(roadGraph);
        Map<PlanElementId, List<Integer>> nodeElevations = new HashMap<>();
        List<RoadSegment> elevatedSegments = new ArrayList<>();

        for (RoadSegment segment : roadGraph.segments()) {
            RoadNode start = requiredNode(nodesById, segment.startNodeId());
            RoadNode end = requiredNode(nodesById, segment.endNodeId());
            GridBounds bounds = AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width());
            int desiredElevation = TerrainPlatform.elevation(request, bounds);
            int elevation = connectedElevation(segment, desiredElevation, nodeElevations);
            elevatedSegments.add(withElevation(segment, elevation));
            addNodeElevation(nodeElevations, segment.startNodeId(), elevation);
            addNodeElevation(nodeElevations, segment.endNodeId(), elevation);
        }

        return new RoadGraph(roadGraph.nodes(), elevatedSegments);
    }

    static RoadNode requiredNode(Map<PlanElementId, RoadNode> nodesById, PlanElementId id) {
        RoadNode node = nodesById.get(id);
        if (node == null) {
            throw new IllegalStateException("road segment references missing node: " + id.value());
        }
        return node;
    }

    private static Map<PlanElementId, RoadNode> nodesById(RoadGraph graph) {
        Map<PlanElementId, RoadNode> nodes = new HashMap<>();
        for (RoadNode node : graph.nodes()) {
            nodes.put(node.id(), node);
        }
        return nodes;
    }

    private static RoadSegment withElevation(RoadSegment segment, int elevation) {
        return new RoadSegment(
                segment.id(),
                segment.startNodeId(),
                segment.endNodeId(),
                segment.width(),
                segment.tags(),
                TerrainPlatform.withElevation(segment.properties(), elevation)
        );
    }

    private static int connectedElevation(
            RoadSegment segment,
            int desiredElevation,
            Map<PlanElementId, List<Integer>> nodeElevations
    ) {
        List<Integer> connected = new ArrayList<>();
        connected.addAll(nodeElevations.getOrDefault(segment.startNodeId(), List.of()));
        connected.addAll(nodeElevations.getOrDefault(segment.endNodeId(), List.of()));
        if (connected.isEmpty()) {
            return desiredElevation;
        }

        int minimum = Integer.MIN_VALUE;
        int maximum = Integer.MAX_VALUE;
        for (int elevation : connected) {
            minimum = Math.max(minimum, elevation - MAX_CONNECTED_STEP);
            maximum = Math.min(maximum, elevation + MAX_CONNECTED_STEP);
        }
        if (minimum > maximum) {
            throw new IllegalStateException("connected road elevations have no valid transition");
        }
        return clamp(desiredElevation, minimum, maximum);
    }

    private static void addNodeElevation(
            Map<PlanElementId, List<Integer>> nodeElevations,
            PlanElementId nodeId,
            int elevation
    ) {
        nodeElevations.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(elevation);
    }

    private static int clamp(int value, int minimum, int maximum) {
        if (value < minimum) {
            return minimum;
        }
        if (value > maximum) {
            return maximum;
        }
        return value;
    }
}
