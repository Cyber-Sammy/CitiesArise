package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

record SuburbLayout(
        GridBounds bounds,
        int mainRoadZ,
        List<Integer> sideRoadXs,
        List<GridBounds> parcelBounds,
        Optional<RoadGraph> routedRoadGraph,
        List<GridBounds> plannedFootprints,
        List<PotentialTerrainPreparationFootprint> terrainPreparationFootprints
) {
    SuburbLayout {
        Objects.requireNonNull(bounds, "bounds");
        sideRoadXs = List.copyOf(sideRoadXs);
        parcelBounds = List.copyOf(parcelBounds);
        routedRoadGraph = Objects.requireNonNull(routedRoadGraph, "routedRoadGraph");
        plannedFootprints = List.copyOf(plannedFootprints);
        terrainPreparationFootprints = List.copyOf(terrainPreparationFootprints);
    }
}
