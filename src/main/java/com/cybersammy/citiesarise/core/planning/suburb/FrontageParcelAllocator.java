package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopology;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class FrontageParcelAllocator {
    static final int MAX_SELECTION_SEARCH_NODES = 5_000;

    List<GridBounds> allocate(
            RoadGraph roadGraph,
            GridBounds districtBounds,
            TerrainSurvey survey,
            SuburbPlanningSettings settings,
            long seed,
            int capacity,
            Optional<TerrainTopology> topology
    ) {
        return allocate(
                roadGraph,
                districtBounds,
                survey,
                settings,
                seed,
                capacity,
                topology,
                new ParcelTerrainEvaluationCache(survey, settings)
        );
    }

    List<GridBounds> allocate(
            RoadGraph roadGraph,
            GridBounds districtBounds,
            TerrainSurvey survey,
            SuburbPlanningSettings settings,
            long seed,
            int capacity,
            Optional<TerrainTopology> topology,
            ParcelTerrainEvaluationCache terrainEvaluations
    ) {
        return allocate(
                roadGraph,
                DistrictFootprint.rectangle(districtBounds),
                survey,
                settings,
                seed,
                capacity,
                topology,
                terrainEvaluations
        );
    }

    List<GridBounds> allocate(
            RoadGraph roadGraph,
            DistrictFootprint districtFootprint,
            TerrainSurvey survey,
            SuburbPlanningSettings settings,
            long seed,
            int capacity,
            Optional<TerrainTopology> topology,
            ParcelTerrainEvaluationCache terrainEvaluations
    ) {
        Objects.requireNonNull(roadGraph, "roadGraph");
        Objects.requireNonNull(districtFootprint, "districtFootprint");
        Objects.requireNonNull(survey, "survey");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(terrainEvaluations, "terrainEvaluations");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        terrainEvaluations.requireCompatible(survey, settings);
        GridBounds districtBounds = districtFootprint.bounds();

        Map<PlanElementId, RoadNode> nodes = nodesById(roadGraph);
        List<GridBounds> roadCorridors = roadCorridors(roadGraph, nodes);
        List<GridBounds> roadReservations = roadCorridors.stream()
                .map(corridor -> expandWithin(
                        corridor,
                        districtBounds,
                        settings.terrainTransitions().roadShoulderRadius()
                ))
                .toList();
        List<ParcelCandidate> candidates = collectCandidates(
                roadGraph.segments(),
                nodes,
                roadReservations,
                districtFootprint,
                terrainEvaluations,
                settings,
                seed,
                topology
        );
        return selectCompatibleParcels(candidates, capacity)
                .orElseGet(List::of);
    }

    private static Optional<List<GridBounds>> selectCompatibleParcels(
            List<ParcelCandidate> candidates,
            int capacity
    ) {
        SelectionSearch search = new SelectionSearch(MAX_SELECTION_SEARCH_NODES);
        SelectionCandidates selectionCandidates = new SelectionCandidates(candidates);
        return selectCompatibleParcels(selectionCandidates, capacity, 0, new ArrayList<>(), search);
    }

    private static Optional<List<GridBounds>> selectCompatibleParcels(
            SelectionCandidates candidates,
            int capacity,
            int startIndex,
            List<GridBounds> selected,
            SelectionSearch search
    ) {
        if (!search.visit()) {
            return Optional.empty();
        }
        if (selected.size() == capacity) {
            return Optional.of(List.copyOf(selected));
        }
        int required = capacity - selected.size();
        if (compatibleCapacityUpperBound(candidates, startIndex, selected) < required) {
            return Optional.empty();
        }

        int finalStart = candidates.size() - required;
        for (int index = startIndex; index <= finalStart; index++) {
            GridBounds candidate = candidates.get(index).bounds();
            if (intersectsAny(candidate, selected)) {
                continue;
            }
            selected.add(candidate);
            Optional<List<GridBounds>> result = selectCompatibleParcels(
                    candidates,
                    capacity,
                    index + 1,
                    selected,
                    search
            );
            selected.removeLast();
            if (result.isPresent()) {
                return result;
            }
            if (search.exhausted()) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static int compatibleCapacityUpperBound(
            SelectionCandidates candidates,
            int startIndex,
            List<GridBounds> selected
    ) {
        int capacity = 0;
        for (Map.Entry<FrontageKey, List<Integer>> entry : candidates.indicesByFrontage().entrySet()) {
            int previousMaximum = Integer.MIN_VALUE;
            for (int index : entry.getValue()) {
                if (index < startIndex) {
                    continue;
                }
                GridBounds bounds = candidates.get(index).bounds();
                if (intersectsAny(bounds, selected)) {
                    continue;
                }
                int minimum = axisMinimum(bounds, entry.getKey().side());
                if (minimum < previousMaximum) {
                    continue;
                }
                capacity++;
                previousMaximum = axisMaximum(bounds, entry.getKey().side());
            }
        }
        return capacity;
    }

    private static int axisMinimum(GridBounds bounds, ParcelSide side) {
        return side.horizontal() ? bounds.minX() : bounds.minZ();
    }

    private static int axisMaximum(GridBounds bounds, ParcelSide side) {
        return side.horizontal() ? bounds.maxXExclusive() : bounds.maxZExclusive();
    }

    private static List<ParcelCandidate> collectCandidates(
            List<RoadSegment> segments,
            Map<PlanElementId, RoadNode> nodes,
            List<GridBounds> roadReservations,
            DistrictFootprint districtFootprint,
            ParcelTerrainEvaluationCache terrainEvaluations,
            SuburbPlanningSettings settings,
            long seed,
            Optional<TerrainTopology> topology
    ) {
        Map<GridBounds, ParcelCandidate> candidatesByBounds = new HashMap<>();
        for (RoadSegment segment : segments) {
            RoadNode start = requiredNode(nodes, segment.startNodeId());
            RoadNode end = requiredNode(nodes, segment.endNodeId());
            GridBounds corridor = AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width());
            for (ParcelSide side : ParcelSide.values()) {
                for (GridBounds bounds : candidates(corridor, side, settings)) {
                    if (!isAvailable(bounds, districtFootprint, roadReservations)) {
                        continue;
                    }
                    if (!isDevelopable(bounds, settings, topology)) {
                        continue;
                    }
                    ParcelCandidate candidate = candidate(
                            bounds,
                            terrainEvaluations,
                            seed,
                            segment.id(),
                            side
                    );
                    candidatesByBounds.merge(
                            bounds,
                            candidate,
                            FrontageParcelAllocator::betterCandidate
                    );
                }
            }
        }
        return candidatesByBounds.values().stream()
                .sorted(ParcelCandidate.ORDER)
                .toList();
    }

    private static List<GridBounds> candidates(
            GridBounds corridor,
            ParcelSide side,
            SuburbPlanningSettings settings
    ) {
        if (!side.matches(corridor)) {
            return List.of();
        }
        int frontage = settings.parcelWidth();
        int axisMinimum = side.horizontal() ? corridor.minX() : corridor.minZ();
        int axisMaximum = side.horizontal() ? corridor.maxXExclusive() : corridor.maxZExclusive();
        int available = axisMaximum - axisMinimum;
        if (available < frontage) {
            return List.of();
        }

        List<GridBounds> candidates = new ArrayList<>();
        for (int axis = axisMinimum; axis + frontage <= axisMaximum; axis++) {
            candidates.add(side.bounds(corridor, settings, axis));
        }
        return List.copyOf(candidates);
    }

    private static boolean isAvailable(
            GridBounds candidate,
            DistrictFootprint districtFootprint,
            List<GridBounds> roadReservations
    ) {
        if (!districtFootprint.contains(candidate)) {
            return false;
        }
        return !intersectsAny(candidate, roadReservations);
    }

    private static ParcelCandidate candidate(
            GridBounds bounds,
            ParcelTerrainEvaluationCache terrainEvaluations,
            long seed,
            PlanElementId segmentId,
            ParcelSide side
    ) {
        ParcelTerrainEvaluation evaluation = terrainEvaluations.evaluate(bounds);
        return new ParcelCandidate(
                bounds,
                evaluation.maximumCorrection(),
                evaluation.totalCorrection(),
                stableOrder(seed, segmentId.value(), side.ordinal()),
                new FrontageKey(segmentId, side)
        );
    }

    private static ParcelCandidate betterCandidate(ParcelCandidate first, ParcelCandidate second) {
        return ParcelCandidate.ORDER.compare(first, second) <= 0 ? first : second;
    }

    private static boolean isDevelopable(
            GridBounds parcel,
            SuburbPlanningSettings settings,
            Optional<TerrainTopology> topology
    ) {
        if (topology.isEmpty()) {
            return true;
        }
        TerrainTopology value = topology.orElseThrow();
        if (!value.isEntirelyDevelopable(parcel)) {
            return false;
        }
        GridBounds building = SuburbParcelGeometry.buildingBounds(settings, parcel);
        GridBounds supportedBuilding = expandWithin(
                building,
                value.bounds(),
                settings.terrainTransitions().buildingShoulderRadius()
        );
        return value.isEntirelyDevelopable(supportedBuilding);
    }

    private static GridBounds expandWithin(GridBounds bounds, GridBounds limit, int radius) {
        int minX = Math.max(limit.minX(), subtractClamped(bounds.minX(), radius));
        int minZ = Math.max(limit.minZ(), subtractClamped(bounds.minZ(), radius));
        int maxX = Math.min(limit.maxXExclusive(), addClamped(bounds.maxXExclusive(), radius));
        int maxZ = Math.min(limit.maxZExclusive(), addClamped(bounds.maxZExclusive(), radius));
        return new GridBounds(
                new GridPoint(minX, minZ),
                new GridSize(maxX - minX, maxZ - minZ)
        );
    }

    private static int subtractClamped(int value, int amount) {
        long result = (long) value - amount;
        return (int) Math.max(Integer.MIN_VALUE, result);
    }

    private static int addClamped(int value, int amount) {
        long result = (long) value + amount;
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    private static boolean intersectsAny(GridBounds bounds, List<GridBounds> others) {
        for (GridBounds other : others) {
            if (bounds.intersects(other)) {
                return true;
            }
        }
        return false;
    }

    private static long stableOrder(long seed, String value, int salt) {
        long mixed = seed ^ value.hashCode() ^ ((long) salt * 0x9E3779B97F4A7C15L);
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static Map<PlanElementId, RoadNode> nodesById(RoadGraph graph) {
        Map<PlanElementId, RoadNode> nodes = new HashMap<>();
        for (RoadNode node : graph.nodes()) {
            nodes.put(node.id(), node);
        }
        return Map.copyOf(nodes);
    }

    private static List<GridBounds> roadCorridors(RoadGraph graph, Map<PlanElementId, RoadNode> nodes) {
        List<GridBounds> corridors = new ArrayList<>();
        for (RoadSegment segment : graph.segments()) {
            RoadNode start = requiredNode(nodes, segment.startNodeId());
            RoadNode end = requiredNode(nodes, segment.endNodeId());
            corridors.add(AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width()));
        }
        return List.copyOf(corridors);
    }

    private static RoadNode requiredNode(Map<PlanElementId, RoadNode> nodes, PlanElementId id) {
        RoadNode node = nodes.get(id);
        if (node == null) {
            throw new IllegalArgumentException("road segment references missing node: " + id.value());
        }
        return node;
    }

    private enum ParcelSide {
        NORTH,
        SOUTH,
        WEST,
        EAST;

        boolean horizontal() {
            return this == NORTH || this == SOUTH;
        }

        boolean matches(GridBounds corridor) {
            if (horizontal()) {
                return corridor.size().width() >= corridor.size().depth();
            }
            return corridor.size().depth() >= corridor.size().width();
        }

        GridBounds bounds(GridBounds corridor, SuburbPlanningSettings settings, int axis) {
            int gap = settings.terrainTransitions().roadShoulderRadius();
            return switch (this) {
                case NORTH -> new GridBounds(
                        new GridPoint(axis, corridor.minZ() - gap - settings.parcelDepth()),
                        new GridSize(settings.parcelWidth(), settings.parcelDepth())
                );
                case SOUTH -> new GridBounds(
                        new GridPoint(axis, corridor.maxZExclusive() + gap),
                        new GridSize(settings.parcelWidth(), settings.parcelDepth())
                );
                case WEST -> new GridBounds(
                        new GridPoint(corridor.minX() - gap - settings.parcelDepth(), axis),
                        new GridSize(settings.parcelDepth(), settings.parcelWidth())
                );
                case EAST -> new GridBounds(
                        new GridPoint(corridor.maxXExclusive() + gap, axis),
                        new GridSize(settings.parcelDepth(), settings.parcelWidth())
                );
            };
        }
    }

    private record ParcelCandidate(
            GridBounds bounds,
            int maximumCorrection,
            long totalCorrection,
            long stableOrder,
            FrontageKey frontage
    ) {
        private static final Comparator<ParcelCandidate> ORDER = Comparator
                .comparingInt(ParcelCandidate::maximumCorrection)
                .thenComparingLong(ParcelCandidate::totalCorrection)
                .thenComparingLong(ParcelCandidate::stableOrder)
                .thenComparingInt(candidate -> candidate.bounds().minX())
                .thenComparingInt(candidate -> candidate.bounds().minZ());

        private ParcelCandidate {
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(frontage, "frontage");
        }
    }

    private record FrontageKey(PlanElementId segmentId, ParcelSide side) {
        private FrontageKey {
            Objects.requireNonNull(segmentId, "segmentId");
            Objects.requireNonNull(side, "side");
        }
    }

    private static final class SelectionCandidates {
        private final List<ParcelCandidate> candidates;
        private final Map<FrontageKey, List<Integer>> indicesByFrontage;

        private SelectionCandidates(List<ParcelCandidate> candidates) {
            this.candidates = List.copyOf(candidates);
            Map<FrontageKey, List<Integer>> grouped = new HashMap<>();
            for (int index = 0; index < candidates.size(); index++) {
                grouped.computeIfAbsent(candidates.get(index).frontage(), ignored -> new ArrayList<>())
                        .add(index);
            }
            grouped.forEach((frontage, indices) -> indices.sort(Comparator
                    .comparingInt((Integer index) -> axisMaximum(candidates.get(index).bounds(), frontage.side()))
                    .thenComparingInt(index -> axisMinimum(candidates.get(index).bounds(), frontage.side()))));
            Map<FrontageKey, List<Integer>> immutable = new HashMap<>();
            grouped.forEach((frontage, indices) -> immutable.put(frontage, List.copyOf(indices)));
            this.indicesByFrontage = Map.copyOf(immutable);
        }

        private int size() {
            return candidates.size();
        }

        private ParcelCandidate get(int index) {
            return candidates.get(index);
        }

        private Map<FrontageKey, List<Integer>> indicesByFrontage() {
            return indicesByFrontage;
        }
    }

    private static final class SelectionSearch {
        private final int nodeLimit;
        private int visitedNodes;

        private SelectionSearch(int nodeLimit) {
            this.nodeLimit = nodeLimit;
        }

        private boolean visit() {
            visitedNodes++;
            return visitedNodes <= nodeLimit;
        }

        private boolean exhausted() {
            return visitedNodes > nodeLimit;
        }
    }
}
