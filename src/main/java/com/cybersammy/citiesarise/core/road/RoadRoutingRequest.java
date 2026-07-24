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
        GridPoint start,
        GridPoint destination,
        int roadWidth,
        int supportRadius,
        double maxBuildableSlope,
        TerrainResponsePolicy terrainResponsePolicy,
        RoadRoutingCostPolicy costPolicy,
        List<GridBounds> reservedBounds
) {
    public RoadRoutingRequest {
        Objects.requireNonNull(survey, "survey");
        Objects.requireNonNull(routingBounds, "routingBounds");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(destination, "destination");
        requirePositive(roadWidth, "roadWidth");
        requireNonNegative(supportRadius, "supportRadius");
        requireFiniteNonNegative(maxBuildableSlope, "maxBuildableSlope");
        Objects.requireNonNull(terrainResponsePolicy, "terrainResponsePolicy");
        Objects.requireNonNull(costPolicy, "costPolicy");
        Objects.requireNonNull(reservedBounds, "reservedBounds");
        reservedBounds = List.copyOf(reservedBounds);
        requireContainedBySurvey(routingBounds, survey.bounds());
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

    private static void requireContainedBySurvey(GridBounds routingBounds, GridBounds surveyBounds) {
        if (!surveyBounds.contains(routingBounds)) {
            throw new IllegalArgumentException("routingBounds must be inside survey bounds");
        }
    }

    private static void requireContainedPoint(GridPoint point, GridBounds bounds, String name) {
        if (!bounds.contains(point)) {
            throw new IllegalArgumentException(name + " must be inside routingBounds");
        }
    }
}
