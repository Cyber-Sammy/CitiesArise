package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import java.util.Objects;

record PotentialTerrainPreparationFootprint(GridBounds bounds, int supportRadius) {
    PotentialTerrainPreparationFootprint {
        Objects.requireNonNull(bounds, "bounds");
        if (supportRadius < 0) {
            throw new IllegalArgumentException("supportRadius must not be negative");
        }
    }
}
