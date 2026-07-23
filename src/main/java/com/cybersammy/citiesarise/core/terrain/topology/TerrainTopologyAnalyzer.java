package com.cybersammy.citiesarise.core.terrain.topology;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public final class TerrainTopologyAnalyzer {
    private static final List<GridPoint> NEIGHBOR_OFFSETS = List.of(
            new GridPoint(-1, 0),
            new GridPoint(1, 0),
            new GridPoint(0, -1),
            new GridPoint(0, 1)
    );

    public TerrainTopology analyze(TerrainSurvey survey, TerrainDevelopmentPolicy policy) {
        Objects.requireNonNull(survey, "survey");
        Objects.requireNonNull(policy, "policy");

        Set<GridPoint> developablePoints = developablePoints(survey, policy);
        List<TerrainBarrier> barriers = barriers(survey, developablePoints);
        Map<GridPoint, Integer> regionIdsByPoint = new HashMap<>();
        List<DevelopableRegion> regions = regions(survey.bounds(), developablePoints, regionIdsByPoint);
        return new TerrainTopology(survey.bounds(), regions, barriers, regionIdsByPoint);
    }

    private static Set<GridPoint> developablePoints(
            TerrainSurvey survey,
            TerrainDevelopmentPolicy policy
    ) {
        Set<GridPoint> points = new HashSet<>();
        for (TerrainCell cell : survey.cells()) {
            if (policy.isDevelopable(cell)) {
                points.add(cell.point());
            }
        }
        return Set.copyOf(points);
    }

    private static List<TerrainBarrier> barriers(
            TerrainSurvey survey,
            Set<GridPoint> developablePoints
    ) {
        List<TerrainBarrier> barriers = new ArrayList<>();
        GridBounds bounds = survey.bounds();
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                TerrainCell cell = survey.findCell(new GridPoint(x, z))
                        .orElseThrow(() -> new IllegalStateException("terrain survey is incomplete"));
                if (!developablePoints.contains(cell.point())) {
                    barriers.add(new TerrainBarrier(cell.point(), barrierType(cell)));
                }
            }
        }
        return List.copyOf(barriers);
    }

    private static TerrainBarrierType barrierType(TerrainCell cell) {
        if (cell.water()) {
            return TerrainBarrierType.WATER;
        }
        if (cell.terrainCategory() == TerrainCategory.BLOCKED) {
            return TerrainBarrierType.BLOCKED_TERRAIN;
        }
        return TerrainBarrierType.POLICY_REJECTED;
    }

    private static List<DevelopableRegion> regions(
            GridBounds bounds,
            Set<GridPoint> developablePoints,
            Map<GridPoint, Integer> regionIdsByPoint
    ) {
        List<DevelopableRegion> regions = new ArrayList<>();
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                GridPoint point = new GridPoint(x, z);
                if (isUnassignedDevelopable(point, developablePoints, regionIdsByPoint)) {
                    regions.add(createRegion(regions.size(), point, bounds, developablePoints, regionIdsByPoint));
                }
            }
        }
        return List.copyOf(regions);
    }

    private static boolean isUnassignedDevelopable(
            GridPoint point,
            Set<GridPoint> developablePoints,
            Map<GridPoint, Integer> regionIdsByPoint
    ) {
        if (!developablePoints.contains(point)) {
            return false;
        }
        return !regionIdsByPoint.containsKey(point);
    }

    private static DevelopableRegion createRegion(
            int id,
            GridPoint start,
            GridBounds surveyBounds,
            Set<GridPoint> developablePoints,
            Map<GridPoint, Integer> regionIdsByPoint
    ) {
        Queue<GridPoint> pending = new ArrayDeque<>();
        List<GridPoint> points = new ArrayList<>();
        pending.add(start);
        regionIdsByPoint.put(start, id);

        while (!pending.isEmpty()) {
            GridPoint point = pending.remove();
            points.add(point);
            addNeighbors(point, id, surveyBounds, developablePoints, regionIdsByPoint, pending);
        }
        return new DevelopableRegion(id, points, bounds(points));
    }

    private static void addNeighbors(
            GridPoint point,
            int regionId,
            GridBounds surveyBounds,
            Set<GridPoint> developablePoints,
            Map<GridPoint, Integer> regionIdsByPoint,
            Queue<GridPoint> pending
    ) {
        for (GridPoint offset : NEIGHBOR_OFFSETS) {
            GridPoint neighbor = new GridPoint(point.x() + offset.x(), point.z() + offset.z());
            if (canAssign(neighbor, surveyBounds, developablePoints, regionIdsByPoint)) {
                regionIdsByPoint.put(neighbor, regionId);
                pending.add(neighbor);
            }
        }
    }

    private static boolean canAssign(
            GridPoint point,
            GridBounds surveyBounds,
            Set<GridPoint> developablePoints,
            Map<GridPoint, Integer> regionIdsByPoint
    ) {
        if (!surveyBounds.contains(point)) {
            return false;
        }
        return isUnassignedDevelopable(point, developablePoints, regionIdsByPoint);
    }

    private static GridBounds bounds(List<GridPoint> points) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (GridPoint point : points) {
            minX = Math.min(minX, point.x());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxZ = Math.max(maxZ, point.z());
        }
        return new GridBounds(
                new GridPoint(minX, minZ),
                new GridSize((maxX - minX) + 1, (maxZ - minZ) + 1)
        );
    }
}
