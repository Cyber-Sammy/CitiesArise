package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.terrain.policy.TerrainFeatureType;
import java.util.Objects;
import java.util.Optional;

record TerrainFootprintEvaluation(
        boolean traversable,
        double maximumSlope,
        int roughCells,
        Optional<TerrainFeatureType> crossingFeature
) {
    TerrainFootprintEvaluation {
        if (!Double.isFinite(maximumSlope) || maximumSlope < 0.0) {
            throw new IllegalArgumentException("maximumSlope must be finite and non-negative");
        }
        if (roughCells < 0) {
            throw new IllegalArgumentException("roughCells must not be negative");
        }
        Objects.requireNonNull(crossingFeature, "crossingFeature");
    }

    static TerrainFootprintEvaluation blocked() {
        return new TerrainFootprintEvaluation(false, 0.0, 0, Optional.empty());
    }

    static TerrainFootprintEvaluation traversable(
            double maximumSlope,
            int roughCells,
            TerrainFeatureType crossingFeature
    ) {
        return new TerrainFootprintEvaluation(
                true,
                maximumSlope,
                roughCells,
                Optional.ofNullable(crossingFeature)
        );
    }
}
