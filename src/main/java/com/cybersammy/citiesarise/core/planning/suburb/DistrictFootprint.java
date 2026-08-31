package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopology;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class DistrictFootprint {
    private static final int[][] NEIGHBOR_OFFSETS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1}
    };
    private static final Comparator<GridPoint> POINT_ORDER = Comparator
            .comparingInt(GridPoint::z)
            .thenComparingInt(GridPoint::x);

    private final GridBounds bounds;
    private final int developableRegionId;
    private final boolean rectangular;
    private final List<GridPoint> points;
    private final Set<GridPoint> pointSet;

    private DistrictFootprint(GridBounds bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.developableRegionId = -1;
        this.rectangular = true;
        this.points = List.of();
        this.pointSet = Set.of();
    }

    private DistrictFootprint(GridBounds bounds, int developableRegionId, List<GridPoint> points) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        if (developableRegionId < -1) {
            throw new IllegalArgumentException("developableRegionId must be -1 or non-negative");
        }
        this.developableRegionId = developableRegionId;
        this.rectangular = false;
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
        List<GridPoint> ordered = points.stream().sorted(POINT_ORDER).toList();
        Set<GridPoint> unique = Set.copyOf(ordered);
        if (unique.size() != ordered.size()) {
            throw new IllegalArgumentException("points must not contain duplicates");
        }
        for (GridPoint point : ordered) {
            if (!bounds.contains(point)) {
                throw new IllegalArgumentException("footprint point is outside bounds: " + point);
            }
        }
        requireConnected(unique);
        this.points = List.copyOf(ordered);
        this.pointSet = unique;
    }

    static DistrictFootprint rectangle(GridBounds bounds) {
        return new DistrictFootprint(bounds);
    }

    static Optional<DistrictFootprint> fromTopology(GridBounds bounds, TerrainTopology topology) {
        return fromRegionMap(bounds, RegionMap.from(topology));
    }

    static Optional<DistrictFootprint> fromRegionMap(GridBounds bounds, RegionMap regionMap) {
        Optional<ComponentScan> scan = scanComponents(bounds, regionMap, true);
        if (scan.isEmpty()) {
            return Optional.empty();
        }
        ComponentScan selected = scan.orElseThrow();
        List<GridPoint> points = new ArrayList<>(selected.selection().area());
        int width = bounds.size().width();
        for (int index : selected.pointIndices()) {
            points.add(point(bounds, width, index));
        }
        return Optional.of(new DistrictFootprint(
                bounds,
                selected.selection().regionId(),
                points
        ));
    }

    static Optional<ComponentSelection> selectionFromTopology(GridBounds bounds, TerrainTopology topology) {
        return selectionFromRegionMap(bounds, RegionMap.from(topology));
    }

    static Optional<ComponentSelection> selectionFromRegionMap(GridBounds bounds, RegionMap regionMap) {
        return scanComponents(bounds, regionMap, false).map(ComponentScan::selection);
    }

    private static Optional<ComponentScan> scanComponents(
            GridBounds bounds,
            RegionMap regionMap,
            boolean capturePoints
    ) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(regionMap, "regionMap");
        if (!regionMap.bounds().contains(bounds)) {
            throw new IllegalArgumentException("footprint bounds must be inside topology bounds");
        }

        int width = bounds.size().width();
        int depth = bounds.size().depth();
        int cellCount = Math.multiplyExact(width, depth);
        int[] regionIds = new int[cellCount];
        for (int index = 0; index < cellCount; index++) {
            int x = bounds.minX() + (index % width);
            int z = bounds.minZ() + (index / width);
            regionIds[index] = regionMap.regionIdAt(x, z);
        }

        boolean[] visited = new boolean[cellCount];
        int[] queue = new int[cellCount];
        int[] bestIndices = capturePoints ? new int[cellCount] : new int[0];
        ComponentSelection best = null;
        for (int start = 0; start < cellCount; start++) {
            if (regionIds[start] < 0 || visited[start]) {
                continue;
            }
            int regionId = regionIds[start];
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            long centerDistance = Long.MAX_VALUE;
            while (head < tail) {
                int index = queue[head++];
                int localX = index % width;
                int localZ = index / width;
                centerDistance = Math.min(centerDistance, centerDistance(
                        bounds,
                        bounds.minX() + localX,
                        bounds.minZ() + localZ
                ));
                if (localX > 0) {
                    tail = enqueue(index - 1, regionId, regionIds, visited, queue, tail);
                }
                if (localX + 1 < width) {
                    tail = enqueue(index + 1, regionId, regionIds, visited, queue, tail);
                }
                if (localZ > 0) {
                    tail = enqueue(index - width, regionId, regionIds, visited, queue, tail);
                }
                if (localZ + 1 < depth) {
                    tail = enqueue(index + width, regionId, regionIds, visited, queue, tail);
                }
            }
            ComponentSelection candidate = new ComponentSelection(
                    regionId,
                    tail,
                    centerDistance,
                    point(bounds, width, start)
            );
            if (best == null || ComponentSelection.ORDER.compare(candidate, best) < 0) {
                best = candidate;
                if (capturePoints) {
                    System.arraycopy(queue, 0, bestIndices, 0, tail);
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        int[] selectedIndices = capturePoints ? Arrays.copyOf(bestIndices, best.area()) : new int[0];
        return Optional.of(new ComponentScan(best, selectedIndices));
    }

    GridBounds bounds() {
        return bounds;
    }

    int developableRegionId() {
        return developableRegionId;
    }

    List<GridPoint> points() {
        if (rectangular) {
            return rectanglePoints(bounds);
        }
        return points;
    }

    boolean rectangular() {
        return rectangular;
    }

    int area() {
        if (rectangular) {
            return Math.multiplyExact(bounds.size().width(), bounds.size().depth());
        }
        return points.size();
    }

    int excludedArea() {
        return Math.subtractExact(
                Math.multiplyExact(bounds.size().width(), bounds.size().depth()),
                area()
        );
    }

    boolean contains(GridPoint point) {
        Objects.requireNonNull(point, "point");
        if (rectangular) {
            return bounds.contains(point);
        }
        return pointSet.contains(point);
    }

    boolean contains(GridBounds candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!bounds.contains(candidate)) {
            return false;
        }
        if (rectangular) {
            return true;
        }
        for (int z = candidate.minZ(); z < candidate.maxZExclusive(); z++) {
            for (int x = candidate.minX(); x < candidate.maxXExclusive(); x++) {
                if (!contains(new GridPoint(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<GridPoint> rectanglePoints(GridBounds bounds) {
        List<GridPoint> result = new ArrayList<>(Math.multiplyExact(
                bounds.size().width(),
                bounds.size().depth()
        ));
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                result.add(new GridPoint(x, z));
            }
        }
        return List.copyOf(result);
    }

    private static int enqueue(
            int index,
            int regionId,
            int[] regionIds,
            boolean[] visited,
            int[] queue,
            int tail
    ) {
        if (visited[index] || regionIds[index] != regionId) {
            return tail;
        }
        visited[index] = true;
        queue[tail] = index;
        return tail + 1;
    }

    private static long centerDistance(GridBounds bounds, int x, int z) {
        long centerX2 = (long) bounds.minX() + bounds.maxXExclusive() - 1L;
        long centerZ2 = (long) bounds.minZ() + bounds.maxZExclusive() - 1L;
        return Math.abs((2L * x) - centerX2)
                + Math.abs((2L * z) - centerZ2);
    }

    private static GridPoint point(GridBounds bounds, int width, int index) {
        return new GridPoint(bounds.minX() + (index % width), bounds.minZ() + (index / width));
    }

    private static void requireConnected(Set<GridPoint> points) {
        Set<GridPoint> visited = new HashSet<>();
        ArrayDeque<GridPoint> pending = new ArrayDeque<>();
        pending.add(points.iterator().next());
        while (!pending.isEmpty()) {
            GridPoint point = pending.removeFirst();
            if (!visited.add(point)) {
                continue;
            }
            for (int[] offset : NEIGHBOR_OFFSETS) {
                GridPoint neighbor = new GridPoint(point.x() + offset[0], point.z() + offset[1]);
                if (points.contains(neighbor) && !visited.contains(neighbor)) {
                    pending.addLast(neighbor);
                }
            }
        }
        if (visited.size() != points.size()) {
            throw new IllegalArgumentException("footprint points must be four-connected");
        }
    }

    record ComponentSelection(int regionId, int area, long centerDistance, GridPoint firstPoint) {
        private static final Comparator<ComponentSelection> ORDER = Comparator
                .comparingInt(ComponentSelection::area)
                .reversed()
                .thenComparingLong(ComponentSelection::centerDistance)
                .thenComparingInt(ComponentSelection::regionId)
                .thenComparing(ComponentSelection::firstPoint, POINT_ORDER);

        ComponentSelection {
            if (regionId < 0 || area <= 0 || centerDistance < 0L) {
                throw new IllegalArgumentException("invalid component selection");
            }
            Objects.requireNonNull(firstPoint, "firstPoint");
        }
    }

    static final class RegionMap {
        private final GridBounds bounds;
        private final int[] regionIds;

        private RegionMap(GridBounds bounds, int[] regionIds) {
            this.bounds = Objects.requireNonNull(bounds, "bounds");
            this.regionIds = regionIds.clone();
        }

        static RegionMap from(TerrainTopology topology) {
            Objects.requireNonNull(topology, "topology");
            GridBounds bounds = topology.bounds();
            int width = bounds.size().width();
            int cellCount = Math.multiplyExact(width, bounds.size().depth());
            int[] regionIds = new int[cellCount];
            Arrays.fill(regionIds, -1);
            for (int index = 0; index < cellCount; index++) {
                regionIds[index] = topology.regionIdAt(point(bounds, width, index)).orElse(-1);
            }
            return new RegionMap(bounds, regionIds);
        }

        GridBounds bounds() {
            return bounds;
        }

        int regionIdAt(int x, int z) {
            if (x < bounds.minX() || x >= bounds.maxXExclusive()
                    || z < bounds.minZ() || z >= bounds.maxZExclusive()) {
                return -1;
            }
            int localX = x - bounds.minX();
            int localZ = z - bounds.minZ();
            return regionIds[(localZ * bounds.size().width()) + localX];
        }
    }

    private record ComponentScan(ComponentSelection selection, int[] pointIndices) {
        private ComponentScan {
            Objects.requireNonNull(selection, "selection");
            pointIndices = pointIndices.clone();
        }

        @Override
        public int[] pointIndices() {
            return pointIndices.clone();
        }
    }
}
