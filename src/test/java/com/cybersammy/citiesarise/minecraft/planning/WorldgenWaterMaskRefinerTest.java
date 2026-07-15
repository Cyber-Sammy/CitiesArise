package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanner;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningFailureReason;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningRequest;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class WorldgenWaterMaskRefinerTest {
    private static final GridBounds BOUNDS = new GridBounds(new GridPoint(0, 0), new GridSize(40, 30));

    @Test
    void rejectsExactWaterInsidePreliminaryPlanFootprint() {
        SuburbPlanner planner = SuburbPlanner.defaults();
        SuburbPlanningRequest request = request(flatSurvey());
        SuburbPlanningResult initialResult = planner.plan(request);
        ExactWaterProvider terrainProvider = new ExactWaterProvider();

        SuburbPlanningResult result = WorldgenWaterMaskRefiner.refine(
                planner,
                terrainProvider,
                request,
                initialResult
        );

        assertFalse(result.successful());
        assertEquals(SuburbPlanningFailureReason.UNSUITABLE_TERRAIN, result.failureReason().orElseThrow());
        assertEquals(1, terrainProvider.refinedSamples);
        assertTrue(terrainProvider.checkedPoints.contains(result.terrainDiagnostic().orElseThrow().cell().point()));
        assertTrue(terrainProvider.checkedPoints.size() < BOUNDS.size().width() * BOUNDS.size().depth());
    }

    @Test
    void skipsRefinementWhenInitialPlanningFailed() {
        SuburbPlanner planner = SuburbPlanner.defaults();
        SuburbPlanningRequest request = request(waterSurvey());
        SuburbPlanningResult initialResult = planner.plan(request);
        ExactWaterProvider terrainProvider = new ExactWaterProvider();

        SuburbPlanningResult result = WorldgenWaterMaskRefiner.refine(
                planner,
                terrainProvider,
                request,
                initialResult
        );

        assertSame(initialResult, result);
        assertEquals(0, terrainProvider.refinedSamples);
    }

    @Test
    void preservesSuccessfulResultWhenProviderDoesNotSupportRefinement() {
        SuburbPlanner planner = SuburbPlanner.defaults();
        SuburbPlanningRequest request = request(flatSurvey());
        SuburbPlanningResult initialResult = planner.plan(request);
        WorldgenTerrainSurveyProvider terrainProvider = ignored -> flatSurvey();

        SuburbPlanningResult result = WorldgenWaterMaskRefiner.refine(
                planner,
                terrainProvider,
                request,
                initialResult
        );

        assertSame(initialResult, result);
    }

    private static SuburbPlanningRequest request(TerrainSurvey survey) {
        return new SuburbPlanningRequest(
                new PlanElementId("cities_arise:test"),
                survey,
                42L,
                SuburbPlanningSettings.defaults()
        );
    }

    private static TerrainSurvey flatSurvey() {
        return survey(Optional.empty());
    }

    private static TerrainSurvey waterSurvey() {
        return survey(Optional.of(new GridPoint(0, 14)));
    }

    private static TerrainSurvey survey(Optional<GridPoint> waterPoint) {
        return TerrainSurvey.sample(BOUNDS, point -> Optional.of(cell(point, waterPoint)));
    }

    private static TerrainCell cell(GridPoint point, Optional<GridPoint> waterPoint) {
        boolean water = waterPoint.filter(point::equals).isPresent();
        TerrainCategory category = water ? TerrainCategory.BLOCKED : TerrainCategory.BUILDABLE;
        return new TerrainCell(point, 64, water, 0.0, BiomeCategory.PLAINS, category);
    }

    private static final class ExactWaterProvider implements WorldgenTerrainSurveyProvider {
        private int refinedSamples;
        private Set<GridPoint> checkedPoints = Set.of();

        @Override
        public TerrainSurvey sample(GridBounds bounds) {
            return flatSurvey();
        }

        @Override
        public Optional<TerrainSurvey> sampleWithExactWaterMask(
                GridBounds bounds,
                Set<GridPoint> waterCheckPoints
        ) {
            refinedSamples++;
            checkedPoints = Set.copyOf(waterCheckPoints);
            return Optional.of(survey(Optional.of(checkedPoints.iterator().next())));
        }
    }
}
