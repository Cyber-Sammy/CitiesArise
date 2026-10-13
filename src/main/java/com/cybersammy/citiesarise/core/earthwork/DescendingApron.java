package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.function.ToIntFunction;

/** An added bank must not rise beyond a lower, unfilled strip on its way to the platform. */
public final class DescendingApron {
    private DescendingApron() { }

    public static int distance(GridBounds bounds, GridPoint point) {
        return Math.max(Math.abs(point.x() - Math.clamp(point.x(), bounds.minX(), bounds.maxXExclusive() - 1)),
                Math.abs(point.z() - Math.clamp(point.z(), bounds.minZ(), bounds.maxZExclusive() - 1)));
    }

    public static boolean connected(GridBounds bounds, GridPoint point, int targetY,
            ToIntFunction<GridPoint> surfaceY) {
        int edgeX = Math.clamp(point.x(), bounds.minX(), bounds.maxXExclusive() - 1);
        int edgeZ = Math.clamp(point.z(), bounds.minZ(), bounds.maxZExclusive() - 1);
        int x = point.x(), z = point.z();
        while (x != edgeX || z != edgeZ) {
            x += Integer.compare(edgeX, x);
            z += Integer.compare(edgeZ, z);
            if (surfaceY.applyAsInt(new GridPoint(x, z)) < targetY) return false;
        }
        return true;
    }
}
