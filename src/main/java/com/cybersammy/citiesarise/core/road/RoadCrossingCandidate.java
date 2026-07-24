package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainFeatureType;
import java.util.List;
import java.util.Objects;

public record RoadCrossingCandidate(TerrainFeatureType featureType, List<GridPoint> points) {
    public RoadCrossingCandidate {
        Objects.requireNonNull(featureType, "featureType");
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
        points = List.copyOf(points);
    }

    public GridPoint entry() {
        return points.getFirst();
    }

    public GridPoint exit() {
        return points.getLast();
    }
}
