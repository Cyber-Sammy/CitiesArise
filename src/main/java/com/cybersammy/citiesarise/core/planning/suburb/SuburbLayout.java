package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import java.util.List;
import java.util.Objects;

record SuburbLayout(
        GridBounds bounds,
        int mainRoadZ,
        List<Integer> sideRoadXs,
        List<GridBounds> parcelBounds,
        List<GridBounds> plannedFootprints,
        List<PotentialTerrainPreparationFootprint> terrainPreparationFootprints
) {
    SuburbLayout {
        Objects.requireNonNull(bounds, "bounds");
        sideRoadXs = List.copyOf(sideRoadXs);
        parcelBounds = List.copyOf(parcelBounds);
        plannedFootprints = List.copyOf(plannedFootprints);
        terrainPreparationFootprints = List.copyOf(terrainPreparationFootprints);
    }
}
