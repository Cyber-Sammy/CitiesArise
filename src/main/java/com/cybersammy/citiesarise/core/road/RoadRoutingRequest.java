package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy;
import java.util.List;
import java.util.Objects;

public record RoadRoutingRequest(
        TerrainSurvey survey,
        GridBounds routingBounds,
        GridBounds terrainCheckBounds,
        GridPoint start,
        GridPoint destination,
        int roadWidth,
        int supportRadius,
        double maxBuildableSlope,
        TerrainResponsePolicy terrainResponsePolicy,
        RoadRoutingCostPolicy costPolicy,
        List<GridBounds> reservedBounds,
        List<GridBounds> allowedReservedOverlapBounds
) {
    public RoadRoutingRequest {
        Objects.requireNonNull(survey, "survey");
        Objects.requireNonNull(routingBounds, "routingBounds");
        Objects.requireNonNull(terrainCheckBounds, "terrainCheckBounds");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(destination, "destination");
        requirePositive(roadWidth, "roadWidth");
        requireNonNegative(supportRadius, "supportRadius");
        requireFiniteNonNegative(maxBuildableSlope, "maxBuildableSlope");
        Objects.requireNonNull(terrainResponsePolicy, "terrainResponsePolicy");
        Objects.requireNonNull(costPolicy, "costPolicy");
        Objects.requireNonNull(reservedBounds, "reservedBounds");
        reservedBounds = List.copyOf(reservedBounds);
        Objects.requireNonNull(allowedReservedOverlapBounds, "allowedReservedOverlapBounds");
        allowedReservedOverlapBounds = List.copyOf(allowedReservedOverlapBounds);
        requireContainedBySurvey(terrainCheckBounds, survey.bounds(), "terrainCheckBounds");
        requireContainedBySurvey(routingBounds, terrainCheckBounds, "routingBounds");
        requireContainedPoint(start, routingBounds, "start");
        requireContainedPoint(destination, routingBounds, "destination");
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requireContainedBySurvey(GridBounds bounds, GridBounds limit, String name) {
        if (!limit.contains(bounds)) {
            throw new IllegalArgumentException(name + " must be inside its containing bounds");
        }
    }

    private static void requireContainedPoint(GridPoint point, GridBounds bounds, String name) {
        if (!bounds.contains(point)) {
            throw new IllegalArgumentException(name + " must be inside routingBounds");
        }
    }
}
