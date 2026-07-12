package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.Objects;

public record TerrainPreparationArea(
        PlanElementId sourceElementId,
        GridBounds bounds,
        int targetElevation,
        long cutVolume,
        long fillVolume
) {
    public TerrainPreparationArea {
        Objects.requireNonNull(sourceElementId, "sourceElementId");
        Objects.requireNonNull(bounds, "bounds");
        requireNonNegative(cutVolume, "cutVolume");
        requireNonNegative(fillVolume, "fillVolume");
    }

    public long totalVolume() {
        return Math.addExact(cutVolume, fillVolume);
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
