package com.cybersammy.citiesarise.core.planning.suburb;

import java.util.Objects;

public record SuburbPlanningSettings(
        int roadWidth,
        double maxBuildableSlope,
        DevelopmentCapacity parcelCapacity,
        int parcelWidth,
        int parcelDepth,
        int buildingMargin,
        int maxElevationRange,
        int preferredMaxCutDepth,
        int preferredMaxFillDepth,
        int maxCutDepth,
        int maxFillDepth,
        int maxBuildingFoundationDepth,
        long maxEarthworkVolume
) {
    public static final int DEFAULT_ROAD_WIDTH = 3;
    public static final double DEFAULT_MAX_BUILDABLE_SLOPE = 0.25;
    public static final int DEFAULT_TARGET_PARCEL_COUNT = 6;
    public static final int DEFAULT_PARCEL_WIDTH = 6;
    public static final int DEFAULT_PARCEL_DEPTH = 7;
    public static final int DEFAULT_BUILDING_MARGIN = 1;
    public static final int DEFAULT_MAX_ELEVATION_RANGE = 12;
    public static final int DEFAULT_PREFERRED_MAX_CUT_DEPTH = 3;
    public static final int DEFAULT_PREFERRED_MAX_FILL_DEPTH = 3;
    public static final int DEFAULT_MAX_CUT_DEPTH = 6;
    public static final int DEFAULT_MAX_FILL_DEPTH = 8;
    public static final int DEFAULT_MAX_BUILDING_FOUNDATION_DEPTH = 4;
    public static final long DEFAULT_MAX_EARTHWORK_VOLUME = 20_000L;

    public SuburbPlanningSettings(int roadWidth, double maxBuildableSlope, int targetParcelCount) {
        this(
                roadWidth,
                maxBuildableSlope,
                DevelopmentCapacity.fixed(targetParcelCount),
                DEFAULT_PARCEL_WIDTH,
                DEFAULT_PARCEL_DEPTH,
                DEFAULT_BUILDING_MARGIN,
                DEFAULT_MAX_ELEVATION_RANGE,
                DEFAULT_PREFERRED_MAX_CUT_DEPTH,
                DEFAULT_PREFERRED_MAX_FILL_DEPTH,
                DEFAULT_MAX_CUT_DEPTH,
                DEFAULT_MAX_FILL_DEPTH,
                DEFAULT_MAX_BUILDING_FOUNDATION_DEPTH,
                DEFAULT_MAX_EARTHWORK_VOLUME
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
                DevelopmentCapacity.fixed(targetParcelCount),
                parcelWidth,
                parcelDepth,
                buildingMargin,
                DEFAULT_MAX_ELEVATION_RANGE,
                DEFAULT_PREFERRED_MAX_CUT_DEPTH,
                DEFAULT_PREFERRED_MAX_FILL_DEPTH,
                DEFAULT_MAX_CUT_DEPTH,
                DEFAULT_MAX_FILL_DEPTH,
                DEFAULT_MAX_BUILDING_FOUNDATION_DEPTH,
                DEFAULT_MAX_EARTHWORK_VOLUME
        );
    }

    public SuburbPlanningSettings(
            int roadWidth,
            double maxBuildableSlope,
            int targetParcelCount,
            int parcelWidth,
            int parcelDepth,
            int buildingMargin,
            int maxElevationRange
    ) {
        this(
                roadWidth,
                maxBuildableSlope,
                DevelopmentCapacity.fixed(targetParcelCount),
                parcelWidth,
                parcelDepth,
                buildingMargin,
                maxElevationRange,
                DEFAULT_PREFERRED_MAX_CUT_DEPTH,
                DEFAULT_PREFERRED_MAX_FILL_DEPTH,
                DEFAULT_MAX_CUT_DEPTH,
                DEFAULT_MAX_FILL_DEPTH,
                DEFAULT_MAX_BUILDING_FOUNDATION_DEPTH,
                DEFAULT_MAX_EARTHWORK_VOLUME
        );
    }

    public SuburbPlanningSettings(
            int roadWidth,
            double maxBuildableSlope,
            int targetParcelCount,
            int parcelWidth,
            int parcelDepth,
            int buildingMargin,
            int maxElevationRange,
            int maxCutDepth,
            int maxFillDepth
    ) {
        this(
                roadWidth,
                maxBuildableSlope,
                DevelopmentCapacity.fixed(targetParcelCount),
                parcelWidth,
                parcelDepth,
                buildingMargin,
                maxElevationRange,
                Math.min(DEFAULT_PREFERRED_MAX_CUT_DEPTH, maxCutDepth),
                Math.min(DEFAULT_PREFERRED_MAX_FILL_DEPTH, maxFillDepth),
                maxCutDepth,
                maxFillDepth,
                Math.min(DEFAULT_MAX_BUILDING_FOUNDATION_DEPTH, maxFillDepth),
                DEFAULT_MAX_EARTHWORK_VOLUME
        );
    }

    public SuburbPlanningSettings(
            int roadWidth,
            double maxBuildableSlope,
            int targetParcelCount,
            int parcelWidth,
            int parcelDepth,
            int buildingMargin,
            int maxElevationRange,
            int maxCutDepth,
            int maxFillDepth,
            long maxEarthworkVolume
    ) {
        this(
                roadWidth,
                maxBuildableSlope,
                DevelopmentCapacity.fixed(targetParcelCount),
                parcelWidth,
                parcelDepth,
                buildingMargin,
                maxElevationRange,
                Math.min(DEFAULT_PREFERRED_MAX_CUT_DEPTH, maxCutDepth),
                Math.min(DEFAULT_PREFERRED_MAX_FILL_DEPTH, maxFillDepth),
                maxCutDepth,
                maxFillDepth,
                Math.min(DEFAULT_MAX_BUILDING_FOUNDATION_DEPTH, maxFillDepth),
                maxEarthworkVolume
        );
    }

    public SuburbPlanningSettings(
            int roadWidth,
            double maxBuildableSlope,
            int targetParcelCount,
            int parcelWidth,
            int parcelDepth,
            int buildingMargin,
            int maxElevationRange,
            int preferredMaxCutDepth,
            int preferredMaxFillDepth,
            int maxCutDepth,
            int maxFillDepth,
            long maxEarthworkVolume
    ) {
        this(
                roadWidth,
                maxBuildableSlope,
                DevelopmentCapacity.fixed(targetParcelCount),
                parcelWidth,
                parcelDepth,
                buildingMargin,
                maxElevationRange,
                preferredMaxCutDepth,
                preferredMaxFillDepth,
                maxCutDepth,
                maxFillDepth,
                Math.min(DEFAULT_MAX_BUILDING_FOUNDATION_DEPTH, maxFillDepth),
                maxEarthworkVolume
        );
    }

    public SuburbPlanningSettings(
            int roadWidth,
            double maxBuildableSlope,
            int targetParcelCount,
            int parcelWidth,
            int parcelDepth,
            int buildingMargin,
            int maxElevationRange,
            int preferredMaxCutDepth,
            int preferredMaxFillDepth,
            int maxCutDepth,
            int maxFillDepth,
            int maxBuildingFoundationDepth,
            long maxEarthworkVolume
    ) {
        this(
                roadWidth,
                maxBuildableSlope,
                DevelopmentCapacity.fixed(targetParcelCount),
                parcelWidth,
                parcelDepth,
                buildingMargin,
                maxElevationRange,
                preferredMaxCutDepth,
                preferredMaxFillDepth,
                maxCutDepth,
                maxFillDepth,
                maxBuildingFoundationDepth,
                maxEarthworkVolume
        );
    }

    public SuburbPlanningSettings {
        requirePositive(roadWidth, "roadWidth");
        requireFiniteNonNegative(maxBuildableSlope, "maxBuildableSlope");
        parcelCapacity = Objects.requireNonNull(parcelCapacity, "parcelCapacity");
        requirePositive(parcelWidth, "parcelWidth");
        requirePositive(parcelDepth, "parcelDepth");
        requireNonNegative(buildingMargin, "buildingMargin");
        requireNonNegative(maxElevationRange, "maxElevationRange");
        requireNonNegative(preferredMaxCutDepth, "preferredMaxCutDepth");
        requireNonNegative(preferredMaxFillDepth, "preferredMaxFillDepth");
        requireNonNegative(maxCutDepth, "maxCutDepth");
        requireNonNegative(maxFillDepth, "maxFillDepth");
        requireNonNegative(maxBuildingFoundationDepth, "maxBuildingFoundationDepth");
        requireNonNegative(maxEarthworkVolume, "maxEarthworkVolume");
        requirePreferredLimitWithinMaximum(preferredMaxCutDepth, maxCutDepth, "preferredMaxCutDepth");
        requirePreferredLimitWithinMaximum(preferredMaxFillDepth, maxFillDepth, "preferredMaxFillDepth");
        requirePreferredLimitWithinMaximum(
                maxBuildingFoundationDepth,
                maxFillDepth,
                "maxBuildingFoundationDepth"
        );
        requireBuildingFitsParcel(parcelWidth, parcelDepth, buildingMargin);
    }

    public static SuburbPlanningSettings defaults() {
        return new SuburbPlanningSettings(
                DEFAULT_ROAD_WIDTH,
                DEFAULT_MAX_BUILDABLE_SLOPE,
                DevelopmentCapacity.fixed(DEFAULT_TARGET_PARCEL_COUNT),
                DEFAULT_PARCEL_WIDTH,
                DEFAULT_PARCEL_DEPTH,
                DEFAULT_BUILDING_MARGIN,
                DEFAULT_MAX_ELEVATION_RANGE,
                DEFAULT_PREFERRED_MAX_CUT_DEPTH,
                DEFAULT_PREFERRED_MAX_FILL_DEPTH,
                DEFAULT_MAX_CUT_DEPTH,
                DEFAULT_MAX_FILL_DEPTH,
                DEFAULT_MAX_BUILDING_FOUNDATION_DEPTH,
                DEFAULT_MAX_EARTHWORK_VOLUME
        );
    }

    public int minimumParcelCount() {
        return parcelCapacity.minimum();
    }

    public int targetParcelCount() {
        return parcelCapacity.target();
    }

    public int maximumParcelCount() {
        return parcelCapacity.maximum();
    }

    /**
     * Retained for settlement profile schema compatibility. Terrain preparation limits now decide acceptance.
     */
    @Deprecated(forRemoval = true)
    public int maxElevationRange() {
        return maxElevationRange;
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

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requirePreferredLimitWithinMaximum(int preferred, int maximum, String name) {
        if (preferred <= maximum) {
            return;
        }

        throw new IllegalArgumentException(name + " must not exceed its absolute maximum");
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
