package com.cybersammy.citiesarise.core.terrain.scoring;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record TerrainSuitability(
        double score,
        Set<TerrainRejectionReason> rejectionReasons,
        List<TerrainSuitabilityStep> steps
) {
    public TerrainSuitability {
        requireScoreRange(score);
        Objects.requireNonNull(rejectionReasons, "rejectionReasons");
        Objects.requireNonNull(steps, "steps");
        rejectionReasons = Set.copyOf(rejectionReasons);
        steps = List.copyOf(steps);
    }

    public boolean rejected() {
        return !rejectionReasons.isEmpty();
    }

    private static void requireScoreRange(double score) {
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }

        if (score < 0.0) {
            throw new IllegalArgumentException("score must not be below 0.0");
        }

        if (score > 1.0) {
            throw new IllegalArgumentException("score must not be above 1.0");
        }
    }
}
