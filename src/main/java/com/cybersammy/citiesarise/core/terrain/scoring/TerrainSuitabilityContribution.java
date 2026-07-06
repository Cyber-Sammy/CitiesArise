package com.cybersammy.citiesarise.core.terrain.scoring;

import java.util.Objects;
import java.util.Optional;

public record TerrainSuitabilityContribution(
        double scoreMultiplier,
        Optional<TerrainRejectionReason> rejectionReason
) {
    public TerrainSuitabilityContribution {
        requireMultiplierRange(scoreMultiplier);
        Objects.requireNonNull(rejectionReason, "rejectionReason");
    }

    public static TerrainSuitabilityContribution multiplier(double scoreMultiplier) {
        return new TerrainSuitabilityContribution(scoreMultiplier, Optional.empty());
    }

    public static TerrainSuitabilityContribution rejection(TerrainRejectionReason reason) {
        Objects.requireNonNull(reason, "reason");
        return new TerrainSuitabilityContribution(0.0, Optional.of(reason));
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
