package com.cybersammy.citiesarise.core.terrain.policy;

public record TerrainFeatureMetrics(
        int area,
        int relief,
        long estimatedEarthworkVolume
) {
    public TerrainFeatureMetrics {
        if (area <= 0) {
            throw new IllegalArgumentException("area must be positive");
        }
        if (relief < 0) {
            throw new IllegalArgumentException("relief must not be negative");
        }
        if (estimatedEarthworkVolume < 0L) {
            throw new IllegalArgumentException("estimatedEarthworkVolume must not be negative");
        }
    }
}
