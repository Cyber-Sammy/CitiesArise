package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.Arrays;
import java.util.function.ToIntFunction;

/** Chooses a pad with minimum cut/fill volume inside the feasible elevation interval. */
final class ParcelPlatformElevation {
    private ParcelPlatformElevation() { }

    static int choose(GridBounds bounds, ToIntFunction<GridPoint> ground,
            SuburbPlanningSettings settings, int minimumAccessY, int maximumAccessY) {
        int[] heights = new int[Math.multiplyExact(bounds.size().width(), bounds.size().depth())];
        int index = 0;
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                heights[index++] = ground.applyAsInt(new GridPoint(x, z));
            }
        }
        Arrays.sort(heights);
        int minimum = Math.max(minimumAccessY, heights[heights.length - 1] - settings.maxCutDepth());
        int maximum = Math.min(maximumAccessY, heights[0]
                + Math.min(settings.maxFillDepth(), settings.maxBuildingFoundationDepth()));
        int median = heights[(heights.length - 1) / 2];
        // An infeasible interval remains subject to the existing depth/access diagnostics.
        return minimum <= maximum ? Math.clamp(median, minimum, maximum) : median;
    }
}
