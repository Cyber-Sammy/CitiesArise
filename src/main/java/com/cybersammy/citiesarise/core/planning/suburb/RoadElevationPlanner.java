package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

final class RoadElevationPlanner {
    private RoadElevationPlanner() {
    }

    static RoadGraph apply(SuburbPlanningRequest request, RoadGraph roadGraph) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(roadGraph);
        Map<PlanElementId, Integer> desiredElevations = desiredElevations(request, roadGraph, nodesById);
        Map<PlanElementId, Integer> componentElevations = componentElevations(roadGraph, desiredElevations);
        List<RoadSegment> elevatedSegments = new ArrayList<>();

        for (RoadSegment segment : roadGraph.segments()) {
            elevatedSegments.add(withElevation(segment, componentElevations.get(segment.id())));
        }

        return new RoadGraph(roadGraph.nodes(), elevatedSegments);
    }

    private static Map<PlanElementId, Integer> desiredElevations(
            SuburbPlanningRequest request,
            RoadGraph graph,
            Map<PlanElementId, RoadNode> nodesById
    ) {
        Map<PlanElementId, Integer> elevations = new HashMap<>();
        for (RoadSegment segment : graph.segments()) {
            RoadNode start = requiredNode(nodesById, segment.startNodeId());
            RoadNode end = requiredNode(nodesById, segment.endNodeId());
            GridBounds bounds = AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width());
            elevations.put(segment.id(), TerrainPlatform.medianElevation(request, bounds));
        }
        return Map.copyOf(elevations);
    }

    private static Map<PlanElementId, Integer> componentElevations(
            RoadGraph graph,
            Map<PlanElementId, Integer> desiredElevations
    ) {
        Map<PlanElementId, RoadSegment> segmentsById = segmentsById(graph);
        Map<PlanElementId, List<PlanElementId>> segmentsByNode = segmentsByNode(graph);
        Map<PlanElementId, Integer> elevations = new HashMap<>();
        Set<PlanElementId> visited = new HashSet<>();
        List<PlanElementId> orderedIds = segmentsById.keySet().stream()
                .sorted(Comparator.comparing(PlanElementId::value))
                .toList();

        for (PlanElementId segmentId : orderedIds) {
            if (visited.contains(segmentId)) {
                continue;
            }
            List<PlanElementId> component = connectedComponent(
                    segmentId,
                    segmentsById,
                    segmentsByNode,
                    visited
            );
            elevations.putAll(boundedComponentElevations(
                    component,
                    segmentsById,
                    segmentsByNode,
                    desiredElevations
            ));
        }
        return Map.copyOf(elevations);
    }

    private static List<PlanElementId> connectedComponent(
            PlanElementId startId,
            Map<PlanElementId, RoadSegment> segmentsById,
            Map<PlanElementId, List<PlanElementId>> segmentsByNode,
            Set<PlanElementId> visited
    ) {
        List<PlanElementId> component = new ArrayList<>();
        Queue<PlanElementId> pending = new ArrayDeque<>();
        pending.add(startId);
        visited.add(startId);

        while (!pending.isEmpty()) {
            PlanElementId segmentId = pending.remove();
            component.add(segmentId);
            RoadSegment segment = segmentsById.get(segmentId);
            enqueueNeighbors(segment.startNodeId(), segmentsByNode, visited, pending);
            enqueueNeighbors(segment.endNodeId(), segmentsByNode, visited, pending);
        }
        return List.copyOf(component);
    }

    private static void enqueueNeighbors(
            PlanElementId nodeId,
            Map<PlanElementId, List<PlanElementId>> segmentsByNode,
            Set<PlanElementId> visited,
            Queue<PlanElementId> pending
    ) {
        for (PlanElementId neighborId : segmentsByNode.getOrDefault(nodeId, List.of())) {
            if (visited.add(neighborId)) {
                pending.add(neighborId);
            }
        }
    }

    private static Map<PlanElementId, Integer> boundedComponentElevations(
            List<PlanElementId> component,
            Map<PlanElementId, RoadSegment> segmentsById,
            Map<PlanElementId, List<PlanElementId>> segmentsByNode,
            Map<PlanElementId, Integer> desiredElevations
    ) {
        Map<PlanElementId, Integer> elevations = new HashMap<>();
        for (PlanElementId segmentId : component) {
            Map<PlanElementId, Integer> distances = segmentDistances(
                    segmentId,
                    segmentsById,
                    segmentsByNode
            );
            elevations.put(segmentId, boundedElevation(component, desiredElevations, distances));
        }
        return Map.copyOf(elevations);
    }

    private static int boundedElevation(
            List<PlanElementId> component,
            Map<PlanElementId, Integer> desiredElevations,
            Map<PlanElementId, Integer> distances
    ) {
        int lowerEnvelope = Integer.MIN_VALUE;
        int upperEnvelope = Integer.MAX_VALUE;
        for (PlanElementId candidateId : component) {
            int desired = desiredElevations.get(candidateId);
            int distance = distances.get(candidateId);
            lowerEnvelope = Math.max(lowerEnvelope, desired - distance);
            upperEnvelope = Math.min(upperEnvelope, desired + distance);
        }
        long envelopeSum = (long) lowerEnvelope + upperEnvelope;
        return Math.toIntExact(Math.floorDiv(envelopeSum, 2L));
    }

    private static Map<PlanElementId, Integer> segmentDistances(
            PlanElementId startId,
            Map<PlanElementId, RoadSegment> segmentsById,
            Map<PlanElementId, List<PlanElementId>> segmentsByNode
    ) {
        Map<PlanElementId, Integer> distances = new HashMap<>();
        Queue<PlanElementId> pending = new ArrayDeque<>();
        distances.put(startId, 0);
        pending.add(startId);

        while (!pending.isEmpty()) {
            PlanElementId segmentId = pending.remove();
            RoadSegment segment = segmentsById.get(segmentId);
            int neighborDistance = distances.get(segmentId) + 1;
            enqueueDistances(segment.startNodeId(), neighborDistance, segmentsByNode, distances, pending);
            enqueueDistances(segment.endNodeId(), neighborDistance, segmentsByNode, distances, pending);
        }
        return Map.copyOf(distances);
    }

    private static void enqueueDistances(
            PlanElementId nodeId,
            int distance,
            Map<PlanElementId, List<PlanElementId>> segmentsByNode,
            Map<PlanElementId, Integer> distances,
            Queue<PlanElementId> pending
    ) {
        for (PlanElementId neighborId : segmentsByNode.getOrDefault(nodeId, List.of())) {
            if (!distances.containsKey(neighborId)) {
                distances.put(neighborId, distance);
                pending.add(neighborId);
            }
        }
    }

    private static Map<PlanElementId, RoadSegment> segmentsById(RoadGraph graph) {
        Map<PlanElementId, RoadSegment> segments = new HashMap<>();
        for (RoadSegment segment : graph.segments()) {
            segments.put(segment.id(), segment);
        }
        return Map.copyOf(segments);
    }

    private static Map<PlanElementId, List<PlanElementId>> segmentsByNode(RoadGraph graph) {
        Map<PlanElementId, List<PlanElementId>> segments = new HashMap<>();
        for (RoadSegment segment : graph.segments()) {
            addNodeSegment(segments, segment.startNodeId(), segment.id());
            addNodeSegment(segments, segment.endNodeId(), segment.id());
        }
        return segments;
    }

    private static void addNodeSegment(
            Map<PlanElementId, List<PlanElementId>> segmentsByNode,
            PlanElementId nodeId,
            PlanElementId segmentId
    ) {
        segmentsByNode.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(segmentId);
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

}
