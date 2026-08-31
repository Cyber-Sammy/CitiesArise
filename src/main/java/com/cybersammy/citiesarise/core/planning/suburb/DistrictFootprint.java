package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopology;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(topology, "topology");
        if (!topology.bounds().contains(bounds)) {
            throw new IllegalArgumentException("footprint bounds must be inside topology bounds");
        }

        Set<GridPoint> remaining = new HashSet<>();
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                GridPoint point = new GridPoint(x, z);
                if (topology.regionIdAt(point).isPresent()) {
                    remaining.add(point);
                }
            }
        }

        Component best = null;
        while (!remaining.isEmpty()) {
            GridPoint start = remaining.stream().min(POINT_ORDER).orElseThrow();
            int regionId = topology.regionIdAt(start).orElseThrow();
            Component candidate = component(bounds, topology, remaining, start, regionId);
            if (best == null || Component.ORDER.compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(new DistrictFootprint(bounds, best.regionId(), best.points()));
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

    int preservedGapArea() {
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

    private static Component component(
            GridBounds bounds,
            TerrainTopology topology,
            Set<GridPoint> remaining,
            GridPoint start,
            int regionId
    ) {
        ArrayDeque<GridPoint> pending = new ArrayDeque<>();
        List<GridPoint> points = new ArrayList<>();
        pending.add(start);
        remaining.remove(start);
        while (!pending.isEmpty()) {
            GridPoint point = pending.removeFirst();
            points.add(point);
            for (int[] offset : NEIGHBOR_OFFSETS) {
                GridPoint neighbor = new GridPoint(point.x() + offset[0], point.z() + offset[1]);
                if (!bounds.contains(neighbor) || !remaining.contains(neighbor)) {
                    continue;
                }
                if (topology.regionIdAt(neighbor).orElseThrow() != regionId) {
                    continue;
                }
                remaining.remove(neighbor);
                pending.addLast(neighbor);
            }
        }
        points.sort(POINT_ORDER);
        return new Component(regionId, points, centerDistance(points, bounds), points.getFirst());
    }

    private static long centerDistance(List<GridPoint> points, GridBounds bounds) {
        long centerX2 = (long) bounds.minX() + bounds.maxXExclusive() - 1L;
        long centerZ2 = (long) bounds.minZ() + bounds.maxZExclusive() - 1L;
        long best = Long.MAX_VALUE;
        for (GridPoint point : points) {
            long distance = Math.abs((2L * point.x()) - centerX2)
                    + Math.abs((2L * point.z()) - centerZ2);
            best = Math.min(best, distance);
        }
        return best;
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

    private record Component(int regionId, List<GridPoint> points, long centerDistance, GridPoint firstPoint) {
        private static final Comparator<Component> ORDER = Comparator
                .comparingInt((Component component) -> component.points().size())
                .reversed()
                .thenComparingLong(Component::centerDistance)
                .thenComparingInt(Component::regionId)
                .thenComparing(Component::firstPoint, POINT_ORDER);

        private Component {
            points = List.copyOf(points);
            Objects.requireNonNull(firstPoint, "firstPoint");
        }
    }
}
