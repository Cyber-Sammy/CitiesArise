package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanner;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningRequest;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;

final class WorldgenWaterMaskRefiner {
    private WorldgenWaterMaskRefiner() {
    }

    static SuburbPlanningResult refine(
            SuburbPlanner planner,
            WorldgenTerrainSurveyProvider terrainProvider,
            SuburbPlanningRequest initialRequest,
            SuburbPlanningResult initialResult
    ) {
        Objects.requireNonNull(planner, "planner");
        Objects.requireNonNull(terrainProvider, "terrainProvider");
        Objects.requireNonNull(initialRequest, "initialRequest");
        Objects.requireNonNull(initialResult, "initialResult");
        if (!initialResult.successful()) {
            return initialResult;
        }

        Set<GridPoint> footprint = refinementFootprint(initialResult);
        Optional<TerrainSurvey> refinedSurvey = terrainProvider.sampleWithExactWaterMask(
                initialRequest.survey().bounds(),
                footprint
        );
        if (refinedSurvey.isEmpty()) {
            return initialResult;
        }

        SuburbPlanningRequest refinedRequest = new SuburbPlanningRequest(
                initialRequest.settlementId(),
                refinedSurvey.orElseThrow(),
                initialRequest.seed(),
                initialRequest.settings()
        );
        return planner.plan(refinedRequest);
    }

    private static Set<GridPoint> refinementFootprint(SuburbPlanningResult result) {
        Set<GridPoint> points = new LinkedHashSet<>(SettlementPlanFootprint.points(result.plan().orElseThrow()));
        result.terrainPreparationPlan().orElseThrow().columns().forEach(column -> points.add(column.point()));
        return Set.copyOf(points);
    }
}
