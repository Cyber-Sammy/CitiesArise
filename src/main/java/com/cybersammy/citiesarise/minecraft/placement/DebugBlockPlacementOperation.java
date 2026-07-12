package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.Objects;
import java.util.OptionalInt;

public record DebugBlockPlacementOperation(
        GridPoint point,
        int verticalOffset,
        DebugPlacementRole role,
        PlanElementId sourceElementId,
        OptionalInt platformY
) {
    public DebugBlockPlacementOperation(
            GridPoint point,
            int verticalOffset,
            DebugPlacementRole role,
            PlanElementId sourceElementId
    ) {
        this(point, verticalOffset, role, sourceElementId, OptionalInt.empty());
    }

    public DebugBlockPlacementOperation {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(sourceElementId, "sourceElementId");
        Objects.requireNonNull(platformY, "platformY");
    }

    public DebugPlacementPosition position() {
        return new DebugPlacementPosition(point, verticalOffset);
    }
}
