package com.cybersammy.citiesarise.core.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanPropertyKeys;
import com.cybersammy.citiesarise.core.model.PlanTags;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.validation.PlanValidator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LightDecayTransformTest {
    private final PlanValidator validator = new PlanValidator();

    @Test
    void transformsSelectedRoadsAndBuildingSlotsWithoutChangingGeometry() {
        SettlementPlan plan = validPlan();
        LightDecayTransform transform = new LightDecayTransform(1.0, 1.0, 2);

        SettlementPlan transformedPlan = transform.apply(plan, new TransformContext(42L));

        assertEquals(plan.id(), transformedPlan.id());
        assertEquals(plan.parcels(), transformedPlan.parcels());
        assertEquals(plan.roadGraph().nodes(), transformedPlan.roadGraph().nodes());
        assertTrue(validator.validate(transformedPlan).isEmpty());
        assertAllRoadsWorn(transformedPlan);
        assertAllBuildingsDecayed(transformedPlan);
    }

    @Test
    void zeroChancesLeavePlanSemanticsUnchanged() {
        SettlementPlan plan = validPlan();
        LightDecayTransform transform = new LightDecayTransform(0.0, 0.0, 1);

        SettlementPlan transformedPlan = transform.apply(plan, new TransformContext(42L));

        assertEquals(plan, transformedPlan);
    }

    @Test
    void sameInputAndSeedProduceSameTransformedPlan() {
        SettlementPlan plan = validPlan();
        LightDecayTransform transform = LightDecayTransform.defaults();
        TransformContext context = new TransformContext(123456L);

        SettlementPlan firstPlan = transform.apply(plan, context);
        SettlementPlan secondPlan = transform.apply(plan, context);

        assertEquals(firstPlan, secondPlan);
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new LightDecayTransform(-0.1, 0.0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LightDecayTransform(0.0, 1.1, 1));
        assertThrows(IllegalArgumentException.class, () -> new LightDecayTransform(Double.NaN, 0.0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LightDecayTransform(0.0, 0.0, 0));
    }

    private static void assertAllRoadsWorn(SettlementPlan plan) {
        for (RoadSegment segment : plan.roadGraph().segments()) {
            assertTrue(segment.tags().contains(PlanTags.WORN));
            assertDecayProperties(segment.properties(), "2");
        }
    }

    private static void assertAllBuildingsDecayed(SettlementPlan plan) {
        for (BuildingSlot buildingSlot : plan.buildingSlots()) {
            assertTrue(buildingSlot.tags().contains(PlanTags.DECAYED));
            assertDecayProperties(buildingSlot.properties(), "2");
        }
    }

    private static void assertDecayProperties(PlanProperties properties, String decayLevel) {
        assertEquals(decayLevel, properties.find(PlanPropertyKeys.DECAY_LEVEL).orElseThrow());
        assertEquals("light_decay", properties.find(PlanPropertyKeys.TRANSFORM_ID).orElseThrow());
    }

    private static SettlementPlan validPlan() {
        PlanElementId nodeA = id("node-a");
        PlanElementId nodeB = id("node-b");
        Parcel parcelA = parcel(id("parcel-a"), 0, 2);
        Parcel parcelB = parcel(id("parcel-b"), 12, 2);

        return new SettlementPlan(
                id("settlement"),
                roadGraph(nodeA, nodeB),
                List.of(parcelA, parcelB),
                List.of(
                        buildingSlot(id("slot-a"), parcelA.id(), 2, 4),
                        buildingSlot(id("slot-b"), parcelB.id(), 14, 4)
                ),
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static RoadGraph roadGraph(PlanElementId nodeA, PlanElementId nodeB) {
        return new RoadGraph(
                List.of(
                        new RoadNode(nodeA, new GridPoint(0, 0), Set.of(), PlanProperties.empty()),
                        new RoadNode(nodeB, new GridPoint(24, 0), Set.of(), PlanProperties.empty())
                ),
                List.of(new RoadSegment(id("road"), nodeA, nodeB, 5, Set.of(), PlanProperties.empty()))
        );
    }

    private static Parcel parcel(PlanElementId id, int x, int z) {
        return new Parcel(id, bounds(x, z, 10, 10), Set.of(), PlanProperties.empty());
    }

    private static BuildingSlot buildingSlot(PlanElementId id, PlanElementId parcelId, int x, int z) {
        return new BuildingSlot(id, parcelId, bounds(x, z, 4, 4), Set.of(), PlanProperties.empty());
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(new GridPoint(x, z), new GridSize(width, depth));
    }

    private static PlanElementId id(String value) {
        return new PlanElementId("test/" + value);
    }
}
