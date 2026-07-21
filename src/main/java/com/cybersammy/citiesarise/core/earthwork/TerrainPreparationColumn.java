package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.Objects;

public record TerrainPreparationColumn(
        GridPoint point,
        PlanElementId sourceElementId,
        int targetElevation,
        int cutDepth,
        int fillDepth,
        TerrainPreparationColumnType type
) {
    public TerrainPreparationColumn(
            GridPoint point,
            PlanElementId sourceElementId,
            int targetElevation,
            int cutDepth,
            int fillDepth
    ) {
        this(point, sourceElementId, targetElevation, cutDepth, fillDepth, TerrainPreparationColumnType.PLATFORM);
    }

    public TerrainPreparationColumn {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(sourceElementId, "sourceElementId");
        Objects.requireNonNull(type, "type");
        requireNonNegative(cutDepth, "cutDepth");
        requireNonNegative(fillDepth, "fillDepth");
        if (cutDepth > 0 && fillDepth > 0) {
            throw new IllegalArgumentException("column must not cut and fill simultaneously");
        }
    }

    public int totalVolume() {
        return Math.addExact(cutDepth, fillDepth);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
