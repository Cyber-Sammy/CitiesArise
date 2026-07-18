package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.Objects;

public record TerrainPreparationLimitDiagnostic(
        PlanElementId sourceElementId,
        long actualValue,
        long preferredLimit,
        long maximumLimit
) {
    public TerrainPreparationLimitDiagnostic {
        Objects.requireNonNull(sourceElementId, "sourceElementId");
        requireNonNegative(actualValue, "actualValue");
        requireNonNegative(preferredLimit, "preferredLimit");
        requireNonNegative(maximumLimit, "maximumLimit");

        if (preferredLimit > maximumLimit) {
            throw new IllegalArgumentException("preferredLimit must not exceed maximumLimit");
        }
    }

    public long excessOverMaximum() {
        return Math.max(0L, actualValue - maximumLimit);
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
