package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.Objects;

public final class BuildingTerrainShoulderPolicy {
    public static final int RADIUS = 3;
    public static final int MAX_FILL_DEPTH = 3;

    private BuildingTerrainShoulderPolicy() {
    }

    public static int distanceFrom(GridBounds bounds, GridPoint point) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(point, "point");
        int xDistance = axisDistance(point.x(), bounds.minX(), bounds.maxXExclusive());
        int zDistance = axisDistance(point.z(), bounds.minZ(), bounds.maxZExclusive());
        return Math.max(xDistance, zDistance);
    }

    public static int targetElevation(GridBounds bounds, GridPoint point, int platformElevation) {
        return Math.subtractExact(platformElevation, distanceFrom(bounds, point));
    }

    public static boolean contains(GridBounds bounds, GridPoint point) {
        int distance = distanceFrom(bounds, point);
        if (distance == 0) {
            return false;
        }
        return distance <= RADIUS;
    }

    private static int axisDistance(int coordinate, int minimum, int maximumExclusive) {
        if (coordinate < minimum) {
            return minimum - coordinate;
        }
        if (coordinate >= maximumExclusive) {
            return coordinate - (maximumExclusive - 1);
        }
        return 0;
    }
}
