package com.cybersammy.citiesarise.core.terrain.topology;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.Objects;

public record TerrainBarrier(GridPoint point, TerrainBarrierType type) {
    public TerrainBarrier {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(type, "type");
    }
}
