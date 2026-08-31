package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainFeatureType;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainPlanningAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

public final class TerrainAwareRoadRouter {
    private static final List<GridPoint> NEIGHBOR_OFFSETS = List.of(
            new GridPoint(-1, 0),
            new GridPoint(0, -1),
            new GridPoint(1, 0),
            new GridPoint(0, 1)
    );
    private static final Comparator<SearchNode> SEARCH_ORDER = Comparator
            .comparingLong(SearchNode::estimatedTotalCost)
            .thenComparingLong(SearchNode::cost)
            .thenComparingInt(node -> node.point().x())
            .thenComparingInt(node -> node.point().z());

    public RoadRoutingResult route(RoadRoutingRequest request) {
        return route(request, new RoadTerrainEvaluationCache());
    }

    public RoadRoutingResult route(
            RoadRoutingRequest request,
            RoadTerrainEvaluationCache terrainEvaluations
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(terrainEvaluations, "terrainEvaluations");
        EvaluationCache evaluations = new EvaluationCache(
                request,
                terrainEvaluations.contextFor(request)
        );
        StepEvaluation start = evaluations.point(request.start());
        if (!start.traversable()) {
            return RoadRoutingResult.rejected(RoadRoutingFailureReason.START_BLOCKED);
        }
        StepEvaluation destination = evaluations.point(request.destination());
        if (!destination.traversable()) {
            return RoadRoutingResult.rejected(RoadRoutingFailureReason.DESTINATION_BLOCKED);
        }
        if (request.start().equals(request.destination())) {
            return RoadRoutingResult.success(new RoadRoute(List.of(request.start()), 0L, List.of()));
        }
        return search(request, evaluations);
    }

    private static RoadRoutingResult search(RoadRoutingRequest request, EvaluationCache evaluations) {
        PriorityQueue<SearchNode> open = new PriorityQueue<>(SEARCH_ORDER);
        Map<GridPoint, Long> costs = new HashMap<>();
        Map<GridPoint, GridPoint> previous = new HashMap<>();
        costs.put(request.start(), 0L);
        open.add(searchNode(request, request.start(), 0L));

        while (!open.isEmpty()) {
            SearchNode current = open.remove();
            if (isStale(current, costs)) {
                continue;
            }
            if (current.point().equals(request.destination())) {
                return successfulResult(request, evaluations, previous, current.cost());
            }
            visitNeighbors(request, evaluations, current, open, costs, previous);
        }
        return RoadRoutingResult.rejected(RoadRoutingFailureReason.NO_ROUTE);
    }

    private static boolean isStale(SearchNode node, Map<GridPoint, Long> costs) {
        return node.cost() != costs.getOrDefault(node.point(), Long.MAX_VALUE);
    }

    private static void visitNeighbors(
            RoadRoutingRequest request,
            EvaluationCache evaluations,
            SearchNode current,
            PriorityQueue<SearchNode> open,
            Map<GridPoint, Long> costs,
            Map<GridPoint, GridPoint> previous
    ) {
        for (GridPoint offset : NEIGHBOR_OFFSETS) {
            GridPoint neighbor = new GridPoint(
                    current.point().x() + offset.x(),
                    current.point().z() + offset.z()
            );
            if (!request.routingBounds().contains(neighbor)) {
                continue;
            }
            StepEvaluation step = evaluations.step(current.point(), neighbor);
            if (!step.traversable()) {
                continue;
            }
            long candidateCost = saturatedAdd(current.cost(), step.cost());
            if (candidateCost >= costs.getOrDefault(neighbor, Long.MAX_VALUE)) {
                continue;
            }
            costs.put(neighbor, candidateCost);
            previous.put(neighbor, current.point());
            open.add(searchNode(request, neighbor, candidateCost));
        }
    }

    private static SearchNode searchNode(RoadRoutingRequest request, GridPoint point, long cost) {
        long remainingDistance = manhattanDistance(point, request.destination());
        long heuristic = saturatedMultiply(remainingDistance, request.costPolicy().baseStepCost());
        return new SearchNode(point, cost, saturatedAdd(cost, heuristic));
    }

    private static RoadRoutingResult successfulResult(
            RoadRoutingRequest request,
            EvaluationCache evaluations,
            Map<GridPoint, GridPoint> previous,
            long totalCost
    ) {
        List<GridPoint> points = reconstructPath(request.start(), request.destination(), previous);
        List<RoadCrossingCandidate> crossings = crossingCandidates(evaluations, points);
        return RoadRoutingResult.success(new RoadRoute(points, totalCost, crossings));
    }

    private static List<GridPoint> reconstructPath(
            GridPoint start,
            GridPoint destination,
            Map<GridPoint, GridPoint> previous
    ) {
        List<GridPoint> reversed = new ArrayList<>();
        GridPoint current = destination;
        reversed.add(current);
        while (!current.equals(start)) {
            current = Objects.requireNonNull(previous.get(current), "route predecessor");
            reversed.add(current);
        }
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static List<RoadCrossingCandidate> crossingCandidates(
            EvaluationCache evaluations,
            List<GridPoint> points
    ) {
        List<RoadCrossingCandidate> candidates = new ArrayList<>();
        TerrainFeatureType activeType = null;
        List<GridPoint> activePoints = new ArrayList<>();
        for (int index = 1; index < points.size(); index++) {
            StepEvaluation step = evaluations.step(points.get(index - 1), points.get(index));
            Optional<TerrainFeatureType> crossingType = step.crossingFeature();
            if (crossingType.isPresent() && crossingType.orElseThrow() == activeType) {
                activePoints.add(points.get(index));
                continue;
            }
            addCrossing(candidates, activeType, activePoints);
            activePoints = new ArrayList<>();
            activeType = crossingType.orElse(null);
            if (activeType != null) {
                activePoints.add(points.get(index));
            }
        }
        addCrossing(candidates, activeType, activePoints);
        return List.copyOf(candidates);
    }

    private static void addCrossing(
            List<RoadCrossingCandidate> candidates,
            TerrainFeatureType featureType,
            List<GridPoint> points
    ) {
        if (featureType != null) {
            candidates.add(new RoadCrossingCandidate(featureType, points));
        }
    }

    private static StepEvaluation evaluatePoint(
            RoadRoutingRequest request,
            GridPoint point,
            RoadTerrainEvaluationCache.Context terrainEvaluations
    ) {
        GridBounds pointBounds = AxisAlignedGridCorridor.bounds(point, point, request.roadWidth());
        if (!request.routingBounds().contains(pointBounds)) {
            return StepEvaluation.blocked();
        }
        return footprintEvaluation(
                request,
                expandWithin(pointBounds, request.terrainCheckBounds(), request.supportRadius()),
                pointBounds,
                point,
                point,
                terrainEvaluations
        );
    }

    private static StepEvaluation evaluateStep(
            RoadRoutingRequest request,
            EvaluationCache evaluations,
            RoadTerrainEvaluationCache.Context terrainEvaluations,
            GridPoint start,
            GridPoint destination
    ) {
        GridBounds corridor = AxisAlignedGridCorridor.bounds(start, destination, request.roadWidth());
        if (!request.routingBounds().contains(corridor)) {
            return StepEvaluation.blocked();
        }
        StepEvaluation evaluation = footprintEvaluation(
                request,
                expandWithin(corridor, request.terrainCheckBounds(), request.supportRadius()),
                corridor,
                start,
                destination,
                terrainEvaluations
        );
        if (!evaluation.traversable()) {
            return evaluation;
        }
        return new StepEvaluation(
                true,
                evaluation.cost(),
                evaluations.point(destination).crossingFeature()
        );
    }

    private static StepEvaluation footprintEvaluation(
            RoadRoutingRequest request,
            GridBounds footprint,
            GridBounds collisionFootprint,
            GridPoint start,
            GridPoint destination,
            RoadTerrainEvaluationCache.Context terrainEvaluations
    ) {
        if (!request.terrainCheckBounds().contains(footprint)) {
            return StepEvaluation.blocked();
        }
        if (intersectsReservedBounds(
                collisionFootprint,
                request.reservedBounds(),
                request.allowedReservedOverlapBounds()
        )) {
            return StepEvaluation.blocked();
        }
        TerrainFootprintEvaluation terrain = requestTerrainEvaluation(
                request,
                footprint,
                terrainEvaluations
        );
        if (!terrain.traversable()) {
            return StepEvaluation.blocked();
        }
        long cost = movementCost(
                request,
                start,
                destination,
                terrain.maximumSlope(),
                terrain.roughCells(),
                terrain.crossingFeature().orElse(null)
        );
        return StepEvaluation.traversable(cost, terrain.crossingFeature().orElse(null));
    }

    private static TerrainFootprintEvaluation requestTerrainEvaluation(
            RoadRoutingRequest request,
            GridBounds footprint,
            RoadTerrainEvaluationCache.Context evaluations
    ) {
        return evaluations.getOrCreate(
                footprint,
                () -> evaluateTerrainFootprint(request, footprint)
        );
    }

    private static TerrainFootprintEvaluation evaluateTerrainFootprint(
            RoadRoutingRequest request,
            GridBounds footprint
    ) {
        TerrainFeatureType crossingFeature = null;
        double maximumSlope = 0.0;
        int roughCells = 0;
        for (int z = footprint.minZ(); z < footprint.maxZExclusive(); z++) {
            for (int x = footprint.minX(); x < footprint.maxXExclusive(); x++) {
                TerrainCell cell = requiredCell(request, new GridPoint(x, z));
                Optional<TerrainFeatureType> featureType = featureType(request, cell);
                if (featureType.isPresent()) {
                    TerrainPlanningAction action = request.terrainAdaptationPlan()
                            .actionAt(cell.point(), featureType.orElseThrow());
                    if (blocksRouting(action)) {
                        return TerrainFootprintEvaluation.blocked();
                    }
                    if (action == TerrainPlanningAction.CROSS) {
                        crossingFeature = preferredCrossing(crossingFeature, featureType.orElseThrow());
                    }
                }
                maximumSlope = Math.max(maximumSlope, cell.slope());
                if (cell.terrainCategory() == TerrainCategory.ROUGH) {
                    roughCells++;
                }
            }
        }
        return TerrainFootprintEvaluation.traversable(maximumSlope, roughCells, crossingFeature);
    }

    private static boolean blocksRouting(TerrainPlanningAction action) {
        return switch (action) {
            case RELOCATE, PRESERVE_IN_PLACE, ROUTE_AROUND -> true;
            case DIRECT_TERRAFORMING, CROSS, STANDARD_PLACEMENT -> false;
        };
    }

    private static Optional<TerrainFeatureType> featureType(
            RoadRoutingRequest request,
            TerrainCell cell
    ) {
        if (cell.water()) {
            return Optional.of(TerrainFeatureType.WATER);
        }
        if (cell.terrainCategory() == TerrainCategory.BLOCKED) {
            return Optional.of(TerrainFeatureType.BLOCKED_TERRAIN);
        }
        if (cell.slope() > request.maxBuildableSlope()) {
            return Optional.of(TerrainFeatureType.STEEP_SLOPE);
        }
        return Optional.empty();
    }

    private static TerrainFeatureType preferredCrossing(
            TerrainFeatureType current,
            TerrainFeatureType candidate
    ) {
        if (current == null) {
            return candidate;
        }
        return current.ordinal() <= candidate.ordinal() ? current : candidate;
    }

    private static long movementCost(
            RoadRoutingRequest request,
            GridPoint start,
            GridPoint destination,
            double maximumSlope,
            int roughCells,
            TerrainFeatureType crossingFeature
    ) {
        RoadRoutingCostPolicy policy = request.costPolicy();
        int startHeight = requiredCell(request, start).height();
        int destinationHeight = requiredCell(request, destination).height();
        long heightDelta = Math.abs((long) startHeight - destinationHeight);
        long cost = policy.baseStepCost();
        cost = saturatedAdd(cost, saturatedMultiply(heightDelta, policy.heightChangePenalty()));
        cost = saturatedAdd(cost, Math.round(maximumSlope * policy.slopePenalty()));
        cost = saturatedAdd(cost, saturatedMultiply(roughCells, policy.roughTerrainPenalty()));
        if (crossingFeature != null) {
            cost = saturatedAdd(cost, policy.crossingPenalty());
        }
        return cost;
    }

    private static TerrainCell requiredCell(RoadRoutingRequest request, GridPoint point) {
        return request.survey()
                .findCell(point)
                .orElseThrow(() -> new IllegalStateException("routing point is outside terrain survey: " + point));
    }

    private static boolean intersectsReservedBounds(
            GridBounds footprint,
            List<GridBounds> reservedBounds,
            List<GridBounds> allowedOverlapBounds
    ) {
        for (GridBounds reserved : reservedBounds) {
            if (footprint.intersects(reserved) && !intersectionIsAllowed(footprint, reserved, allowedOverlapBounds)) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersectionIsAllowed(
            GridBounds first,
            GridBounds second,
            List<GridBounds> allowedOverlapBounds
    ) {
        int minX = Math.max(first.minX(), second.minX());
        int minZ = Math.max(first.minZ(), second.minZ());
        int maxX = Math.min(first.maxXExclusive(), second.maxXExclusive());
        int maxZ = Math.min(first.maxZExclusive(), second.maxZExclusive());
        GridBounds intersection = new GridBounds(
                new GridPoint(minX, minZ),
                new GridSize(maxX - minX, maxZ - minZ)
        );
        for (GridBounds allowed : allowedOverlapBounds) {
            if (allowed.contains(intersection)) {
                return true;
            }
        }
        return false;
    }

    private static GridBounds expandWithin(GridBounds bounds, GridBounds limit, int radius) {
        if (radius == 0) {
            return bounds;
        }
        int minX = Math.max(limit.minX(), subtractClamped(bounds.minX(), radius));
        int minZ = Math.max(limit.minZ(), subtractClamped(bounds.minZ(), radius));
        int maxX = Math.min(limit.maxXExclusive(), addClamped(bounds.maxXExclusive(), radius));
        int maxZ = Math.min(limit.maxZExclusive(), addClamped(bounds.maxZExclusive(), radius));
        return new GridBounds(
                new GridPoint(minX, minZ),
                new GridSize(maxX - minX, maxZ - minZ)
        );
    }

    private static int subtractClamped(int value, int offset) {
        long result = (long) value - offset;
        return (int) Math.max(Integer.MIN_VALUE, result);
    }

    private static int addClamped(int value, int offset) {
        long result = (long) value + offset;
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    private static long manhattanDistance(GridPoint first, GridPoint second) {
        return Math.abs((long) first.x() - second.x()) + Math.abs((long) first.z() - second.z());
    }

    private static long saturatedAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    private static long saturatedMultiply(long first, long second) {
        if (first == 0L || second == 0L) {
            return 0L;
        }
        if (first > Long.MAX_VALUE / second) {
            return Long.MAX_VALUE;
        }
        return first * second;
    }

    private record SearchNode(GridPoint point, long cost, long estimatedTotalCost) {
    }

    private static final class EvaluationCache {
        private final RoadRoutingRequest request;
        private final RoadTerrainEvaluationCache.Context terrainEvaluations;
        private final Map<GridPoint, StepEvaluation> pointEvaluations = new HashMap<>();
        private final Map<RouteStep, StepEvaluation> stepEvaluations = new HashMap<>();

        private EvaluationCache(
                RoadRoutingRequest request,
                RoadTerrainEvaluationCache.Context terrainEvaluations
        ) {
            this.request = request;
            this.terrainEvaluations = terrainEvaluations;
        }

        private StepEvaluation point(GridPoint point) {
            return pointEvaluations.computeIfAbsent(
                    point,
                    candidate -> evaluatePoint(request, candidate, terrainEvaluations)
            );
        }

        private StepEvaluation step(GridPoint start, GridPoint destination) {
            return stepEvaluations.computeIfAbsent(
                    new RouteStep(start, destination),
                    candidate -> evaluateStep(
                            request,
                            this,
                            terrainEvaluations,
                            candidate.start(),
                            candidate.destination()
                    )
            );
        }
    }

    private record RouteStep(GridPoint start, GridPoint destination) {
        private RouteStep {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(destination, "destination");
        }
    }

    private record StepEvaluation(
            boolean traversable,
            long cost,
            Optional<TerrainFeatureType> crossingFeature
    ) {
        private StepEvaluation {
            Objects.requireNonNull(crossingFeature, "crossingFeature");
            if (cost < 0L) {
                throw new IllegalArgumentException("cost must not be negative");
            }
        }

        private static StepEvaluation blocked() {
            return new StepEvaluation(false, 0L, Optional.empty());
        }

        private static StepEvaluation traversable(long cost, TerrainFeatureType crossingFeature) {
            return new StepEvaluation(true, cost, Optional.ofNullable(crossingFeature));
        }
    }
}
