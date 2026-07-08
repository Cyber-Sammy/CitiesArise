package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.Objects;

public record DebugBlockPlacementOperation(
        GridPoint point,
        int verticalOffset,
        DebugPlacementRole role,
        PlanElementId sourceElementId
) {
    public DebugBlockPlacementOperation {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(sourceElementId, "sourceElementId");
    }

    public DebugPlacementPosition position() {
        return new DebugPlacementPosition(point, verticalOffset);
    }
}
