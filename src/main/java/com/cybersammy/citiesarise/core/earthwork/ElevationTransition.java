package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.Objects;

public record ElevationTransition(
        ElevationTransitionType type,
        PlanElementId sourceZoneId,
        PlanElementId targetZoneId,
        GridPoint anchor,
        int sourceElevation,
        int targetElevation,
        int minimumRunPerRise
) {
    public ElevationTransition(
            ElevationTransitionType type,
            PlanElementId sourceZoneId,
            PlanElementId targetZoneId,
            GridPoint anchor,
            int sourceElevation,
            int targetElevation
    ) {
        this(type, sourceZoneId, targetZoneId, anchor, sourceElevation, targetElevation, 1);
    }

    public ElevationTransition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sourceZoneId, "sourceZoneId");
        Objects.requireNonNull(targetZoneId, "targetZoneId");
        Objects.requireNonNull(anchor, "anchor");
        if (sourceZoneId.equals(targetZoneId)) {
            throw new IllegalArgumentException("transition must connect different elevation zones");
        }
        if (minimumRunPerRise <= 0) {
            throw new IllegalArgumentException("minimumRunPerRise must be positive");
        }
    }

    public long elevationDelta() {
        return Math.abs((long) targetElevation - sourceElevation);
    }
}
