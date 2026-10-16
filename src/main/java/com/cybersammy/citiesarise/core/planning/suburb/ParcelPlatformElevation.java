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
        return choose(bounds,ground,settings,minimumAccessY,maximumAccessY,null);
    }
    static int choose(GridBounds bounds, ToIntFunction<GridPoint> ground,
            SuburbPlanningSettings settings, int minimumAccessY, int maximumAccessY,Integer target) {
        var interval=interval(bounds,ground,settings,minimumAccessY,maximumAccessY,target!=null);
        if(interval.minimum()>interval.maximum())return interval.preferred();
        return Math.clamp(target==null?interval.preferred():target,interval.minimum(),interval.maximum());
    }
    static NeighborhoodLevels.Interval interval(GridBounds bounds,ToIntFunction<GridPoint> ground,
            SuburbPlanningSettings settings,int minimumAccessY,int maximumAccessY,boolean preferredDepth) {
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
        if(preferredDepth && minimum<=maximum) {
            int depth=settings.districts().elevation().preferredDepth();
            int low=Math.max(minimum,heights[heights.length-1]-depth),high=Math.min(maximum,heights[0]+depth);
            return new NeighborhoodLevels.Interval(low<=high?low:minimum,low<=high?high:maximum,median);
        }
        // An infeasible interval remains subject to the existing depth/access diagnostics.
        return new NeighborhoodLevels.Interval(minimum,maximum,median);
    }
}
