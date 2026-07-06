package com.cybersammy.citiesarise.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanTag;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PlanValidatorTest {
    private final PlanValidator validator = new PlanValidator();

    @Test
    void acceptsSmallValidSettlementPlan() {
        SettlementPlan plan = validPlan();

        assertTrue(validator.validate(plan).isEmpty());
    }

    @Test
    void reportsDuplicateElementIdsAcrossPlanElements() {
        PlanElementId duplicateId = id("duplicate");
        RoadGraph roadGraph = new RoadGraph(
                List.of(
                        roadNode(duplicateId, 0, 0),
                        roadNode(id("node-b"), 8, 0)
                ),
                List.of(roadSegment(duplicateId, duplicateId, id("node-b")))
        );
        SettlementPlan plan = new SettlementPlan(
                id("settlement"),
                roadGraph,
                List.of(parcel(id("parcel"), 0, 1, 8, 8)),
                List.of(),
                Set.of(),
                PlanProperties.empty()
        );

        List<PlanValidationError> errors = validator.validate(plan);

        assertErrorCodes(errors, PlanValidationErrorCode.DUPLICATE_ELEMENT_ID);
    }

    @Test
    void reportsMissingStartAndEndRoadNodes() {
        RoadGraph roadGraph = new RoadGraph(
                List.of(),
                List.of(roadSegment(id("road"), id("missing-a"), id("missing-b")))
        );
        SettlementPlan plan = new SettlementPlan(
                id("settlement"),
                roadGraph,
                List.of(),
                List.of(),
                Set.of(),
                PlanProperties.empty()
        );

        List<PlanValidationError> errors = validator.validate(plan);

        assertErrorCodes(
                errors,
                PlanValidationErrorCode.MISSING_ROAD_NODE,
                PlanValidationErrorCode.MISSING_ROAD_NODE
        );
    }

    @Test
    void reportsBuildingSlotOutsideParcel() {
        Parcel parcel = parcel(id("parcel"), 0, 0, 10, 10);
        BuildingSlot buildingSlot = buildingSlot(id("slot"), parcel.id(), 8, 8, 4, 4);
        SettlementPlan plan = planWithParcelsAndSlots(List.of(parcel), List.of(buildingSlot));

        List<PlanValidationError> errors = validator.validate(plan);

        assertErrorCodes(errors, PlanValidationErrorCode.BUILDING_SLOT_OUTSIDE_PARCEL);
    }

    @Test
    void reportsBuildingSlotReferencingMissingParcel() {
        BuildingSlot buildingSlot = buildingSlot(id("slot"), id("missing-parcel"), 0, 0, 4, 4);
        SettlementPlan plan = planWithParcelsAndSlots(List.of(), List.of(buildingSlot));

        List<PlanValidationError> errors = validator.validate(plan);

        assertErrorCodes(errors, PlanValidationErrorCode.MISSING_PARCEL);
    }

    @Test
    void reportsMultipleValidationErrorsAtOnce() {
        RoadGraph roadGraph = new RoadGraph(
                List.of(roadNode(id("node-a"), 0, 0)),
                List.of(roadSegment(id("node-a"), id("node-a"), id("missing-node")))
        );
        BuildingSlot buildingSlot = buildingSlot(id("slot"), id("missing-parcel"), 0, 0, 4, 4);
        SettlementPlan plan = new SettlementPlan(
                id("settlement"),
                roadGraph,
                List.of(),
                List.of(buildingSlot),
                Set.of(),
                PlanProperties.empty()
        );

        List<PlanValidationError> errors = validator.validate(plan);

        assertErrorCodes(
                errors,
                PlanValidationErrorCode.DUPLICATE_ELEMENT_ID,
                PlanValidationErrorCode.MISSING_ROAD_NODE,
                PlanValidationErrorCode.MISSING_PARCEL
        );
    }

    private static SettlementPlan validPlan() {
        PlanElementId nodeA = id("node-a");
        PlanElementId nodeB = id("node-b");
        Parcel parcel = parcel(id("parcel"), 0, 2, 12, 10);
        BuildingSlot buildingSlot = buildingSlot(id("slot"), parcel.id(), 2, 4, 6, 4);
        RoadGraph roadGraph = new RoadGraph(
                List.of(roadNode(nodeA, 0, 0), roadNode(nodeB, 12, 0)),
                List.of(roadSegment(id("road"), nodeA, nodeB))
        );

        return new SettlementPlan(
                id("settlement"),
                roadGraph,
                List.of(parcel),
                List.of(buildingSlot),
                Set.of(new PlanTag("suburban")),
                PlanProperties.empty()
        );
    }

    private static SettlementPlan planWithParcelsAndSlots(List<Parcel> parcels, List<BuildingSlot> buildingSlots) {
        return new SettlementPlan(
                id("settlement"),
                RoadGraph.empty(),
                parcels,
                buildingSlots,
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static RoadNode roadNode(PlanElementId id, int x, int z) {
        return new RoadNode(id, new GridPoint(x, z), Set.of(), PlanProperties.empty());
    }

    private static RoadSegment roadSegment(PlanElementId id, PlanElementId startNodeId, PlanElementId endNodeId) {
        return new RoadSegment(id, startNodeId, endNodeId, 5, Set.of(), PlanProperties.empty());
    }

    private static Parcel parcel(PlanElementId id, int x, int z, int width, int depth) {
        return new Parcel(id, bounds(x, z, width, depth), Set.of(), PlanProperties.empty());
    }

    private static BuildingSlot buildingSlot(
            PlanElementId id,
            PlanElementId parcelId,
            int x,
            int z,
            int width,
            int depth
    ) {
        return new BuildingSlot(id, parcelId, bounds(x, z, width, depth), Set.of(), PlanProperties.empty());
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(new GridPoint(x, z), new GridSize(width, depth));
    }

    private static PlanElementId id(String value) {
        return new PlanElementId(value);
    }

    private static void assertErrorCodes(List<PlanValidationError> errors, PlanValidationErrorCode... expectedCodes) {
        List<PlanValidationErrorCode> actualCodes = errors.stream()
                .map(PlanValidationError::code)
                .toList();

        assertEquals(List.of(expectedCodes), actualCodes);
    }
}
