package com.cybersammy.citiesarise.core.earthwork;

/**
 * Profile-owned limits for local elevation transitions and platform support.
 */
public record TerrainTransitionSettings(
        int buildingAccessRunPerRise,
        int roadShoulderRadius,
        int roadShoulderMaxFillDepth,
        int parcelShoulderRadius,
        int parcelShoulderMaxFillDepth,
        int buildingShoulderRadius,
        int buildingShoulderMaxFillDepth,
        boolean retainingWalls,
        int retainingWallMinimumHeight
) {
    public static final int MAX_SUPPORT_RADIUS = 8;
    // Worldgen's late support guard currently permits at most three blocks of shoulder fill.
    public static final int MAX_SUPPORT_FILL_DEPTH = 3;
    public static final int MAX_ACCESS_RUN_PER_RISE = 8;

    public TerrainTransitionSettings {
        requireRange(buildingAccessRunPerRise, 1, MAX_ACCESS_RUN_PER_RISE, "buildingAccessRunPerRise");
        requireRange(roadShoulderRadius, 0, MAX_SUPPORT_RADIUS, "roadShoulderRadius");
        requireRange(roadShoulderMaxFillDepth, 0, MAX_SUPPORT_FILL_DEPTH, "roadShoulderMaxFillDepth");
        requireRange(parcelShoulderRadius, 0, MAX_SUPPORT_RADIUS, "parcelShoulderRadius");
        requireRange(parcelShoulderMaxFillDepth, 0, MAX_SUPPORT_FILL_DEPTH, "parcelShoulderMaxFillDepth");
        requireRange(buildingShoulderRadius, 0, MAX_SUPPORT_RADIUS, "buildingShoulderRadius");
        requireRange(buildingShoulderMaxFillDepth, 0, MAX_SUPPORT_FILL_DEPTH, "buildingShoulderMaxFillDepth");
        requireRange(retainingWallMinimumHeight, 1, MAX_SUPPORT_FILL_DEPTH, "retainingWallMinimumHeight");
    }

    public static TerrainTransitionSettings defaults() {
        return new TerrainTransitionSettings(
                1,
                RoadTerrainShoulderPolicy.RADIUS,
                RoadTerrainShoulderPolicy.MAX_FILL_DEPTH,
                ParcelTerrainShoulderPolicy.RADIUS,
                ParcelTerrainShoulderPolicy.MAX_FILL_DEPTH,
                BuildingTerrainShoulderPolicy.RADIUS,
                BuildingTerrainShoulderPolicy.MAX_FILL_DEPTH,
                false,
                2
        );
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
    }
}
