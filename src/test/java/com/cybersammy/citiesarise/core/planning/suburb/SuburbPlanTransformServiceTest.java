package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.transform.PlanTransform;
import com.cybersammy.citiesarise.core.transform.TransformPipeline;
import com.cybersammy.citiesarise.core.validation.PlanValidationErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SuburbPlanTransformServiceTest {
    @Test
    void keepsRejectedPlanningResultUnchanged() {
        SuburbPlanningResult result = SuburbPlanningResult.rejected(SuburbPlanningFailureReason.SURVEY_TOO_SMALL);
        SuburbPlanTransformService service = new SuburbPlanTransformService(TransformPipeline.empty());

        SuburbPlanningResult transformedResult = service.apply(result, 42L);

        assertSame(result, transformedResult);
    }

    @Test
    void returnsInvalidResultWhenTransformBreaksPlanValidity() {
        SuburbPlanTransformService service = new SuburbPlanTransformService(TransformPipeline.of(invalidTransform()));

        SuburbPlanningResult result = service.apply(SuburbPlanningResult.success(validPlan()), 42L);

        assertTrue(result.failureReason().isPresent());
        assertEquals(SuburbPlanningFailureReason.INVALID_PLAN, result.failureReason().orElseThrow());
        assertEquals(1, result.validationErrors().size());
    }

    @Test
    void preservesTerrainPreparationPlanAcrossSemanticTransforms() {
        SuburbPlanningResult planned = SuburbPlanner.defaults().plan(new SuburbPlanningRequest(
                new PlanElementId("settlement/prepared"),
                flatSurvey(),
                42L,
                SuburbPlanningSettings.defaults()
        ));
        SuburbPlanTransformService service = new SuburbPlanTransformService(TransformPipeline.empty());

        SuburbPlanningResult transformed = service.apply(planned, 42L);

        assertEquals(planned.terrainPreparationPlan(), transformed.terrainPreparationPlan());
    }

    @Test
    void rejectsGeometryTransformThatLeavesPreparationPlanStale() {
        SuburbPlanningResult planned = SuburbPlanner.defaults().plan(new SuburbPlanningRequest(
                new PlanElementId("settlement/prepared"),
                flatSurvey(),
                42L,
                SuburbPlanningSettings.defaults()
        ));
        SuburbPlanTransformService service = new SuburbPlanTransformService(
                TransformPipeline.of(shrinkFirstBuildingTransform())
        );

        SuburbPlanningResult transformed = service.apply(planned, 42L);

        assertEquals(SuburbPlanningFailureReason.INVALID_PLAN, transformed.failureReason().orElseThrow());
        assertEquals(
                PlanValidationErrorCode.TERRAIN_PREPARATION_MISMATCH,
                transformed.validationErrors().getFirst().code()
        );
    }

    private static PlanTransform invalidTransform() {
        return (plan, context) -> new SettlementPlan(
                plan.id(),
                plan.roadGraph(),
                plan.parcels(),
                List.of(buildingSlot(new PlanElementId("building/missing"), new PlanElementId("parcel/missing"))),
                plan.tags(),
                plan.properties()
        );
    }

    private static SettlementPlan validPlan() {
        PlanElementId parcelId = new PlanElementId("parcel/test");

        return new SettlementPlan(
                new PlanElementId("settlement/test"),
                RoadGraph.empty(),
                List.of(new Parcel(parcelId, bounds(0, 0, 8, 8), Set.of(), PlanProperties.empty())),
                List.of(buildingSlot(new PlanElementId("building/test"), parcelId)),
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static BuildingSlot buildingSlot(PlanElementId id, PlanElementId parcelId) {
        return new BuildingSlot(id, parcelId, bounds(1, 1, 4, 4), Set.of(), PlanProperties.empty());
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(new GridPoint(x, z), new GridSize(width, depth));
    }

    private static PlanTransform shrinkFirstBuildingTransform() {
        return (plan, context) -> {
            BuildingSlot original = plan.buildingSlots().getFirst();
            GridBounds originalBounds = original.bounds();
            BuildingSlot changed = new BuildingSlot(
                    original.id(),
                    original.parcelId(),
                    new GridBounds(
                            originalBounds.origin(),
                            new GridSize(originalBounds.size().width() - 1, originalBounds.size().depth())
                    ),
                    original.tags(),
                    original.properties()
            );
            List<BuildingSlot> buildings = new ArrayList<>(plan.buildingSlots());
            buildings.set(0, changed);
            return new SettlementPlan(
                    plan.id(),
                    plan.roadGraph(),
                    plan.parcels(),
                    buildings,
                    plan.tags(),
                    plan.properties()
            );
        };
    }

    private static TerrainSurvey flatSurvey() {
        GridBounds bounds = bounds(0, 0, 40, 30);
        return TerrainSurvey.sample(
                bounds,
                point -> Optional.of(new TerrainCell(
                        point,
                        64,
                        false,
                        0.0,
                        BiomeCategory.PLAINS,
                        TerrainCategory.BUILDABLE
                ))
        );
    }
}
