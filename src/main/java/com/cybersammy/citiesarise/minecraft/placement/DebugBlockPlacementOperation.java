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
        OptionalInt platformY,
        String material,
        int rotation,
        String fillMaterial
) {
    public DebugBlockPlacementOperation(GridPoint point,int verticalOffset,DebugPlacementRole role,
            PlanElementId sourceElementId,OptionalInt platformY,String material,int rotation) {
        this(point,verticalOffset,role,sourceElementId,platformY,material,rotation,"");
    }
    public DebugBlockPlacementOperation(
            GridPoint point,
            int verticalOffset,
            DebugPlacementRole role,
            PlanElementId sourceElementId
    ) {
        this(point, verticalOffset, role, sourceElementId, OptionalInt.empty());
    }

    public DebugBlockPlacementOperation(GridPoint point, int verticalOffset, DebugPlacementRole role,
            PlanElementId sourceElementId, OptionalInt platformY) {
        this(point,verticalOffset,role,sourceElementId,platformY,"",0);
    }
    public DebugBlockPlacementOperation {
        Objects.requireNonNull(material,"material");
        Objects.requireNonNull(fillMaterial,"fillMaterial");
        if(fillMaterial.length()>512 || fillMaterial.contains("\n") || fillMaterial.contains("\r")) throw new IllegalArgumentException("Invalid fill material");
        if(rotation<0 || rotation>3 || material.length()>512) throw new IllegalArgumentException("Invalid material/rotation");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(sourceElementId, "sourceElementId");
        Objects.requireNonNull(platformY, "platformY");
    }

    public DebugPlacementPosition position() {
        return new DebugPlacementPosition(point, verticalOffset);
    }
}
