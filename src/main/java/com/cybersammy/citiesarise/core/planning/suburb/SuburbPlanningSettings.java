package com.cybersammy.citiesarise.core.planning.suburb;

public record SuburbPlanningSettings(
        int roadWidth,
        double maxBuildableSlope,
        int targetParcelCount,
        int parcelWidth,
        int parcelDepth,
        int buildingMargin,
        int maxElevationRange
) {
    public static final int DEFAULT_ROAD_WIDTH = 3;
    public static final double DEFAULT_MAX_BUILDABLE_SLOPE = 0.25;
    public static final int DEFAULT_TARGET_PARCEL_COUNT = 6;
    public static final int DEFAULT_PARCEL_WIDTH = 6;
    public static final int DEFAULT_PARCEL_DEPTH = 7;
    public static final int DEFAULT_BUILDING_MARGIN = 1;
    public static final int DEFAULT_MAX_ELEVATION_RANGE = 12;

    public SuburbPlanningSettings(int roadWidth, double maxBuildableSlope, int targetParcelCount) {
        this(
                roadWidth,
                maxBuildableSlope,
                targetParcelCount,
                DEFAULT_PARCEL_WIDTH,
                DEFAULT_PARCEL_DEPTH,
                DEFAULT_BUILDING_MARGIN,
                DEFAULT_MAX_ELEVATION_RANGE
        );
    }

    public SuburbPlanningSettings(
            int roadWidth,
            double maxBuildableSlope,
            int targetParcelCount,
            int parcelWidth,
            int parcelDepth,
            int buildingMargin
    ) {
        this(
                roadWidth,
                maxBuildableSlope,
                targetParcelCount,
                parcelWidth,
                parcelDepth,
                buildingMargin,
                DEFAULT_MAX_ELEVATION_RANGE
        );
    }

    public SuburbPlanningSettings {
        requirePositive(roadWidth, "roadWidth");
        requireFiniteNonNegative(maxBuildableSlope, "maxBuildableSlope");
        requirePositive(targetParcelCount, "targetParcelCount");
        requirePositive(parcelWidth, "parcelWidth");
        requirePositive(parcelDepth, "parcelDepth");
        requireNonNegative(buildingMargin, "buildingMargin");
        requireNonNegative(maxElevationRange, "maxElevationRange");
        requireBuildingFitsParcel(parcelWidth, parcelDepth, buildingMargin);
    }

    public static SuburbPlanningSettings defaults() {
        return new SuburbPlanningSettings(
                DEFAULT_ROAD_WIDTH,
                DEFAULT_MAX_BUILDABLE_SLOPE,
                DEFAULT_TARGET_PARCEL_COUNT,
                DEFAULT_PARCEL_WIDTH,
                DEFAULT_PARCEL_DEPTH,
                DEFAULT_BUILDING_MARGIN,
                DEFAULT_MAX_ELEVATION_RANGE
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
