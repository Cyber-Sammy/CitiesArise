package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.List;
import java.util.Objects;

public record RoadRoute(List<GridPoint> points, long totalCost, List<RoadCrossingCandidate> crossingCandidates) {
    public RoadRoute {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(crossingCandidates, "crossingCandidates");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
        if (totalCost < 0L) {
            throw new IllegalArgumentException("totalCost must not be negative");
        }
        points = List.copyOf(points);
        crossingCandidates = List.copyOf(crossingCandidates);
        requireConnected(points);
    }

    private static void requireConnected(List<GridPoint> points) {
        for (int index = 1; index < points.size(); index++) {
            GridPoint previous = points.get(index - 1);
            GridPoint current = points.get(index);
            int distance = Math.abs(previous.x() - current.x()) + Math.abs(previous.z() - current.z());
            if (distance != 1) {
                throw new IllegalArgumentException("route points must be cardinally adjacent");
            }
        }
    }
}
