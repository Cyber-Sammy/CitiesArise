package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

record SuburbLayout(
        GridBounds bounds,
        DistrictFootprint districtFootprint,
        int requestedParcelCapacity,
        List<GridBounds> parcelBounds,
        Optional<RoadGraph> routedRoadGraph,
        List<GridBounds> plannedFootprints,
        List<PotentialTerrainPreparationFootprint> terrainPreparationFootprints
) {
    SuburbLayout {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(districtFootprint, "districtFootprint");
        if (!bounds.equals(districtFootprint.bounds())) {
            throw new IllegalArgumentException("districtFootprint bounds must match layout bounds");
        }
        if (requestedParcelCapacity <= 0) {
            throw new IllegalArgumentException("requestedParcelCapacity must be positive");
        }
        parcelBounds = List.copyOf(parcelBounds);
        routedRoadGraph = Objects.requireNonNull(routedRoadGraph, "routedRoadGraph");
        plannedFootprints = List.copyOf(plannedFootprints);
        terrainPreparationFootprints = List.copyOf(terrainPreparationFootprints);
    }

    SuburbLayout(
            GridBounds bounds,
            int mainRoadZ,
            List<Integer> sideRoadXs,
            int requestedParcelCapacity,
            List<GridBounds> parcelBounds,
            Optional<RoadGraph> routedRoadGraph,
            List<GridBounds> plannedFootprints,
            List<PotentialTerrainPreparationFootprint> terrainPreparationFootprints
    ) {
        this(
                bounds,
                DistrictFootprint.rectangle(bounds),
                requestedParcelCapacity,
                parcelBounds,
                routedRoadGraph,
                plannedFootprints,
                terrainPreparationFootprints
        );
    }
}
