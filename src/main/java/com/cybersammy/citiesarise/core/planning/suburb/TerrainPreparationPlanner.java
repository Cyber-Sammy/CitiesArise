package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationArea;
import com.cybersammy.citiesarise.core.earthwork.BuildingTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.earthwork.ElevationTransition;
import com.cybersammy.citiesarise.core.earthwork.ElevationTransitionPolicy;
import com.cybersammy.citiesarise.core.earthwork.ElevationTransitionType;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumn;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.core.earthwork.ElevationZone;
import com.cybersammy.citiesarise.core.earthwork.ElevationZoneType;
import com.cybersammy.citiesarise.core.earthwork.RegionalElevationPlan;
import com.cybersammy.citiesarise.core.earthwork.RoadTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
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

        Optional<SuburbTerrainDiagnostic> transitionDiagnostic = addTransitions(
                request,
                elevationPlan,
                columns
        );
        if (transitionDiagnostic.isPresent()) {
            return TerrainPreparationAssessment.rejected(transitionDiagnostic.orElseThrow());
        }

        Optional<SuburbTerrainDiagnostic> shoulderDiagnostic = addTerrainShoulders(request, zones, columns);
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

    private static Optional<SuburbTerrainDiagnostic> addTransitions(
            SuburbPlanningRequest request,
            RegionalElevationPlan elevationPlan,
            Map<GridPoint, TerrainPreparationColumn> columns
    ) {
        Map<PlanElementId, ElevationZone> zonesById = zonesById(elevationPlan.zones());
        for (ElevationTransition transition : elevationPlan.transitions()) {
            ElevationZone source = zonesById.get(transition.sourceZoneId());
            ElevationZone target = zonesById.get(transition.targetZoneId());
            if (!ElevationTransitionPolicy.canMaterialize(transition, source, target)) {
                return Optional.of(transitionLengthDiagnostic(request, transition, source));
            }
            Optional<SuburbTerrainDiagnostic> diagnostic = addTransition(
                    request,
                    transition,
                    source,
                    target,
                    columns
            );
            if (diagnostic.isPresent()) {
                return diagnostic;
            }
        }
        return Optional.empty();
    }

    private static Map<PlanElementId, ElevationZone> zonesById(List<ElevationZone> zones) {
        Map<PlanElementId, ElevationZone> zonesById = new HashMap<>();
        for (ElevationZone zone : zones) {
            zonesById.put(zone.sourceElementId(), zone);
        }
        return Map.copyOf(zonesById);
    }

    private static Optional<SuburbTerrainDiagnostic> addTransition(
            SuburbPlanningRequest request,
            ElevationTransition transition,
            ElevationZone source,
            ElevationZone target,
            Map<GridPoint, TerrainPreparationColumn> columns
    ) {
        TerrainPreparationColumnType flatType = transitionFlatType(transition.type());
        TerrainPreparationColumnType stepType = transitionStepType(transition.type());
        PlanElementId owner = transitionOwner(transition);
        for (ElevationTransitionPolicy.TransitionPoint point
                : ElevationTransitionPolicy.materialize(transition, source, target)) {
            TerrainCell cell = TerrainPlatform.requiredTerrainCell(request, point.point());
            Optional<SuburbTerrainDiagnostic> terrainDiagnostic = shoulderTerrainDiagnostic(cell);
            if (terrainDiagnostic.isPresent()) {
                return terrainDiagnostic;
            }
            int elevationDelta = Math.subtractExact(point.targetElevation(), cell.height() - 1);
            Optional<TerrainRejectionReason> rejection = rejection(
                    request.settings(),
                    ElevationZoneType.ROAD_SEGMENT,
                    elevationDelta
            );
            if (rejection.isPresent()) {
                return Optional.of(depthDiagnostic(
                        request.settings(),
                        source,
                        cell,
                        elevationDelta,
                        rejection.orElseThrow()
                ));
            }
            columns.put(point.point(), new TerrainPreparationColumn(
                    point.point(),
                    owner,
                    point.targetElevation(),
                    Math.max(0, -elevationDelta),
                    Math.max(0, elevationDelta),
                    point.step() ? stepType : flatType
            ));
        }
        return Optional.empty();
    }

    private static TerrainPreparationColumnType transitionFlatType(ElevationTransitionType type) {
        if (type == ElevationTransitionType.ROAD_CONNECTION) {
            return TerrainPreparationColumnType.ROAD_TRANSITION_STEP;
        }
        return TerrainPreparationColumnType.BUILDING_ACCESS;
    }

    private static TerrainPreparationColumnType transitionStepType(ElevationTransitionType type) {
        if (type == ElevationTransitionType.ROAD_CONNECTION) {
            return TerrainPreparationColumnType.ROAD_TRANSITION_STEP;
        }
        return TerrainPreparationColumnType.BUILDING_ACCESS_STEP;
    }

    private static PlanElementId transitionOwner(ElevationTransition transition) {
        if (transition.type() == ElevationTransitionType.BUILDING_ACCESS) {
            return transition.targetZoneId();
        }
        return transition.sourceZoneId();
    }

    private static SuburbTerrainDiagnostic transitionLengthDiagnostic(
            SuburbPlanningRequest request,
            ElevationTransition transition,
            ElevationZone source
    ) {
        TerrainCell cell = TerrainPlatform.requiredTerrainCell(request, transition.anchor());
        long maximumDelta = ElevationTransitionPolicy.maximumMaterializableDelta(transition, source);
        TerrainPreparationLimitDiagnostic limit = new TerrainPreparationLimitDiagnostic(
                transition.targetZoneId(),
                transition.elevationDelta(),
                maximumDelta,
                maximumDelta
        );
        return diagnostic(cell, TerrainRejectionReason.EXCESSIVE_EARTHWORK, limit);
    }

    private static Optional<SuburbTerrainDiagnostic> addTerrainShoulders(
            SuburbPlanningRequest request,
            List<ElevationZone> zones,
            Map<GridPoint, TerrainPreparationColumn> columns
    ) {
        for (ShoulderKind kind : ShoulderKind.values()) {
            for (ElevationZone zone : zones) {
                if (zone.type() != kind.zoneType()) {
                    continue;
                }
                Optional<SuburbTerrainDiagnostic> diagnostic = addTerrainShoulder(
                        request,
                        zone,
                        kind,
                        columns
                );
                if (diagnostic.isPresent()) {
                    return diagnostic;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<SuburbTerrainDiagnostic> addTerrainShoulder(
            SuburbPlanningRequest request,
            ElevationZone zone,
            ShoulderKind kind,
            Map<GridPoint, TerrainPreparationColumn> columns
    ) {
        int radius = kind.radius();
        int minX = Math.subtractExact(zone.bounds().minX(), radius);
        int minZ = Math.subtractExact(zone.bounds().minZ(), radius);
        int maxXExclusive = Math.addExact(zone.bounds().maxXExclusive(), radius);
        int maxZExclusive = Math.addExact(zone.bounds().maxZExclusive(), radius);
        for (int z = minZ; z < maxZExclusive; z++) {
            for (int x = minX; x < maxXExclusive; x++) {
                Optional<SuburbTerrainDiagnostic> diagnostic = addTerrainShoulderColumn(
                        request,
                        zone,
                        kind,
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

    private static Optional<SuburbTerrainDiagnostic> addTerrainShoulderColumn(
            SuburbPlanningRequest request,
            ElevationZone zone,
            ShoulderKind kind,
            GridPoint point,
            Map<GridPoint, TerrainPreparationColumn> columns
    ) {
        if (!kind.contains(zone.bounds(), point)) {
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
        int targetElevation = kind.targetElevation(zone, point);
        int fillDepth = Math.subtractExact(targetElevation, cell.height() - 1);
        if (fillDepth <= 0) {
            return Optional.empty();
        }
        if (fillDepth > kind.maximumFillDepth()) {
            return Optional.of(supportDepthDiagnostic(
                    zone,
                    cell,
                    fillDepth,
                    kind.maximumFillDepth()
            ));
        }
        columns.put(point, new TerrainPreparationColumn(
                point,
                zone.sourceElementId(),
                targetElevation,
                0,
                fillDepth,
                kind.columnType()
        ));
        return Optional.empty();
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

    private static Optional<SuburbTerrainDiagnostic> shoulderTerrainDiagnostic(TerrainCell cell) {
        if (cell.water()) {
            return Optional.of(rejectionDiagnostic(cell, TerrainRejectionReason.WATER));
        }
        if (cell.terrainCategory() == TerrainCategory.BLOCKED) {
            return Optional.of(rejectionDiagnostic(cell, TerrainRejectionReason.BLOCKED_TERRAIN));
        }
        return Optional.empty();
    }

    private static SuburbTerrainDiagnostic supportDepthDiagnostic(
            ElevationZone zone,
            TerrainCell cell,
            int fillDepth,
            int maximumFillDepth
    ) {
        TerrainPreparationLimitDiagnostic limit = new TerrainPreparationLimitDiagnostic(
                zone.sourceElementId(),
                fillDepth,
                maximumFillDepth,
                maximumFillDepth
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

    private enum ShoulderKind {
        ROAD(
                ElevationZoneType.ROAD_SEGMENT,
                TerrainPreparationColumnType.ROAD_SHOULDER,
                RoadTerrainShoulderPolicy.RADIUS,
                RoadTerrainShoulderPolicy.MAX_FILL_DEPTH
        ),
        BUILDING(
                ElevationZoneType.BUILDING_PAD,
                TerrainPreparationColumnType.BUILDING_SHOULDER,
                BuildingTerrainShoulderPolicy.RADIUS,
                BuildingTerrainShoulderPolicy.MAX_FILL_DEPTH
        );

        private final ElevationZoneType zoneType;
        private final TerrainPreparationColumnType columnType;
        private final int radius;
        private final int maximumFillDepth;

        ShoulderKind(
                ElevationZoneType zoneType,
                TerrainPreparationColumnType columnType,
                int radius,
                int maximumFillDepth
        ) {
            this.zoneType = zoneType;
            this.columnType = columnType;
            this.radius = radius;
            this.maximumFillDepth = maximumFillDepth;
        }

        ElevationZoneType zoneType() {
            return zoneType;
        }

        TerrainPreparationColumnType columnType() {
            return columnType;
        }

        int radius() {
            return radius;
        }

        int maximumFillDepth() {
            return maximumFillDepth;
        }

        boolean contains(GridBounds bounds, GridPoint point) {
            if (this == ROAD) {
                return RoadTerrainShoulderPolicy.contains(bounds, point);
            }
            return BuildingTerrainShoulderPolicy.contains(bounds, point);
        }

        int targetElevation(ElevationZone zone, GridPoint point) {
            if (this == ROAD) {
                return RoadTerrainShoulderPolicy.targetElevation(
                        zone.bounds(),
                        point,
                        zone.targetElevation()
                );
            }
            return BuildingTerrainShoulderPolicy.targetElevation(
                    zone.bounds(),
                    point,
                    zone.targetElevation()
            );
        }
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
