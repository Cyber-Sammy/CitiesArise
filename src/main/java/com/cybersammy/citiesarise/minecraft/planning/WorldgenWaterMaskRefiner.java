package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanner;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningRequest;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.Objects;
import java.util.Optional;
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

        Set<GridPoint> footprint = SettlementPlanFootprint.points(initialResult.plan().orElseThrow());
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
}
