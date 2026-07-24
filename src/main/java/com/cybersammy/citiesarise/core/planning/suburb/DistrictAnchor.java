package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.Objects;

public record DistrictAnchor(int developableRegionId, GridPoint point) {
    public DistrictAnchor {
        if (developableRegionId < 0) {
            throw new IllegalArgumentException("developableRegionId must not be negative");
        }
        Objects.requireNonNull(point, "point");
    }
}
