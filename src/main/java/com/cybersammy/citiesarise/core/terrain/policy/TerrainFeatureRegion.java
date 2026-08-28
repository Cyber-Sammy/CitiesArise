package com.cybersammy.citiesarise.core.terrain.policy;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.List;
import java.util.Objects;

public record TerrainFeatureRegion(
        int id,
        TerrainFeatureType type,
        List<GridPoint> points,
        GridBounds bounds,
        TerrainFeatureMetrics metrics,
        TerrainPlanningAction action
) {
    public TerrainFeatureRegion {
        if (id < 0) {
            throw new IllegalArgumentException("id must not be negative");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(points, "points");
        points = List.copyOf(points);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(action, "action");
    }
}
