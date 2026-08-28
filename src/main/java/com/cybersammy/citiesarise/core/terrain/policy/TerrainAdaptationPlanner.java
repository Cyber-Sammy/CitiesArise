package com.cybersammy.citiesarise.core.terrain.policy;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class TerrainAdaptationPlanner {
    private static final List<GridPoint> NEIGHBOR_OFFSETS = List.of(
            new GridPoint(-1, 0),
            new GridPoint(0, -1),
            new GridPoint(1, 0),
            new GridPoint(0, 1)
    );
    private static final Comparator<GridPoint> POINT_ORDER = Comparator
            .comparingInt(GridPoint::z)
            .thenComparingInt(GridPoint::x);

    public TerrainAdaptationPlan plan(
            TerrainSurvey survey,
            double maxBuildableSlope,
            TerrainResponsePolicy policy
    ) {
        Objects.requireNonNull(survey, "survey");
        if (!Double.isFinite(maxBuildableSlope) || maxBuildableSlope < 0.0) {
            throw new IllegalArgumentException("maxBuildableSlope must be finite and non-negative");
        }
        Objects.requireNonNull(policy, "policy");

        Map<GridPoint, TerrainFeatureType> featureTypes = featureTypes(survey, maxBuildableSlope);
        Set<GridPoint> visited = new HashSet<>();
        List<TerrainFeatureRegion> features = new ArrayList<>();
        List<GridPoint> starts = featureTypes.keySet().stream().sorted(POINT_ORDER).toList();
        for (GridPoint start : starts) {
            if (visited.contains(start)) {
                continue;
            }
            TerrainFeatureType type = featureTypes.get(start);
            List<GridPoint> points = connectedPoints(start, type, featureTypes, visited);
            TerrainFeatureMetrics metrics = metrics(survey, points);
            features.add(new TerrainFeatureRegion(
                    features.size(),
                    type,
                    points,
                    bounds(points),
                    metrics,
                    resolveAction(policy, type, metrics)
            ));
        }
        return new TerrainAdaptationPlan(policy, features);
    }

    private static Map<GridPoint, TerrainFeatureType> featureTypes(
            TerrainSurvey survey,
            double maxBuildableSlope
    ) {
        Map<GridPoint, TerrainFeatureType> types = new HashMap<>();
        for (TerrainCell cell : survey.cells()) {
            featureType(cell, maxBuildableSlope).ifPresent(type -> types.put(cell.point(), type));
        }
        return Map.copyOf(types);
    }

    private static Optional<TerrainFeatureType> featureType(
            TerrainCell cell,
            double maxBuildableSlope
    ) {
        if (cell.water()) {
            return Optional.of(TerrainFeatureType.WATER);
        }
        if (cell.terrainCategory() == TerrainCategory.BLOCKED) {
            return Optional.of(TerrainFeatureType.BLOCKED_TERRAIN);
        }
        if (cell.slope() > maxBuildableSlope) {
            return Optional.of(TerrainFeatureType.STEEP_SLOPE);
        }
        return Optional.empty();
    }

    private static List<GridPoint> connectedPoints(
            GridPoint start,
            TerrainFeatureType type,
            Map<GridPoint, TerrainFeatureType> featureTypes,
            Set<GridPoint> visited
    ) {
        ArrayDeque<GridPoint> pending = new ArrayDeque<>();
        List<GridPoint> points = new ArrayList<>();
        pending.add(start);
        visited.add(start);
        while (!pending.isEmpty()) {
            GridPoint point = pending.removeFirst();
            points.add(point);
            for (GridPoint offset : NEIGHBOR_OFFSETS) {
                GridPoint neighbor = new GridPoint(point.x() + offset.x(), point.z() + offset.z());
                if (featureTypes.get(neighbor) != type || !visited.add(neighbor)) {
                    continue;
                }
                pending.addLast(neighbor);
            }
        }
        points.sort(POINT_ORDER);
        return List.copyOf(points);
    }

    private static TerrainFeatureMetrics metrics(TerrainSurvey survey, List<GridPoint> points) {
        List<Integer> elevations = points.stream()
                .map(point -> requiredCell(survey, point).height() - 1)
                .sorted()
                .toList();
        int minimum = elevations.getFirst();
        int maximum = elevations.getLast();
        int median = elevations.get((elevations.size() - 1) / 2);
        long volume = 0L;
        for (int elevation : elevations) {
            volume = Math.addExact(volume, Math.abs((long) elevation - median));
        }
        return new TerrainFeatureMetrics(points.size(), maximum - minimum, volume);
    }

    private static TerrainCell requiredCell(TerrainSurvey survey, GridPoint point) {
        return survey.findCell(point).orElseThrow();
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
                new GridSize(Math.addExact(Math.subtractExact(maxX, minX), 1),
                        Math.addExact(Math.subtractExact(maxZ, minZ), 1))
        );
    }

    static TerrainPlanningAction resolveAction(
            TerrainResponsePolicy policy,
            TerrainFeatureType type,
            TerrainFeatureMetrics metrics
    ) {
        TerrainPlanningAction configured = policy.actionFor(type);
        if (configured != TerrainPlanningAction.ROUTE_AROUND) {
            return configured;
        }
        if (policy.adaptationSettings().permits(metrics)) {
            return TerrainPlanningAction.DIRECT_TERRAFORMING;
        }
        return TerrainPlanningAction.ROUTE_AROUND;
    }
}
