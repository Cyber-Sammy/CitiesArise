package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
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
        SuburbPlanningResult template = planner.plan(request(flatSurvey()));
        TerrainSurvey terrain = buildingShoulderSurvey(template.plan().orElseThrow());
        SuburbPlanningRequest request = request(terrain);
        SuburbPlanningResult initialResult = planner.plan(request);
        GridPoint shoulderPoint = initialResult.terrainPreparationPlan().orElseThrow().columns().stream()
                .filter(column -> column.type() == TerrainPreparationColumnType.BUILDING_SHOULDER)
                .findFirst()
                .orElseThrow()
                .point();
        ExactWaterProvider terrainProvider = new ExactWaterProvider(terrain, shoulderPoint);

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
        assertTrue(terrainProvider.checkedPoints.contains(shoulderPoint));
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

    private static TerrainSurvey buildingShoulderSurvey(SettlementPlan plan) {
        return TerrainSurvey.sample(BOUNDS, point -> Optional.of(new TerrainCell(
                point,
                belongsToBuilding(plan, point) ? 65 : 62,
                false,
                0.0,
                BiomeCategory.PLAINS,
                TerrainCategory.BUILDABLE
        )));
    }

    private static boolean belongsToBuilding(SettlementPlan plan, GridPoint point) {
        for (BuildingSlot slot : plan.buildingSlots()) {
            if (slot.bounds().contains(point)) {
                return true;
            }
        }
        return false;
    }

    private static TerrainCell cell(GridPoint point, Optional<GridPoint> waterPoint) {
        boolean water = waterPoint.filter(point::equals).isPresent();
        TerrainCategory category = water ? TerrainCategory.BLOCKED : TerrainCategory.BUILDABLE;
        return new TerrainCell(point, 64, water, 0.0, BiomeCategory.PLAINS, category);
    }

    private static final class ExactWaterProvider implements WorldgenTerrainSurveyProvider {
        private final TerrainSurvey terrain;
        private final GridPoint waterPoint;
        private int refinedSamples;
        private Set<GridPoint> checkedPoints = Set.of();

        private ExactWaterProvider() {
            this(flatSurvey(), null);
        }

        private ExactWaterProvider(TerrainSurvey terrain, GridPoint waterPoint) {
            this.terrain = terrain;
            this.waterPoint = waterPoint;
        }

        @Override
        public TerrainSurvey sample(GridBounds bounds) {
            return terrain;
        }

        @Override
        public Optional<TerrainSurvey> sampleWithExactWaterMask(
                GridBounds bounds,
                Set<GridPoint> waterCheckPoints
        ) {
            refinedSamples++;
            checkedPoints = Set.copyOf(waterCheckPoints);
            GridPoint exactWaterPoint = waterPoint == null ? checkedPoints.iterator().next() : waterPoint;
            return Optional.of(TerrainSurvey.sample(bounds, point -> Optional.of(exactCell(point, exactWaterPoint))));
        }

        private TerrainCell exactCell(GridPoint point, GridPoint exactWaterPoint) {
            TerrainCell source = terrain.findCell(point).orElseThrow();
            boolean water = point.equals(exactWaterPoint);
            TerrainCategory category = water ? TerrainCategory.BLOCKED : source.terrainCategory();
            return new TerrainCell(
                    point,
                    source.height(),
                    water,
                    source.slope(),
                    source.biomeCategory(),
                    category
            );
        }
    }
}
