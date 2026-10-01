package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import java.util.Objects;

record PotentialTerrainPreparationFootprint(
        GridBounds bounds,
        int modificationRadius,
        int requiredSupportRadius
) {
    PotentialTerrainPreparationFootprint(GridBounds bounds, int supportRadius) {
        this(bounds, supportRadius, supportRadius);
    }

    PotentialTerrainPreparationFootprint {
        Objects.requireNonNull(bounds, "bounds");
        if (modificationRadius < 0) {
            throw new IllegalArgumentException("modificationRadius must not be negative");
        }
        if (requiredSupportRadius < 0) {
            throw new IllegalArgumentException("requiredSupportRadius must not be negative");
        }
        if (requiredSupportRadius > modificationRadius) {
            throw new IllegalArgumentException("requiredSupportRadius must not exceed modificationRadius");
        }
    }
}
