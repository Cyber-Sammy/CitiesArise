package com.cybersammy.citiesarise.core.terrain.scoring;

import java.util.Objects;
import java.util.Optional;

public record TerrainSuitabilityStep(
        String ruleName,
        double scoreMultiplier,
        Optional<TerrainRejectionReason> rejectionReason
) {
    public TerrainSuitabilityStep {
        ruleName = requireRuleName(ruleName);
        requireMultiplierRange(scoreMultiplier);
        Objects.requireNonNull(rejectionReason, "rejectionReason");
    }

    private static String requireRuleName(String ruleName) {
        Objects.requireNonNull(ruleName, "ruleName");
        String normalized = ruleName.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("ruleName must not be blank");
        }

        return normalized;
    }

    private static void requireMultiplierRange(double scoreMultiplier) {
        if (!Double.isFinite(scoreMultiplier)) {
            throw new IllegalArgumentException("scoreMultiplier must be finite");
        }

        if (scoreMultiplier < 0.0) {
            throw new IllegalArgumentException("scoreMultiplier must not be below 0.0");
        }

        if (scoreMultiplier > 1.0) {
            throw new IllegalArgumentException("scoreMultiplier must not be above 1.0");
        }
    }
}
