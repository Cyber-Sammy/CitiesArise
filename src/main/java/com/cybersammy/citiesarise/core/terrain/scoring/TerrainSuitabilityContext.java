package com.cybersammy.citiesarise.core.terrain.scoring;

public record TerrainSuitabilityContext(double maxBuildableSlope) {
    public TerrainSuitabilityContext {
        requireFiniteNonNegative(maxBuildableSlope, "maxBuildableSlope");
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
