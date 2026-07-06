package com.cybersammy.citiesarise.core.geometry;

import java.util.Objects;

public record GridBounds(GridPoint origin, GridSize size) {
    public GridBounds {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(size, "size");
        validateCoordinateRange(origin, size);
    }

    public int minX() {
        return origin.x();
    }

    public int minZ() {
        return origin.z();
    }

    public int maxXExclusive() {
        return Math.addExact(origin.x(), size.width());
    }

    public int maxZExclusive() {
        return Math.addExact(origin.z(), size.depth());
    }

    private static void validateCoordinateRange(GridPoint origin, GridSize size) {
        requireValidExclusiveMax(origin.x(), size.width(), "x");
        requireValidExclusiveMax(origin.z(), size.depth(), "z");
    }

    private static void requireValidExclusiveMax(int originCoordinate, int size, String axisName) {
        try {
            Math.addExact(originCoordinate, size);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(axisName + " bounds exceed integer coordinate range", exception);
        }
    }

    public boolean contains(GridPoint point) {
        Objects.requireNonNull(point, "point");

        if (point.x() < minX()) {
            return false;
        }

        if (point.z() < minZ()) {
            return false;
        }

        if (point.x() >= maxXExclusive()) {
            return false;
        }

        return point.z() < maxZExclusive();
    }

    public boolean contains(GridBounds other) {
        Objects.requireNonNull(other, "other");

        if (other.minX() < minX()) {
            return false;
        }

        if (other.minZ() < minZ()) {
            return false;
        }

        if (other.maxXExclusive() > maxXExclusive()) {
            return false;
        }

        return other.maxZExclusive() <= maxZExclusive();
    }

    public boolean intersects(GridBounds other) {
        Objects.requireNonNull(other, "other");

        if (other.maxXExclusive() <= minX()) {
            return false;
        }

        if (other.minX() >= maxXExclusive()) {
            return false;
        }

        if (other.maxZExclusive() <= minZ()) {
            return false;
        }

        return other.minZ() < maxZExclusive();
    }
}
