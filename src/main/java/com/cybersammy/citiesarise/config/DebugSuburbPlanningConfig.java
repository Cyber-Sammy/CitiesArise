package com.cybersammy.citiesarise.config;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;

public record DebugSuburbPlanningConfig(
        int surveyWidth,
        int surveyDepth,
        int roadWidth,
        double maxBuildableSlope,
        int targetParcelCount
) {
    public static final int DEFAULT_SURVEY_WIDTH = 40;
    public static final int DEFAULT_SURVEY_DEPTH = 30;
    public static final int DEFAULT_ROAD_WIDTH = 3;
    public static final double DEFAULT_MAX_BUILDABLE_SLOPE = 0.75;
    public static final int DEFAULT_TARGET_PARCEL_COUNT = 6;

    public DebugSuburbPlanningConfig {
        requirePositive(surveyWidth, "surveyWidth");
        requirePositive(surveyDepth, "surveyDepth");
        requirePositive(roadWidth, "roadWidth");
        requireFiniteNonNegative(maxBuildableSlope, "maxBuildableSlope");
        requirePositive(targetParcelCount, "targetParcelCount");
    }

    public static DebugSuburbPlanningConfig defaults() {
        return new DebugSuburbPlanningConfig(
                DEFAULT_SURVEY_WIDTH,
                DEFAULT_SURVEY_DEPTH,
                DEFAULT_ROAD_WIDTH,
                DEFAULT_MAX_BUILDABLE_SLOPE,
                DEFAULT_TARGET_PARCEL_COUNT
        );
    }

    public GridSize toSurveySize() {
        return new GridSize(surveyWidth, surveyDepth);
    }

    public SuburbPlanningSettings toSuburbPlanningSettings() {
        return new SuburbPlanningSettings(roadWidth, maxBuildableSlope, targetParcelCount);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
