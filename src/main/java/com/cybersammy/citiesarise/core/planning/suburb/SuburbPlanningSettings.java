package com.cybersammy.citiesarise.core.planning.suburb;

public record SuburbPlanningSettings(
        int roadWidth,
        double maxBuildableSlope,
        int targetParcelCount
) {
    public SuburbPlanningSettings {
        requirePositive(roadWidth, "roadWidth");
        requireFiniteNonNegative(maxBuildableSlope, "maxBuildableSlope");
        requirePositive(targetParcelCount, "targetParcelCount");
    }

    public static SuburbPlanningSettings defaults() {
        return new SuburbPlanningSettings(3, 0.25, 6);
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
