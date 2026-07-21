package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationArea;
import com.cybersammy.citiesarise.core.earthwork.BuildingTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumn;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.core.earthwork.ElevationZone;
import com.cybersammy.citiesarise.core.earthwork.ElevationZoneType;
import com.cybersammy.citiesarise.core.earthwork.RegionalElevationPlan;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
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

        Optional<SuburbTerrainDiagnostic> shoulderDiagnostic = addBuildingShoulders(request, zones, columns);
        if (shoulderDiagnostic.isPresent()) {
            return TerrainPreparationAssessment.rejected(shoulderDiagnostic.orElseThrow());
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
                Optional<TerrainRejectionReason> rejection = rejection(
                        request.settings(),
                        zone.type(),
                        elevationDelta
                );
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

    private static Optional<SuburbTerrainDiagnostic> addBuildingShoulders(
            SuburbPlanningRequest request,
            List<ElevationZone> zones,
            Map<GridPoint, TerrainPreparationColumn> columns
    ) {
        for (ElevationZone zone : zones) {
            if (zone.type() != ElevationZoneType.BUILDING_PAD) {
                continue;
            }
            Optional<SuburbTerrainDiagnostic> diagnostic = addBuildingShoulder(request, zone, columns);
            if (diagnostic.isPresent()) {
                return diagnostic;
            }
        }
        return Optional.empty();
    }

    private static Optional<SuburbTerrainDiagnostic> addBuildingShoulder(
            SuburbPlanningRequest request,
            ElevationZone zone,
            Map<GridPoint, TerrainPreparationColumn> columns
    ) {
        int radius = BuildingTerrainShoulderPolicy.RADIUS;
        int minX = Math.subtractExact(zone.bounds().minX(), radius);
        int minZ = Math.subtractExact(zone.bounds().minZ(), radius);
        int maxXExclusive = Math.addExact(zone.bounds().maxXExclusive(), radius);
        int maxZExclusive = Math.addExact(zone.bounds().maxZExclusive(), radius);

        for (int z = minZ; z < maxZExclusive; z++) {
            for (int x = minX; x < maxXExclusive; x++) {
                Optional<SuburbTerrainDiagnostic> diagnostic = addBuildingShoulderColumn(
                        request,
                        zone,
                        new GridPoint(x, z),
                        columns
                );
                if (diagnostic.isPresent()) {
                    return diagnostic;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<SuburbTerrainDiagnostic> addBuildingShoulderColumn(
            SuburbPlanningRequest request,
            ElevationZone zone,
            GridPoint point,
            Map<GridPoint, TerrainPreparationColumn> columns
    ) {
        if (!BuildingTerrainShoulderPolicy.contains(zone.bounds(), point)) {
            return Optional.empty();
        }
        if (!request.survey().bounds().contains(point)) {
            return Optional.empty();
        }
        if (columns.containsKey(point)) {
            return Optional.empty();
        }

        TerrainCell cell = TerrainPlatform.requiredTerrainCell(request, point);
        Optional<SuburbTerrainDiagnostic> terrainDiagnostic = shoulderTerrainDiagnostic(cell);
        if (terrainDiagnostic.isPresent()) {
            return terrainDiagnostic;
        }

        int targetElevation = BuildingTerrainShoulderPolicy.targetElevation(
                zone.bounds(),
                point,
                zone.targetElevation()
        );
        int fillDepth = Math.subtractExact(targetElevation, cell.height() - 1);
        if (fillDepth <= 0) {
            return Optional.empty();
        }
        if (fillDepth > BuildingTerrainShoulderPolicy.MAX_FILL_DEPTH) {
            return Optional.of(shoulderDepthDiagnostic(zone, cell, fillDepth));
        }

        columns.put(point, new TerrainPreparationColumn(
                point,
                zone.sourceElementId(),
                targetElevation,
                0,
                fillDepth,
                TerrainPreparationColumnType.BUILDING_SHOULDER
        ));
        return Optional.empty();
    }

    private static Optional<SuburbTerrainDiagnostic> shoulderTerrainDiagnostic(TerrainCell cell) {
        if (cell.water()) {
            return Optional.of(rejectionDiagnostic(cell, TerrainRejectionReason.WATER));
        }
        if (cell.terrainCategory() == TerrainCategory.BLOCKED) {
            return Optional.of(rejectionDiagnostic(cell, TerrainRejectionReason.BLOCKED_TERRAIN));
        }
        return Optional.empty();
    }

    private static SuburbTerrainDiagnostic shoulderDepthDiagnostic(
            ElevationZone zone,
            TerrainCell cell,
            int fillDepth
    ) {
        TerrainPreparationLimitDiagnostic limit = new TerrainPreparationLimitDiagnostic(
                zone.sourceElementId(),
                fillDepth,
                BuildingTerrainShoulderPolicy.MAX_FILL_DEPTH,
                BuildingTerrainShoulderPolicy.MAX_FILL_DEPTH
        );
        TerrainSuitability suitability = new TerrainSuitability(
                0.0,
                Set.of(TerrainRejectionReason.EXCESSIVE_FILL),
                List.of()
        );
        return new SuburbTerrainDiagnostic(cell, suitability, limit);
    }

    private static SuburbTerrainDiagnostic rejectionDiagnostic(
            TerrainCell cell,
            TerrainRejectionReason reason
    ) {
        return new SuburbTerrainDiagnostic(cell, new TerrainSuitability(0.0, Set.of(reason), List.of()));
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
            ElevationZoneType zoneType,
            int elevationDelta
    ) {
        if (elevationDelta < -settings.maxCutDepth()) {
            return Optional.of(TerrainRejectionReason.EXCESSIVE_CUT);
        }
        if (exceedsFillLimit(settings, zoneType, elevationDelta)) {
            return Optional.of(TerrainRejectionReason.EXCESSIVE_FILL);
        }
        return Optional.empty();
    }

    private static boolean exceedsFillLimit(
            SuburbPlanningSettings settings,
            ElevationZoneType zoneType,
            int elevationDelta
    ) {
        if (zoneType == ElevationZoneType.BUILDING_PAD) {
            return elevationDelta > settings.maxBuildingFoundationDepth();
        }

        return elevationDelta > settings.maxFillDepth();
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
        int maximumLimit = maximumLimit(settings, zone.type(), rejectionReason);
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
            ElevationZoneType zoneType,
            TerrainRejectionReason rejectionReason
    ) {
        if (rejectionReason == TerrainRejectionReason.EXCESSIVE_CUT) {
            return settings.maxCutDepth();
        }

        if (zoneType == ElevationZoneType.BUILDING_PAD) {
            return settings.maxBuildingFoundationDepth();
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
