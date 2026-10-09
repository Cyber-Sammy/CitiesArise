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
                // Mixed interpolated/exact heights can temporarily disconnect districts. Before
                // rejecting a previously viable city, resolve its bounded area consistently once.
                return initialRequest.settings().districts().maxCount()>1
                        ? refineCompleteSurvey(planner,terrainProvider,initialRequest,currentResult) : currentResult;
            }
            Set<GridPoint> footprint = refinementFootprint(currentResult);
            footprint = new LinkedHashSet<>(footprint);
            footprint.addAll(com.cybersammy.citiesarise.core.road.BridgePlanner.probePoints(
                    initialRequest, currentResult.plan().orElseThrow()));
            if (checkedPoints.containsAll(footprint)) {
                return repairSupport(planner, terrainProvider, initialRequest, currentResult);
            }
            checkedPoints.addAll(footprint);
            Optional<TerrainSurvey> refinedSurvey = terrainProvider.sampleWithExactWaterMask(
                    initialRequest.survey().bounds(),
                    java.util.Collections.unmodifiableSet(new LinkedHashSet<>(checkedPoints))
            );
            if (refinedSurvey.isEmpty()) {
                return repairSupport(planner, terrainProvider, initialRequest, currentResult);
            }
            currentResult = planner.plan(withSurvey(initialRequest, refinedSurvey.orElseThrow()));
        }

        if (!currentResult.successful()) {
            return initialRequest.settings().districts().maxCount()>1
                    ? refineCompleteSurvey(planner,terrainProvider,initialRequest,currentResult) : currentResult;
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
            return currentResult.successful() ? repairSupport(planner, terrainProvider, initialRequest, currentResult) : currentResult;
        }
        var request = withSurvey(initialRequest, refinedSurvey.orElseThrow());
        return planner.plan(request, (candidateRequest, candidate) ->
                TerrainSupportAcceptance.validate(terrainProvider, candidateRequest.survey(), candidate));
    }

    private static SuburbPlanningResult repairSupport(SuburbPlanner planner, WorldgenTerrainSurveyProvider terrain,
            SuburbPlanningRequest request, SuburbPlanningResult result) {
        var checked = TerrainSupportAcceptance.validate(terrain, request.survey(), result);
        if (checked.successful() || (request.settings().districts().maxCount() == 1
                && result.plan().orElseThrow().roadGraph().bridges().isEmpty())) return checked;
        // Repair can move a district outside the refined footprint. Resolve the whole bounded survey first.
        var exact = terrain.sampleWithExactWaterMask(request.survey().bounds(), points(request.survey())).orElse(request.survey());
        return planner.plan(withSurvey(request, exact), (candidateRequest, candidate) ->
                TerrainSupportAcceptance.validate(terrain, candidateRequest.survey(), candidate));
    }

    private static SuburbPlanningRequest withSurvey(SuburbPlanningRequest initialRequest, TerrainSurvey refinedSurvey) {
        return new SuburbPlanningRequest(
                initialRequest.settlementId(),
                refinedSurvey,
                initialRequest.seed(),
                initialRequest.settings(),
                initialRequest.terrainResponsePolicy()
        );
    }

    private static Set<GridPoint> points(TerrainSurvey survey) {
        Set<GridPoint> points = new LinkedHashSet<>();
        for (int z = survey.bounds().minZ(); z < survey.bounds().maxZExclusive(); z++) {
            for (int x = survey.bounds().minX(); x < survey.bounds().maxXExclusive(); x++) {
                points.add(new GridPoint(x, z));
            }
        }
        return java.util.Collections.unmodifiableSet(points);
    }

    private static Set<GridPoint> refinementFootprint(SuburbPlanningResult result) {
        Set<GridPoint> points = new LinkedHashSet<>(SettlementPlanFootprint.points(result.plan().orElseThrow()));
        result.terrainPreparationPlan().orElseThrow().columns().forEach(column -> points.add(column.point()));
        return java.util.Collections.unmodifiableSet(points);
    }
}
