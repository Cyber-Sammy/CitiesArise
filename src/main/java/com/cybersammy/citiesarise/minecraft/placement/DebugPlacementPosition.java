package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.Objects;

public record DebugPlacementPosition(GridPoint point, int verticalOffset) {
    public DebugPlacementPosition {
        Objects.requireNonNull(point, "point");
    }
}
