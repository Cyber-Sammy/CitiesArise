package com.cybersammy.citiesarise.core.terrain.topology;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.List;
import java.util.Objects;

public record DevelopableRegion(int id, List<GridPoint> points, GridBounds bounds) {
    public DevelopableRegion {
        if (id < 0) {
            throw new IllegalArgumentException("id must not be negative");
        }
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
        Objects.requireNonNull(bounds, "bounds");
        for (GridPoint point : points) {
            Objects.requireNonNull(point, "points must not contain null");
            if (!bounds.contains(point)) {
                throw new IllegalArgumentException("region point is outside region bounds");
            }
        }
        points = List.copyOf(points);
    }

    public int area() {
        return points.size();
    }
}
