package com.cybersammy.citiesarise.core.geometry;

import java.util.Objects;

public final class AxisAlignedGridCorridor {
    private AxisAlignedGridCorridor() {
    }

    public static GridBounds bounds(GridPoint start, GridPoint end, int width) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (start.z() == end.z()) {
            return horizontalBounds(start, end, width);
        }
        if (start.x() == end.x()) {
            return verticalBounds(start, end, width);
        }
        throw new IllegalArgumentException("corridor must be axis-aligned");
    }

    private static GridBounds horizontalBounds(GridPoint start, GridPoint end, int width) {
        int minX = Math.min(start.x(), end.x());
        int maxX = Math.max(start.x(), end.x());
        int minZ = start.z() - (width / 2);
        return boundsFromInclusive(minX, minZ, maxX, minZ + width - 1);
    }

    private static GridBounds verticalBounds(GridPoint start, GridPoint end, int width) {
        int minZ = Math.min(start.z(), end.z());
        int maxZ = Math.max(start.z(), end.z());
        int minX = start.x() - (width / 2);
        return boundsFromInclusive(minX, minZ, minX + width - 1, maxZ);
    }

    private static GridBounds boundsFromInclusive(int minX, int minZ, int maxX, int maxZ) {
        return new GridBounds(
                new GridPoint(minX, minZ),
                new GridSize((maxX - minX) + 1, (maxZ - minZ) + 1)
        );
    }
}
