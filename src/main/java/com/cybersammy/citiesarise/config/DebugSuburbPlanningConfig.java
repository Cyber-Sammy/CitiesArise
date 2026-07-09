package com.cybersammy.citiesarise.config;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;

public record DebugSuburbPlanningConfig(
        int surveyWidth,
        int surveyDepth,
        int roadWidth,
        double maxBuildableSlope,
        int targetParcelCount,
        int parcelWidth,
        int parcelDepth,
        int buildingMargin
) {
    public static final int DEFAULT_SURVEY_WIDTH = 120;
    public static final int DEFAULT_SURVEY_DEPTH = 72;
    public static final int DEFAULT_ROAD_WIDTH = 5;
    public static final double DEFAULT_MAX_BUILDABLE_SLOPE = 0.75;
    public static final int DEFAULT_TARGET_PARCEL_COUNT = 8;
    public static final int DEFAULT_PARCEL_WIDTH = 18;
    public static final int DEFAULT_PARCEL_DEPTH = 20;
    public static final int DEFAULT_BUILDING_MARGIN = 4;
    public static final String DEFAULT_SETTLEMENT_PROFILE_ID = "cities_arise:suburb";

    public DebugSuburbPlanningConfig {
        requirePositive(surveyWidth, "surveyWidth");
        requirePositive(surveyDepth, "surveyDepth");
        requirePositive(roadWidth, "roadWidth");
        requireFiniteNonNegative(maxBuildableSlope, "maxBuildableSlope");
        requirePositive(targetParcelCount, "targetParcelCount");
        requirePositive(parcelWidth, "parcelWidth");
        requirePositive(parcelDepth, "parcelDepth");
        requireNonNegative(buildingMargin, "buildingMargin");
        requireBuildingFitsParcel(parcelWidth, parcelDepth, buildingMargin);
    }

    public static DebugSuburbPlanningConfig defaults() {
        return new DebugSuburbPlanningConfig(
                DEFAULT_SURVEY_WIDTH,
                DEFAULT_SURVEY_DEPTH,
                DEFAULT_ROAD_WIDTH,
                DEFAULT_MAX_BUILDABLE_SLOPE,
                DEFAULT_TARGET_PARCEL_COUNT,
                DEFAULT_PARCEL_WIDTH,
                DEFAULT_PARCEL_DEPTH,
                DEFAULT_BUILDING_MARGIN
        );
    }

    public GridSize toSurveySize() {
        return new GridSize(surveyWidth, surveyDepth);
    }

    public SuburbPlanningSettings toSuburbPlanningSettings() {
        return new SuburbPlanningSettings(
                roadWidth,
                maxBuildableSlope,
                targetParcelCount,
                parcelWidth,
                parcelDepth,
                buildingMargin
        );
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
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requireBuildingFitsParcel(int parcelWidth, int parcelDepth, int buildingMargin) {
        if (buildingSize(parcelWidth, buildingMargin) <= 0) {
            throw new IllegalArgumentException("building width must be positive");
        }

        if (buildingSize(parcelDepth, buildingMargin) <= 0) {
            throw new IllegalArgumentException("building depth must be positive");
        }
    }

    private static int buildingSize(int parcelSize, int buildingMargin) {
        return parcelSize - (buildingMargin * 2);
    }
}
