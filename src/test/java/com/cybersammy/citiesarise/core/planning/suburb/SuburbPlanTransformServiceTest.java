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
import com.cybersammy.citiesarise.core.transform.PlanTransform;
import com.cybersammy.citiesarise.core.transform.TransformPipeline;
import java.util.List;
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
}
