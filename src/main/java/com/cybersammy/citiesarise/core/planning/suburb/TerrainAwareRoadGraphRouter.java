package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.RoadTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.road.RoadRoute;
import com.cybersammy.citiesarise.core.road.RoadRoutingCostPolicy;
import com.cybersammy.citiesarise.core.road.RoadRoutingRequest;
import com.cybersammy.citiesarise.core.road.RoadRoutingResult;
import com.cybersammy.citiesarise.core.road.TerrainAwareRoadRouter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class TerrainAwareRoadGraphRouter {
    private static final TerrainAwareRoadRouter ROUTER = new TerrainAwareRoadRouter();

    Optional<RoadGraph> route(
            SuburbPlanningRequest request,
            GridBounds routingBounds,
            RoadGraph source,
            List<GridBounds> reservedBounds
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(routingBounds, "routingBounds");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reservedBounds, "reservedBounds");

        Map<PlanElementId, RoadNode> sourceNodes = nodesById(source);
        List<RoadNode> routedNodes = new ArrayList<>(source.nodes());
        List<RoadSegment> routedSegments = new ArrayList<>();
        for (RoadSegment segment : source.segments()) {
            Optional<RoutedSegment> routed = routeSegment(
                    request,
                    routingBounds,
                    segment,
                    sourceNodes,
                    reservedBounds
            );
            if (routed.isEmpty()) {
                return Optional.empty();
            }
            routedNodes.addAll(routed.orElseThrow().nodes());
            routedSegments.addAll(routed.orElseThrow().segments());
        }
        return Optional.of(new RoadGraph(routedNodes, routedSegments));
    }

    private static Optional<RoutedSegment> routeSegment(
            SuburbPlanningRequest request,
            GridBounds routingBounds,
            RoadSegment segment,
            Map<PlanElementId, RoadNode> sourceNodes,
            List<GridBounds> reservedBounds
    ) {
        RoadNode start = requiredNode(sourceNodes, segment.startNodeId());
        RoadNode end = requiredNode(sourceNodes, segment.endNodeId());
        RoadRoutingResult result = ROUTER.route(new RoadRoutingRequest(
                request.survey(),
                routingBounds,
                start.point(),
                end.point(),
                segment.width(),
                RoadTerrainShoulderPolicy.RADIUS,
                request.settings().maxBuildableSlope(),
                request.terrainResponsePolicy(),
                RoadRoutingCostPolicy.defaults(),
                reservedBounds
        ));
        if (!result.successful()) {
            return Optional.empty();
        }
        RoadRoute route = result.route().orElseThrow();
        if (!route.crossingCandidates().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toSegments(segment, route));
    }

    private static RoutedSegment toSegments(RoadSegment source, RoadRoute route) {
        List<GridPoint> corners = corners(route.points());
        if (corners.size() == 2) {
            return new RoutedSegment(List.of(), List.of(source));
        }

        List<RoadNode> nodes = new ArrayList<>();
        List<RoadSegment> segments = new ArrayList<>();
        PlanElementId previousNodeId = source.startNodeId();
        for (int index = 1; index < corners.size(); index++) {
            boolean destination = index == corners.size() - 1;
            PlanElementId nextNodeId = destination
                    ? source.endNodeId()
                    : source.id().child("route-node-" + (index - 1));
            if (!destination) {
                nodes.add(new RoadNode(
                        nextNodeId,
                        corners.get(index),
                        Set.of(),
                        PlanProperties.empty()
                ));
            }
            segments.add(new RoadSegment(
                    source.id().child("route-part-" + (index - 1)),
                    previousNodeId,
                    nextNodeId,
                    source.width(),
                    source.tags(),
                    source.properties()
            ));
            previousNodeId = nextNodeId;
        }
        return new RoutedSegment(nodes, segments);
    }

    private static List<GridPoint> corners(List<GridPoint> points) {
        if (points.size() <= 2) {
            return points;
        }
        List<GridPoint> corners = new ArrayList<>();
        corners.add(points.getFirst());
        for (int index = 1; index < points.size() - 1; index++) {
            GridPoint previous = points.get(index - 1);
            GridPoint current = points.get(index);
            GridPoint next = points.get(index + 1);
            if (directionChanges(previous, current, next)) {
                corners.add(current);
            }
        }
        corners.add(points.getLast());
        return List.copyOf(corners);
    }

    private static boolean directionChanges(GridPoint previous, GridPoint current, GridPoint next) {
        int firstX = current.x() - previous.x();
        int firstZ = current.z() - previous.z();
        int secondX = next.x() - current.x();
        int secondZ = next.z() - current.z();
        return firstX != secondX || firstZ != secondZ;
    }

    private static Map<PlanElementId, RoadNode> nodesById(RoadGraph graph) {
        Map<PlanElementId, RoadNode> nodes = new HashMap<>();
        for (RoadNode node : graph.nodes()) {
            RoadNode previous = nodes.put(node.id(), node);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate road node id: " + node.id().value());
            }
        }
        return Map.copyOf(nodes);
    }

    private static RoadNode requiredNode(Map<PlanElementId, RoadNode> nodes, PlanElementId id) {
        RoadNode node = nodes.get(id);
        if (node == null) {
            throw new IllegalArgumentException("road segment references missing node: " + id.value());
        }
        return node;
    }

    private record RoutedSegment(List<RoadNode> nodes, List<RoadSegment> segments) {
        private RoutedSegment {
            nodes = List.copyOf(nodes);
            segments = List.copyOf(segments);
        }
    }
}
