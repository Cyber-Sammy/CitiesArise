package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.RoadTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.road.RoadRoute;
import com.cybersammy.citiesarise.core.road.RoadTerrainEvaluationCache;
import com.cybersammy.citiesarise.core.road.RoadRoutingCostPolicy;
import com.cybersammy.citiesarise.core.road.RoadRoutingRequest;
import com.cybersammy.citiesarise.core.road.RoadRoutingResult;
import com.cybersammy.citiesarise.core.road.TerrainAwareRoadRouter;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainFeatureType;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlan;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainPlanningAction;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponse;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy;
import java.util.ArrayList;
import java.util.EnumMap;
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
        TerrainAdaptationPlan adaptationPlan = new com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlanner()
                .plan(request.survey(), request.settings().maxBuildableSlope(), request.terrainResponsePolicy());
        return route(
                request,
                routingBounds,
                source,
                reservedBounds,
                adaptationPlan,
                new RoadTerrainEvaluationCache()
        );
    }

    Optional<RoadGraph> route(
            SuburbPlanningRequest request,
            GridBounds routingBounds,
            RoadGraph source,
            List<GridBounds> reservedBounds,
            TerrainAdaptationPlan adaptationPlan
    ) {
        return route(
                request,
                routingBounds,
                source,
                reservedBounds,
                adaptationPlan,
                new RoadTerrainEvaluationCache()
        );
    }

    Optional<RoadGraph> route(
            SuburbPlanningRequest request,
            GridBounds routingBounds,
            RoadGraph source,
            List<GridBounds> reservedBounds,
            TerrainAdaptationPlan adaptationPlan,
            RoadTerrainEvaluationCache terrainEvaluations
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(routingBounds, "routingBounds");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reservedBounds, "reservedBounds");
        Objects.requireNonNull(adaptationPlan, "adaptationPlan");
        Objects.requireNonNull(terrainEvaluations, "terrainEvaluations");

        Map<PlanElementId, RoadNode> sourceNodes = nodesById(source);
        List<RoadNode> routedNodes = new ArrayList<>(source.nodes());
        List<RoadSegment> routedSegments = new ArrayList<>();
        List<GridBounds> dynamicReservations = new ArrayList<>(reservedBounds);
        TerrainResponsePolicy routingPolicy = routingPolicy(request.terrainResponsePolicy());
        TerrainAdaptationPlan routingAdaptationPlan = adaptationPlan.withPolicy(
                routingPolicy,
                crossingBarrierOverrides(request.terrainResponsePolicy())
        );
        for (RoadSegment segment : source.segments()) {
            Optional<RoutedSegment> routed = routeSegment(
                    request,
                    routingBounds,
                    segment,
                    sourceNodes,
                    dynamicReservations,
                    routingPolicy,
                    routingAdaptationPlan,
                    terrainEvaluations
            );
            if (routed.isEmpty()) {
                return Optional.empty();
            }
            routedNodes.addAll(routed.orElseThrow().nodes());
            routedSegments.addAll(routed.orElseThrow().segments());
            dynamicReservations.addAll(routeCorridors(routed.orElseThrow().route(), segment.width()));
        }
        return Optional.of(new RoadGraph(routedNodes, routedSegments));
    }

    private static Optional<RoutedSegment> routeSegment(
            SuburbPlanningRequest request,
            GridBounds routingBounds,
            RoadSegment segment,
            Map<PlanElementId, RoadNode> sourceNodes,
            List<GridBounds> reservedBounds,
            TerrainResponsePolicy routingPolicy,
            TerrainAdaptationPlan routingAdaptationPlan,
            RoadTerrainEvaluationCache terrainEvaluations
    ) {
        RoadNode start = requiredNode(sourceNodes, segment.startNodeId());
        RoadNode end = requiredNode(sourceNodes, segment.endNodeId());
        RoadRoutingResult result = ROUTER.route(new RoadRoutingRequest(
                request.survey(),
                routingBounds,
                request.survey().bounds(),
                start.point(),
                end.point(),
                segment.width(),
                RoadTerrainShoulderPolicy.RADIUS,
                request.settings().maxBuildableSlope(),
                routingPolicy,
                routingAdaptationPlan,
                RoadRoutingCostPolicy.defaults(),
                reservedBounds,
                List.of(
                        junctionBounds(start.point(), segment.width()),
                        junctionBounds(end.point(), segment.width())
                )
        ), terrainEvaluations);
        if (!result.successful()) {
            return Optional.empty();
        }
        RoadRoute route = result.route().orElseThrow();
        return Optional.of(toSegments(segment, route));
    }

    private static TerrainResponsePolicy routingPolicy(TerrainResponsePolicy source) {
        Map<TerrainFeatureType, TerrainResponse> responses = new EnumMap<>(TerrainFeatureType.class);
        for (TerrainFeatureType featureType : TerrainFeatureType.values()) {
            TerrainResponse response = source.responseFor(featureType);
            responses.put(
                    featureType,
                    response == TerrainResponse.CROSS_IF_SUPPORTED ? TerrainResponse.BUILD_AROUND : response
            );
        }
        return new TerrainResponsePolicy(responses, source.capabilities(), source.adaptationSettings());
    }

    private static Map<TerrainFeatureType, TerrainPlanningAction> crossingBarrierOverrides(
            TerrainResponsePolicy source
    ) {
        Map<TerrainFeatureType, TerrainPlanningAction> overrides = new EnumMap<>(TerrainFeatureType.class);
        for (TerrainFeatureType featureType : TerrainFeatureType.values()) {
            if (source.responseFor(featureType) == TerrainResponse.CROSS_IF_SUPPORTED) {
                overrides.put(featureType, TerrainPlanningAction.ROUTE_AROUND);
            }
        }
        return Map.copyOf(overrides);
    }

    private static GridBounds junctionBounds(GridPoint point, int width) {
        int halfWidth = width / 2;
        return new GridBounds(
                new GridPoint(point.x() - halfWidth, point.z() - halfWidth),
                new GridSize(width, width)
        );
    }

    private static List<GridBounds> routeCorridors(RoadRoute route, int width) {
        List<GridBounds> corridors = new ArrayList<>();
        for (int index = 1; index < route.points().size(); index++) {
            corridors.add(AxisAlignedGridCorridor.bounds(
                    route.points().get(index - 1),
                    route.points().get(index),
                    width
            ));
        }
        return List.copyOf(corridors);
    }

    private static RoutedSegment toSegments(RoadSegment source, RoadRoute route) {
        List<GridPoint> corners = corners(route.points());
        if (corners.size() == 2) {
            return new RoutedSegment(List.of(), List.of(source), route);
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
        return new RoutedSegment(nodes, segments, route);
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

    private record RoutedSegment(List<RoadNode> nodes, List<RoadSegment> segments, RoadRoute route) {
        private RoutedSegment {
            nodes = List.copyOf(nodes);
            segments = List.copyOf(segments);
            Objects.requireNonNull(route, "route");
        }
    }
}
