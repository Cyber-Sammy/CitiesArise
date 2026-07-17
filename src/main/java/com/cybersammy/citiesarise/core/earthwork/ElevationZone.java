package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.Objects;

public record ElevationZone(
        PlanElementId sourceElementId,
        ElevationZoneType type,
        GridBounds bounds,
        int targetElevation
) {
    public ElevationZone {
        Objects.requireNonNull(sourceElementId, "sourceElementId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(bounds, "bounds");
    }
}
