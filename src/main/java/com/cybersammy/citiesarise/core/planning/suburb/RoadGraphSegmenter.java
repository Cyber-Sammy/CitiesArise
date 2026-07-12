package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RoadGraphSegmenter {
    private RoadGraphSegmenter() {
    }

    static RoadGraph splitLongSegments(RoadGraph graph, int maxSegmentLength) {
        if (maxSegmentLength <= 0) {
            throw new IllegalArgumentException("maxSegmentLength must be positive");
        }

        Map<PlanElementId, RoadNode> nodesById = nodesById(graph);
        List<RoadNode> nodes = new ArrayList<>(graph.nodes());
        List<RoadSegment> segments = new ArrayList<>();
        for (RoadSegment segment : graph.segments()) {
            splitSegment(segment, nodesById, maxSegmentLength, nodes, segments);
        }
        return new RoadGraph(nodes, segments);
    }

    private static void splitSegment(
            RoadSegment segment,
            Map<PlanElementId, RoadNode> nodesById,
            int maxSegmentLength,
            List<RoadNode> nodes,
            List<RoadSegment> segments
    ) {
        RoadNode start = requiredNode(nodesById, segment.startNodeId());
        RoadNode end = requiredNode(nodesById, segment.endNodeId());
        Direction direction = Direction.between(start.point(), end.point());
        int length = direction.length(start.point(), end.point());
        if (length <= maxSegmentLength) {
            segments.add(segment);
            return;
        }

        PlanElementId previousNodeId = start.id();
        int partIndex = 0;
        for (int distance = maxSegmentLength; distance < length; distance += maxSegmentLength) {
            PlanElementId nodeId = segment.id().child("grade-node-" + partIndex);
            nodes.add(new RoadNode(
                    nodeId,
                    direction.move(start.point(), distance),
                    Set.of(),
                    PlanProperties.empty()
            ));
            segments.add(part(segment, partIndex, previousNodeId, nodeId));
            previousNodeId = nodeId;
            partIndex++;
        }
        segments.add(part(segment, partIndex, previousNodeId, end.id()));
    }

    private static RoadSegment part(
            RoadSegment source,
            int partIndex,
            PlanElementId startNodeId,
            PlanElementId endNodeId
    ) {
        return new RoadSegment(
                source.id().child("grade-part-" + partIndex),
                startNodeId,
                endNodeId,
                source.width(),
                source.tags(),
                source.properties()
        );
    }

    private static Map<PlanElementId, RoadNode> nodesById(RoadGraph graph) {
        Map<PlanElementId, RoadNode> nodes = new HashMap<>();
        for (RoadNode node : graph.nodes()) {
            nodes.put(node.id(), node);
        }
        return Map.copyOf(nodes);
    }

    private static RoadNode requiredNode(Map<PlanElementId, RoadNode> nodesById, PlanElementId id) {
        RoadNode node = nodesById.get(id);
        if (node == null) {
            throw new IllegalStateException("road segment references missing node: " + id.value());
        }
        return node;
    }

    private enum Direction {
        EAST(1, 0),
        WEST(-1, 0),
        SOUTH(0, 1),
        NORTH(0, -1);

        private final int xStep;
        private final int zStep;

        Direction(int xStep, int zStep) {
            this.xStep = xStep;
            this.zStep = zStep;
        }

        private static Direction between(GridPoint start, GridPoint end) {
            if (start.z() == end.z()) {
                return start.x() < end.x() ? EAST : WEST;
            }
            if (start.x() == end.x()) {
                return start.z() < end.z() ? SOUTH : NORTH;
            }
            throw new IllegalArgumentException("road segment must be axis aligned");
        }

        private int length(GridPoint start, GridPoint end) {
            return Math.addExact(Math.abs(end.x() - start.x()), Math.abs(end.z() - start.z()));
        }

        private GridPoint move(GridPoint start, int distance) {
            return new GridPoint(
                    Math.addExact(start.x(), Math.multiplyExact(xStep, distance)),
                    Math.addExact(start.z(), Math.multiplyExact(zStep, distance))
            );
        }
    }
}
