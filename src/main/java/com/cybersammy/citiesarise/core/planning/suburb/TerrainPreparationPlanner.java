package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationArea;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumn;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
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

    static TerrainPreparationAssessment plan(SuburbPlanningRequest request, SettlementPlan settlementPlan) {
        List<AreaDefinition> definitions = areaDefinitions(settlementPlan);
        Map<GridPoint, TerrainPreparationColumn> columns = new LinkedHashMap<>();

        for (AreaDefinition definition : definitions) {
            Optional<SuburbTerrainDiagnostic> diagnostic = addColumns(request, definition, columns);
            if (diagnostic.isPresent()) {
                return TerrainPreparationAssessment.rejected(diagnostic.orElseThrow());
            }
        }

        List<TerrainPreparationArea> areas = normalizedAreas(definitions, columns.values());
        TerrainPreparationPlan preparationPlan = TerrainPreparationPlan.of(areas, List.copyOf(columns.values()));
        if (preparationPlan.totalVolume() > request.settings().maxEarthworkVolume()) {
            return TerrainPreparationAssessment.rejected(totalVolumeDiagnostic(request, columns.values()));
        }
        return TerrainPreparationAssessment.accepted(preparationPlan);
    }

    private static List<AreaDefinition> areaDefinitions(SettlementPlan plan) {
        List<AreaDefinition> definitions = new ArrayList<>();
        addRoadDefinitions(plan, definitions);
        addBuildingDefinitions(plan, definitions);
        return List.copyOf(definitions);
    }

    private static void addRoadDefinitions(SettlementPlan plan, List<AreaDefinition> definitions) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(plan);
        for (RoadSegment segment : plan.roadGraph().segments()) {
            RoadNode start = RoadElevationPlanner.requiredNode(nodesById, segment.startNodeId());
            RoadNode end = RoadElevationPlanner.requiredNode(nodesById, segment.endNodeId());
            definitions.add(new AreaDefinition(
                    segment.id(),
                    AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width()),
                    TerrainPlatform.requiredElevation(segment.properties())
            ));
        }
    }

    private static void addBuildingDefinitions(SettlementPlan plan, List<AreaDefinition> definitions) {
        for (BuildingSlot slot : plan.buildingSlots()) {
            definitions.add(new AreaDefinition(
                    slot.id(),
                    slot.bounds(),
                    TerrainPlatform.requiredElevation(slot.properties())
            ));
        }
    }

    private static Map<PlanElementId, RoadNode> nodesById(SettlementPlan plan) {
        Map<PlanElementId, RoadNode> nodesById = new HashMap<>();
        for (RoadNode node : plan.roadGraph().nodes()) {
            nodesById.put(node.id(), node);
        }
        return Map.copyOf(nodesById);
    }

    private static Optional<SuburbTerrainDiagnostic> addColumns(
            SuburbPlanningRequest request,
            AreaDefinition definition,
            Map<GridPoint, TerrainPreparationColumn> columns
    ) {
        for (int z = definition.bounds().minZ(); z < definition.bounds().maxZExclusive(); z++) {
            for (int x = definition.bounds().minX(); x < definition.bounds().maxXExclusive(); x++) {
                GridPoint point = new GridPoint(x, z);
                TerrainCell cell = TerrainPlatform.requiredTerrainCell(request, point);
                int elevationDelta = Math.subtractExact(definition.targetElevation(), cell.height() - 1);
                Optional<TerrainRejectionReason> rejection = rejection(request.settings(), elevationDelta);
                if (rejection.isPresent()) {
                    return Optional.of(diagnostic(cell, rejection.orElseThrow()));
                }
                columns.putIfAbsent(point, column(definition, point, elevationDelta));
            }
        }
        return Optional.empty();
    }

    private static TerrainPreparationColumn column(
            AreaDefinition definition,
            GridPoint point,
            int elevationDelta
    ) {
        int cutDepth = Math.max(0, -elevationDelta);
        int fillDepth = Math.max(0, elevationDelta);
        return new TerrainPreparationColumn(
                point,
                definition.sourceElementId(),
                definition.targetElevation(),
                cutDepth,
                fillDepth
        );
    }

    private static List<TerrainPreparationArea> normalizedAreas(
            List<AreaDefinition> definitions,
            Iterable<TerrainPreparationColumn> columns
    ) {
        Map<PlanElementId, Volume> volumeByElement = new HashMap<>();
        for (TerrainPreparationColumn column : columns) {
            volumeByElement.computeIfAbsent(column.sourceElementId(), ignored -> new Volume()).add(column);
        }

        List<TerrainPreparationArea> areas = new ArrayList<>();
        for (AreaDefinition definition : definitions) {
            Volume volume = volumeByElement.getOrDefault(definition.sourceElementId(), new Volume());
            areas.add(new TerrainPreparationArea(
                    definition.sourceElementId(),
                    definition.bounds(),
                    definition.targetElevation(),
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
            Iterable<TerrainPreparationColumn> columns
    ) {
        TerrainPreparationColumn largest = largestColumn(columns);
        TerrainCell cell = TerrainPlatform.requiredTerrainCell(request, largest.point());
        return diagnostic(cell, TerrainRejectionReason.EXCESSIVE_EARTHWORK);
    }

    private static TerrainPreparationColumn largestColumn(Iterable<TerrainPreparationColumn> columns) {
        TerrainPreparationColumn largest = null;
        for (TerrainPreparationColumn column : columns) {
            if (largest == null || column.totalVolume() > largest.totalVolume()) {
                largest = column;
            }
        }
        if (largest == null) {
            throw new IllegalStateException("earthwork budget exceeded without preparation columns");
        }
        return largest;
    }

    private static SuburbTerrainDiagnostic diagnostic(
            TerrainCell cell,
            TerrainRejectionReason rejectionReason
    ) {
        TerrainSuitability suitability = new TerrainSuitability(0.0, Set.of(rejectionReason), List.of());
        return new SuburbTerrainDiagnostic(cell, suitability);
    }

    private record AreaDefinition(PlanElementId sourceElementId, GridBounds bounds, int targetElevation) {
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
