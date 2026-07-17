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
        int targetElevation
) {
    public ElevationTransition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sourceZoneId, "sourceZoneId");
        Objects.requireNonNull(targetZoneId, "targetZoneId");
        Objects.requireNonNull(anchor, "anchor");
        if (sourceZoneId.equals(targetZoneId)) {
            throw new IllegalArgumentException("transition must connect different elevation zones");
        }
    }

    public long elevationDelta() {
        return Math.abs((long) targetElevation - sourceElevation);
    }
}
