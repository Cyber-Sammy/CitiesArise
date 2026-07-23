package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ElevationTransitionPolicy {
    private ElevationTransitionPolicy() {
    }

    public static boolean canMaterialize(
            ElevationTransition transition,
            ElevationZone source,
            ElevationZone target
    ) {
        Objects.requireNonNull(transition, "transition");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (transition.type() == ElevationTransitionType.ROAD_CONNECTION) {
            return transition.elevationDelta() <= 1L;
        }
        return transition.elevationDelta() <= maximumMaterializableDelta(transition, source);
    }

    public static int maximumMaterializableDelta(ElevationTransition transition, ElevationZone source) {
        Objects.requireNonNull(transition, "transition");
        Objects.requireNonNull(source, "source");
        if (transition.type() == ElevationTransitionType.ROAD_CONNECTION) {
            return 1;
        }
        GridPoint start = nearestPoint(source.bounds(), transition.anchor());
        return manhattanDistance(start, transition.anchor());
    }

    public static List<TransitionPoint> materialize(
            ElevationTransition transition,
            ElevationZone source,
            ElevationZone target
    ) {
        if (!canMaterialize(transition, source, target)) {
            throw new IllegalArgumentException("elevation transition exceeds available transition length");
        }
        if (transition.type() == ElevationTransitionType.ROAD_CONNECTION) {
            return roadPoints(source, target);
        }
        return buildingAccessPoints(transition, source);
    }

    private static List<TransitionPoint> roadPoints(ElevationZone source, ElevationZone target) {
        if (source.targetElevation() == target.targetElevation()) {
            return List.of();
        }
        GridBounds intersection = intersection(source.bounds(), target.bounds());
        int elevation = Math.max(source.targetElevation(), target.targetElevation());
        List<TransitionPoint> points = new ArrayList<>();
        for (int z = intersection.minZ(); z < intersection.maxZExclusive(); z++) {
            for (int x = intersection.minX(); x < intersection.maxXExclusive(); x++) {
                points.add(new TransitionPoint(new GridPoint(x, z), elevation, true));
            }
        }
        return List.copyOf(points);
    }

    private static List<TransitionPoint> buildingAccessPoints(
            ElevationTransition transition,
            ElevationZone roadZone
    ) {
        GridPoint start = transitionStart(transition, roadZone.bounds());
        List<GridPoint> path = manhattanPath(start, transition.anchor());
        if (path.size() < 2) {
            return List.of();
        }

        int edgeCount = path.size() - 1;
        List<Integer> elevations = pathElevations(
                transition.sourceElevation(),
                transition.targetElevation(),
                edgeCount
        );
        List<TransitionPoint> points = new ArrayList<>();
        for (int index = 0; index < edgeCount; index++) {
            int elevation = elevations.get(index);
            int nextElevation = elevations.get(index + 1);
            boolean step = elevation != nextElevation;
            int surfaceElevation = step ? Math.max(elevation, nextElevation) : elevation;
            points.add(new TransitionPoint(path.get(index), surfaceElevation, step));
        }
        return List.copyOf(points);
    }

    private static List<Integer> pathElevations(int source, int target, int edgeCount) {
        int delta = target - source;
        int direction = Integer.signum(delta);
        int absoluteDelta = Math.abs(delta);
        List<Integer> elevations = new ArrayList<>();
        for (int index = 0; index <= edgeCount; index++) {
            int completedSteps = (absoluteDelta * index) / edgeCount;
            elevations.add(source + (direction * completedSteps));
        }
        return List.copyOf(elevations);
    }

    private static List<GridPoint> manhattanPath(GridPoint start, GridPoint end) {
        List<GridPoint> points = new ArrayList<>();
        points.add(start);
        int x = start.x();
        int z = start.z();
        while (x != end.x()) {
            x += Integer.signum(end.x() - x);
            points.add(new GridPoint(x, z));
        }
        while (z != end.z()) {
            z += Integer.signum(end.z() - z);
            points.add(new GridPoint(x, z));
        }
        return List.copyOf(points);
    }

    private static GridPoint transitionStart(ElevationTransition transition, GridBounds roadBounds) {
        return nearestPoint(roadBounds, transition.anchor());
    }

    private static GridPoint nearestPoint(GridBounds bounds, GridPoint point) {
        int x = clamp(point.x(), bounds.minX(), bounds.maxXExclusive() - 1);
        int z = clamp(point.z(), bounds.minZ(), bounds.maxZExclusive() - 1);
        return new GridPoint(x, z);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static GridBounds intersection(GridBounds first, GridBounds second) {
        int minX = Math.max(first.minX(), second.minX());
        int minZ = Math.max(first.minZ(), second.minZ());
        int maxX = Math.min(first.maxXExclusive(), second.maxXExclusive());
        int maxZ = Math.min(first.maxZExclusive(), second.maxZExclusive());
        return new GridBounds(
                new GridPoint(minX, minZ),
                new com.cybersammy.citiesarise.core.geometry.GridSize(maxX - minX, maxZ - minZ)
        );
    }

    private static int manhattanDistance(GridPoint first, GridPoint second) {
        return Math.addExact(Math.abs(first.x() - second.x()), Math.abs(first.z() - second.z()));
    }

    public record TransitionPoint(GridPoint point, int targetElevation, boolean step) {
        public TransitionPoint {
            Objects.requireNonNull(point, "point");
        }
    }
}
