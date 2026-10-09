package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbTerrainDiagnostic;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitability;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumn;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;

final class TerrainSupportAcceptance {
    private TerrainSupportAcceptance() { }

    static SuburbPlanningResult validate(WorldgenTerrainSurveyProvider terrain, TerrainSurvey survey,
            SuburbPlanningResult result) {
        if (!result.successful() || result.terrainPreparationPlan().isEmpty()) return result;
        var preparation = result.terrainPreparationPlan().orElseThrow();
        var supportColumns = new ArrayList<TerrainPreparationColumn>();
        Set<com.cybersammy.citiesarise.core.geometry.GridPoint> known = new java.util.HashSet<>();
        for (var bridge : result.plan().orElseThrow().roadGraph().bridges()) {
            for(var footing:bridge.foundations()) {
                var point=bridge.point(footing.distance(),footing.lateral());
                if(known.add(point)) supportColumns.add(new TerrainPreparationColumn(point,bridge.id(),footing.bottomY()+1,0,0));
            }
            for (int distance = 0; distance <= bridge.length(); distance++) {
                if (!bridge.bank(distance)) continue;
                for (int lateral = 0; lateral < bridge.width(); lateral++) {
                    var point = bridge.point(distance, lateral - bridge.width() / 2);
                    if (known.add(point)) supportColumns.add(new TerrainPreparationColumn(point, bridge.id(),
                            bridge.deckElevation(distance) - bridge.deckDepth() + 1, 0, 0));
                }
            }
        }
        var supportPlan = TerrainPreparationPlan.of(preparation.elevationPlan(), List.of(), supportColumns);
        return terrain.unsupportedColumn(preparation).or(() -> terrain.unsupportedColumn(supportPlan))
                .map(column -> SuburbPlanningResult.rejectedTerrain(new SuburbTerrainDiagnostic(
                        survey.findCell(column.point()).orElseThrow(),
                        new TerrainSuitability(0.0, Set.of(TerrainRejectionReason.UNSUPPORTED_TERRAIN), List.of()))))
                .orElse(result);
    }
}
