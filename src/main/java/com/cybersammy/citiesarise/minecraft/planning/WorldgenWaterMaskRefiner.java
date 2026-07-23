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
    private static final int MAX_INCREMENTAL_REFINEMENTS = 8;

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

        Set<GridPoint> checkedPoints = new LinkedHashSet<>();
        SuburbPlanningResult currentResult = initialResult;
        for (int iteration = 0; iteration < MAX_INCREMENTAL_REFINEMENTS; iteration++) {
            if (!currentResult.successful()) {
                return currentResult;
            }
            Set<GridPoint> footprint = refinementFootprint(currentResult);
            if (checkedPoints.containsAll(footprint)) {
                return currentResult;
            }
            checkedPoints.addAll(footprint);
            Optional<TerrainSurvey> refinedSurvey = terrainProvider.sampleWithExactWaterMask(
                    initialRequest.survey().bounds(),
                    Set.copyOf(checkedPoints)
            );
            if (refinedSurvey.isEmpty()) {
                return currentResult;
            }
            currentResult = replan(planner, initialRequest, refinedSurvey.orElseThrow());
        }

        if (!currentResult.successful()) {
            return currentResult;
        }
        return refineCompleteSurvey(planner, terrainProvider, initialRequest, currentResult);
    }

    private static SuburbPlanningResult refineCompleteSurvey(
            SuburbPlanner planner,
            WorldgenTerrainSurveyProvider terrainProvider,
            SuburbPlanningRequest initialRequest,
            SuburbPlanningResult currentResult
    ) {
        Set<GridPoint> surveyPoints = points(initialRequest.survey());
        Optional<TerrainSurvey> refinedSurvey = terrainProvider.sampleWithExactWaterMask(
                initialRequest.survey().bounds(),
                surveyPoints
        );
        if (refinedSurvey.isEmpty()) {
            return currentResult;
        }
        return replan(planner, initialRequest, refinedSurvey.orElseThrow());
    }

    private static SuburbPlanningResult replan(
            SuburbPlanner planner,
            SuburbPlanningRequest initialRequest,
            TerrainSurvey refinedSurvey
    ) {
        SuburbPlanningRequest refinedRequest = new SuburbPlanningRequest(
                initialRequest.settlementId(),
                refinedSurvey,
                initialRequest.seed(),
                initialRequest.settings()
        );
        return planner.plan(refinedRequest);
    }

    private static Set<GridPoint> points(TerrainSurvey survey) {
        Set<GridPoint> points = new LinkedHashSet<>();
        for (int z = survey.bounds().minZ(); z < survey.bounds().maxZExclusive(); z++) {
            for (int x = survey.bounds().minX(); x < survey.bounds().maxXExclusive(); x++) {
                points.add(new GridPoint(x, z));
            }
        }
        return Set.copyOf(points);
    }

    private static Set<GridPoint> refinementFootprint(SuburbPlanningResult result) {
        Set<GridPoint> points = new LinkedHashSet<>(SettlementPlanFootprint.points(result.plan().orElseThrow()));
        result.terrainPreparationPlan().orElseThrow().columns().forEach(column -> points.add(column.point()));
        return Set.copyOf(points);
    }
}
