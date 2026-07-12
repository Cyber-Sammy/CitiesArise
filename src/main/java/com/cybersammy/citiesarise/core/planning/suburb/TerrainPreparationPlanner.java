package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationArea;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class TerrainPreparationPlanner {
    private TerrainPreparationPlanner() {
    }

    static TerrainPreparationAssessment plan(SuburbPlanningRequest request, SettlementPlan settlementPlan) {
        List<TerrainPreparationArea> areas = new ArrayList<>();
        Optional<SuburbTerrainDiagnostic> roadDiagnostic = addRoadAreas(request, settlementPlan, areas);
        if (roadDiagnostic.isPresent()) {
            return TerrainPreparationAssessment.rejected(roadDiagnostic.orElseThrow());
        }

        Optional<SuburbTerrainDiagnostic> buildingDiagnostic = addBuildingAreas(request, settlementPlan, areas);
        if (buildingDiagnostic.isPresent()) {
            return TerrainPreparationAssessment.rejected(buildingDiagnostic.orElseThrow());
        }

        TerrainPreparationPlan preparationPlan = TerrainPreparationPlan.of(areas);
        if (preparationPlan.totalVolume() > request.settings().maxEarthworkVolume()) {
            return TerrainPreparationAssessment.rejected(totalVolumeDiagnostic(request, areas));
        }
        return TerrainPreparationAssessment.accepted(preparationPlan);
    }

    private static Optional<SuburbTerrainDiagnostic> addRoadAreas(
            SuburbPlanningRequest request,
            SettlementPlan plan,
            List<TerrainPreparationArea> areas
    ) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(plan);
        for (RoadSegment segment : plan.roadGraph().segments()) {
            RoadNode start = RoadElevationPlanner.requiredNode(nodesById, segment.startNodeId());
            RoadNode end = RoadElevationPlanner.requiredNode(nodesById, segment.endNodeId());
            GridBounds bounds = AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width());
            Optional<SuburbTerrainDiagnostic> diagnostic = addArea(
                    request,
                    segment.id(),
                    bounds,
                    TerrainPlatform.requiredElevation(segment.properties()),
                    areas
            );
            if (diagnostic.isPresent()) {
                return diagnostic;
            }
        }
        return Optional.empty();
    }

    private static Map<PlanElementId, RoadNode> nodesById(SettlementPlan plan) {
        Map<PlanElementId, RoadNode> nodesById = new HashMap<>();
        for (RoadNode node : plan.roadGraph().nodes()) {
            nodesById.put(node.id(), node);
        }
        return Map.copyOf(nodesById);
    }

    private static Optional<SuburbTerrainDiagnostic> addBuildingAreas(
            SuburbPlanningRequest request,
            SettlementPlan plan,
            List<TerrainPreparationArea> areas
    ) {
        for (BuildingSlot slot : plan.buildingSlots()) {
            Optional<SuburbTerrainDiagnostic> diagnostic = addArea(
                    request,
                    slot.id(),
                    slot.bounds(),
                    TerrainPlatform.requiredElevation(slot.properties()),
                    areas
            );
            if (diagnostic.isPresent()) {
                return diagnostic;
            }
        }
        return Optional.empty();
    }

    private static Optional<SuburbTerrainDiagnostic> addArea(
            SuburbPlanningRequest request,
            PlanElementId sourceElementId,
            GridBounds bounds,
            int targetElevation,
            List<TerrainPreparationArea> areas
    ) {
        long cutVolume = 0L;
        long fillVolume = 0L;
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                TerrainCell cell = TerrainPlatform.requiredTerrainCell(request, new GridPoint(x, z));
                int elevationDelta = Math.subtractExact(targetElevation, cell.height() - 1);
                Optional<TerrainRejectionReason> rejection = rejection(request.settings(), elevationDelta);
                if (rejection.isPresent()) {
                    return Optional.of(diagnostic(cell, rejection.orElseThrow()));
                }
                if (elevationDelta < 0) {
                    cutVolume = Math.addExact(cutVolume, -(long) elevationDelta);
                } else {
                    fillVolume = Math.addExact(fillVolume, elevationDelta);
                }
            }
        }
        areas.add(new TerrainPreparationArea(sourceElementId, bounds, targetElevation, cutVolume, fillVolume));
        return Optional.empty();
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
            List<TerrainPreparationArea> areas
    ) {
        TerrainPreparationArea area = largestArea(areas);
        TerrainCell cell = TerrainPlatform.requiredTerrainCell(request, area.bounds().origin());
        return diagnostic(cell, TerrainRejectionReason.EXCESSIVE_EARTHWORK);
    }

    private static TerrainPreparationArea largestArea(List<TerrainPreparationArea> areas) {
        TerrainPreparationArea largest = null;
        for (TerrainPreparationArea area : areas) {
            if (largest == null || area.totalVolume() > largest.totalVolume()) {
                largest = area;
            }
        }
        if (largest == null) {
            throw new IllegalStateException("earthwork budget exceeded without preparation areas");
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
}
