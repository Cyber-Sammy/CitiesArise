package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.BuildingTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.earthwork.RoadTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
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
    List<GridBounds> allocate(
            RoadGraph roadGraph,
            GridBounds districtBounds,
            TerrainSurvey survey,
            SuburbPlanningSettings settings,
            long seed,
            int capacity,
            Optional<TerrainTopology> topology
    ) {
        Objects.requireNonNull(roadGraph, "roadGraph");
        Objects.requireNonNull(districtBounds, "districtBounds");
        Objects.requireNonNull(survey, "survey");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(topology, "topology");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }

        Map<PlanElementId, RoadNode> nodes = nodesById(roadGraph);
        List<GridBounds> roadCorridors = roadCorridors(roadGraph, nodes);
        List<GridBounds> roadReservations = roadCorridors.stream()
                .map(corridor -> expandWithin(
                        corridor,
                        districtBounds,
                        RoadTerrainShoulderPolicy.RADIUS
                ))
                .toList();
        List<ParcelCandidate> candidates = collectCandidates(
                roadGraph.segments(),
                nodes,
                roadReservations,
                districtBounds,
                survey,
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
        return selectCompatibleParcels(candidates, capacity, 0, new ArrayList<>());
    }

    private static Optional<List<GridBounds>> selectCompatibleParcels(
            List<ParcelCandidate> candidates,
            int capacity,
            int startIndex,
            List<GridBounds> selected
    ) {
        if (selected.size() == capacity) {
            return Optional.of(List.copyOf(selected));
        }
        int required = capacity - selected.size();
        if (compatibleCandidateCount(candidates, startIndex, selected) < required) {
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
                    selected
            );
            selected.removeLast();
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private static int compatibleCandidateCount(
            List<ParcelCandidate> candidates,
            int startIndex,
            List<GridBounds> selected
    ) {
        int count = 0;
        for (int index = startIndex; index < candidates.size(); index++) {
            if (!intersectsAny(candidates.get(index).bounds(), selected)) {
                count++;
            }
        }
        return count;
    }

    private static List<ParcelCandidate> collectCandidates(
            List<RoadSegment> segments,
            Map<PlanElementId, RoadNode> nodes,
            List<GridBounds> roadReservations,
            GridBounds districtBounds,
            TerrainSurvey survey,
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
                    if (!isAvailable(bounds, districtBounds, roadReservations)) {
                        continue;
                    }
                    if (!isDevelopable(bounds, settings, topology)) {
                        continue;
                    }
                    ParcelCandidate candidate = candidate(bounds, survey, settings, seed, segment.id(), side);
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
            GridBounds districtBounds,
            List<GridBounds> roadReservations
    ) {
        if (!districtBounds.contains(candidate)) {
            return false;
        }
        return !intersectsAny(candidate, roadReservations);
    }

    private static ParcelCandidate candidate(
            GridBounds bounds,
            TerrainSurvey survey,
            SuburbPlanningSettings settings,
            long seed,
            PlanElementId segmentId,
            ParcelSide side
    ) {
        GridBounds building = SuburbParcelGeometry.buildingBounds(settings, bounds);
        int targetHeight = maximumHeight(building, survey);
        int maximumCorrection = 0;
        long totalCorrection = 0L;
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                int correction = Math.abs(targetHeight - requiredCell(survey, new GridPoint(x, z)).height());
                maximumCorrection = Math.max(maximumCorrection, correction);
                totalCorrection += correction;
            }
        }
        return new ParcelCandidate(
                bounds,
                maximumCorrection,
                totalCorrection,
                stableOrder(seed, segmentId.value(), side.ordinal())
        );
    }

    private static int maximumHeight(GridBounds bounds, TerrainSurvey survey) {
        int maximum = Integer.MIN_VALUE;
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                maximum = Math.max(maximum, requiredCell(survey, new GridPoint(x, z)).height());
            }
        }
        return maximum;
    }

    private static TerrainCell requiredCell(TerrainSurvey survey, GridPoint point) {
        return survey.findCell(point)
                .orElseThrow(() -> new IllegalArgumentException("parcel point is outside terrain survey: " + point));
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
                BuildingTerrainShoulderPolicy.RADIUS
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
            int gap = RoadTerrainShoulderPolicy.RADIUS;
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
            long stableOrder
    ) {
        private static final Comparator<ParcelCandidate> ORDER = Comparator
                .comparingInt(ParcelCandidate::maximumCorrection)
                .thenComparingLong(ParcelCandidate::totalCorrection)
                .thenComparingLong(ParcelCandidate::stableOrder)
                .thenComparingInt(candidate -> candidate.bounds().minX())
                .thenComparingInt(candidate -> candidate.bounds().minZ());

        private ParcelCandidate {
            Objects.requireNonNull(bounds, "bounds");
        }
    }
}
