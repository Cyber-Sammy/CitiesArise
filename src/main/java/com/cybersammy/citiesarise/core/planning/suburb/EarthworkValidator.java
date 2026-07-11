package com.cybersammy.citiesarise.core.planning.suburb;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class EarthworkValidator {
    private EarthworkValidator() {
    }

    static Optional<SuburbTerrainDiagnostic> findDiagnostic(
            SuburbPlanningRequest request,
            SettlementPlan plan
    ) {
        Optional<SuburbTerrainDiagnostic> roadDiagnostic = findRoadDiagnostic(request, plan);
        if (roadDiagnostic.isPresent()) {
            return roadDiagnostic;
        }

        for (BuildingSlot slot : plan.buildingSlots()) {
            Optional<SuburbTerrainDiagnostic> diagnostic = findBoundsDiagnostic(
                    request,
                    slot.bounds(),
                    TerrainPlatform.requiredElevation(slot.properties())
            );
            if (diagnostic.isPresent()) {
                return diagnostic;
            }
        }
        return Optional.empty();
    }

    private static Optional<SuburbTerrainDiagnostic> findRoadDiagnostic(
            SuburbPlanningRequest request,
            SettlementPlan plan
    ) {
        Map<PlanElementId, RoadNode> nodesById = new HashMap<>();
        for (RoadNode node : plan.roadGraph().nodes()) {
            nodesById.put(node.id(), node);
        }

        for (RoadSegment segment : plan.roadGraph().segments()) {
            RoadNode start = RoadElevationPlanner.requiredNode(nodesById, segment.startNodeId());
            RoadNode end = RoadElevationPlanner.requiredNode(nodesById, segment.endNodeId());
            GridBounds bounds = AxisAlignedGridCorridor.bounds(start.point(), end.point(), segment.width());
            Optional<SuburbTerrainDiagnostic> diagnostic = findBoundsDiagnostic(
                    request,
                    bounds,
                    TerrainPlatform.requiredElevation(segment.properties())
            );
            if (diagnostic.isPresent()) {
                return diagnostic;
            }
        }
        return Optional.empty();
    }

    private static Optional<SuburbTerrainDiagnostic> findBoundsDiagnostic(
            SuburbPlanningRequest request,
            GridBounds bounds,
            int platformY
    ) {
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                TerrainCell cell = TerrainPlatform.requiredTerrainCell(request, new GridPoint(x, z));
                Optional<TerrainRejectionReason> rejection = rejection(request.settings(), cell, platformY);
                if (rejection.isPresent()) {
                    return Optional.of(diagnostic(cell, rejection.orElseThrow()));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<TerrainRejectionReason> rejection(
            SuburbPlanningSettings settings,
            TerrainCell cell,
            int platformY
    ) {
        int groundY = cell.height() - 1;
        if (groundY - platformY > settings.maxCutDepth()) {
            return Optional.of(TerrainRejectionReason.EXCESSIVE_CUT);
        }
        if (platformY - groundY > settings.maxFillDepth()) {
            return Optional.of(TerrainRejectionReason.EXCESSIVE_FILL);
        }
        return Optional.empty();
    }

    private static SuburbTerrainDiagnostic diagnostic(
            TerrainCell cell,
            TerrainRejectionReason rejectionReason
    ) {
        TerrainSuitability suitability = new TerrainSuitability(0.0, Set.of(rejectionReason), List.of());
        return new SuburbTerrainDiagnostic(cell, suitability);
    }
}
