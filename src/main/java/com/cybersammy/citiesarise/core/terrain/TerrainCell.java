package com.cybersammy.citiesarise.core.terrain;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.Objects;

public record TerrainCell(
        GridPoint point,
        int height,
        boolean water,
        double slope,
        BiomeCategory biomeCategory,
        TerrainCategory terrainCategory
) {
    public TerrainCell {
        Objects.requireNonNull(point, "point");
        requireFiniteNonNegativeSlope(slope);
        Objects.requireNonNull(biomeCategory, "biomeCategory");
        Objects.requireNonNull(terrainCategory, "terrainCategory");
    }

    private static void requireFiniteNonNegativeSlope(double slope) {
        if (!Double.isFinite(slope)) {
            throw new IllegalArgumentException("slope must be finite");
        }

        if (slope < 0.0) {
            throw new IllegalArgumentException("slope must not be negative");
        }
    }
}
