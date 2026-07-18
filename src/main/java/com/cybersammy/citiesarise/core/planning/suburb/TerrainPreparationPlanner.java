package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationArea;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumn;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.core.earthwork.ElevationZone;
import com.cybersammy.citiesarise.core.earthwork.RegionalElevationPlan;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitability;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class TerrainPreparationPlanner {
    private TerrainPreparationPlanner() {
    }

    static TerrainPreparationAssessment plan(
            SuburbPlanningRequest request,
            RegionalElevationPlan elevationPlan
    ) {
        List<ElevationZone> zones = elevationPlan.zones();
        Map<GridPoint, TerrainPreparationColumn> columns = new LinkedHashMap<>();

        for (ElevationZone zone : zones) {
            Optional<SuburbTerrainDiagnostic> diagnostic = addColumns(request, zone, columns);
            if (diagnostic.isPresent()) {
                return TerrainPreparationAssessment.rejected(diagnostic.orElseThrow());
            }
        }

        List<TerrainPreparationArea> areas = normalizedAreas(zones, columns.values());
        TerrainPreparationPlan preparationPlan = TerrainPreparationPlan.of(
                elevationPlan,
                areas,
                List.copyOf(columns.values())
        );
        if (preparationPlan.totalVolume() > request.settings().maxEarthworkVolume()) {
            return TerrainPreparationAssessment.rejected(totalVolumeDiagnostic(
                    request,
                    columns.values(),
                    preparationPlan.totalVolume()
            ));
        }
        return TerrainPreparationAssessment.accepted(preparationPlan);
    }

    private static Optional<SuburbTerrainDiagnostic> addColumns(
            SuburbPlanningRequest request,
            ElevationZone zone,
            Map<GridPoint, TerrainPreparationColumn> columns
    ) {
        for (int z = zone.bounds().minZ(); z < zone.bounds().maxZExclusive(); z++) {
            for (int x = zone.bounds().minX(); x < zone.bounds().maxXExclusive(); x++) {
                GridPoint point = new GridPoint(x, z);
                TerrainCell cell = TerrainPlatform.requiredTerrainCell(request, point);
                int elevationDelta = Math.subtractExact(zone.targetElevation(), cell.height() - 1);
                Optional<TerrainRejectionReason> rejection = rejection(request.settings(), elevationDelta);
                if (rejection.isPresent()) {
                    return Optional.of(depthDiagnostic(
                            request.settings(),
                            zone,
                            cell,
                            elevationDelta,
                            rejection.orElseThrow()
                    ));
                }
                columns.putIfAbsent(point, column(zone, point, elevationDelta));
            }
        }
        return Optional.empty();
    }

    private static TerrainPreparationColumn column(
            ElevationZone zone,
            GridPoint point,
            int elevationDelta
    ) {
        int cutDepth = Math.max(0, -elevationDelta);
        int fillDepth = Math.max(0, elevationDelta);
        return new TerrainPreparationColumn(
                point,
                zone.sourceElementId(),
                zone.targetElevation(),
                cutDepth,
                fillDepth
        );
    }

    private static List<TerrainPreparationArea> normalizedAreas(
            List<ElevationZone> zones,
            Iterable<TerrainPreparationColumn> columns
    ) {
        Map<PlanElementId, Volume> volumeByElement = new HashMap<>();
        for (TerrainPreparationColumn column : columns) {
            volumeByElement.computeIfAbsent(column.sourceElementId(), ignored -> new Volume()).add(column);
        }

        List<TerrainPreparationArea> areas = new ArrayList<>();
        for (ElevationZone zone : zones) {
            Volume volume = volumeByElement.getOrDefault(zone.sourceElementId(), new Volume());
            areas.add(new TerrainPreparationArea(
                    zone.sourceElementId(),
                    zone.bounds(),
                    zone.targetElevation(),
                    volume.cutVolume,
                    volume.fillVolume
            ));
        }
        return List.copyOf(areas);
    }

    private static Optional<TerrainRejectionReason> rejection(
            SuburbPlanningSettings settings,
            int elevationDelta
    ) {
        if (elevationDelta < -settings.maxCutDepth()) {
            return Optional.of(TerrainRejectionReason.EXCESSIVE_CUT);
        }
        if (elevationDelta > settings.maxFillDepth()) {
            return Optional.of(TerrainRejectionReason.EXCESSIVE_FILL);
        }
        return Optional.empty();
    }

    private static SuburbTerrainDiagnostic totalVolumeDiagnostic(
            SuburbPlanningRequest request,
            Iterable<TerrainPreparationColumn> columns,
            long totalVolume
    ) {
        TerrainPreparationColumn largest = largestColumn(columns);
        TerrainCell cell = TerrainPlatform.requiredTerrainCell(request, largest.point());
        TerrainPreparationLimitDiagnostic limit = new TerrainPreparationLimitDiagnostic(
                largest.sourceElementId(),
                totalVolume,
                request.settings().maxEarthworkVolume(),
                request.settings().maxEarthworkVolume()
        );
        return diagnostic(cell, TerrainRejectionReason.EXCESSIVE_EARTHWORK, limit);
    }

    private static TerrainPreparationColumn largestColumn(Iterable<TerrainPreparationColumn> columns) {
        TerrainPreparationColumn largest = null;
        for (TerrainPreparationColumn column : columns) {
            if (isLarger(column, largest)) {
                largest = column;
            }
        }
        if (largest == null) {
            throw new IllegalStateException("earthwork budget exceeded without preparation columns");
        }
        return largest;
    }

    private static boolean isLarger(
            TerrainPreparationColumn candidate,
            TerrainPreparationColumn current
    ) {
        if (current == null) {
            return true;
        }

        return candidate.totalVolume() > current.totalVolume();
    }

    private static SuburbTerrainDiagnostic depthDiagnostic(
            SuburbPlanningSettings settings,
            ElevationZone zone,
            TerrainCell cell,
            int elevationDelta,
            TerrainRejectionReason rejectionReason
    ) {
        int actualDepth = Math.abs(elevationDelta);
        int preferredLimit = preferredLimit(settings, rejectionReason);
        int maximumLimit = maximumLimit(settings, rejectionReason);
        TerrainPreparationLimitDiagnostic limit = new TerrainPreparationLimitDiagnostic(
                zone.sourceElementId(),
                actualDepth,
                preferredLimit,
                maximumLimit
        );
        return diagnostic(cell, rejectionReason, limit);
    }

    private static int preferredLimit(
            SuburbPlanningSettings settings,
            TerrainRejectionReason rejectionReason
    ) {
        if (rejectionReason == TerrainRejectionReason.EXCESSIVE_CUT) {
            return settings.preferredMaxCutDepth();
        }

        return settings.preferredMaxFillDepth();
    }

    private static int maximumLimit(
            SuburbPlanningSettings settings,
            TerrainRejectionReason rejectionReason
    ) {
        if (rejectionReason == TerrainRejectionReason.EXCESSIVE_CUT) {
            return settings.maxCutDepth();
        }

        return settings.maxFillDepth();
    }

    private static SuburbTerrainDiagnostic diagnostic(
            TerrainCell cell,
            TerrainRejectionReason rejectionReason,
            TerrainPreparationLimitDiagnostic limit
    ) {
        TerrainSuitability suitability = new TerrainSuitability(0.0, Set.of(rejectionReason), List.of());
        return new SuburbTerrainDiagnostic(cell, suitability, limit);
    }

    private static final class Volume {
        private long cutVolume;
        private long fillVolume;

        private void add(TerrainPreparationColumn column) {
            cutVolume = Math.addExact(cutVolume, column.cutDepth());
            fillVolume = Math.addExact(fillVolume, column.fillDepth());
        }
    }
}
