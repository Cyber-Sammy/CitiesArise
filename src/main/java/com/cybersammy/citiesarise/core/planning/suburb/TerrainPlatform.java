package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanPropertyKeys;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import java.util.Arrays;

final class TerrainPlatform {
    private TerrainPlatform() {
    }

    static int medianElevation(SuburbPlanningRequest request, GridBounds bounds) {
        int cellCount = Math.multiplyExact(bounds.size().width(), bounds.size().depth());
        int[] heights = new int[cellCount];
        int index = 0;

        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                heights[index] = requiredTerrainCell(request, new GridPoint(x, z)).height() - 1;
                index++;
            }
        }

        Arrays.sort(heights);
        return heights[heights.length / 2];
    }

    static int highestElevation(SuburbPlanningRequest request, GridBounds bounds) {
        int highest = Integer.MIN_VALUE;
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                TerrainCell cell = requiredTerrainCell(request, new GridPoint(x, z));
                highest = Math.max(highest, cell.height() - 1);
            }
        }
        return highest;
    }

    static PlanProperties withElevation(PlanProperties properties, int elevation) {
        return properties.with(PlanPropertyKeys.PLATFORM_Y, Integer.toString(elevation));
    }

    static int requiredElevation(PlanProperties properties) {
        String value = properties.find(PlanPropertyKeys.PLATFORM_Y)
                .orElseThrow(() -> new IllegalStateException("platform_y is required"));
        return Integer.parseInt(value);
    }

    static TerrainCell requiredTerrainCell(SuburbPlanningRequest request, GridPoint point) {
        return request.survey()
                .findCell(point)
                .orElseThrow(() -> new IllegalStateException("planned footprint is outside terrain survey: " + point));
    }
}
