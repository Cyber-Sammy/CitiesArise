package com.cybersammy.citiesarise.minecraft.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class DebugPlacementPlanConverterTest {
    private final DebugPlacementPlanConverter converter = new DebugPlacementPlanConverter();

    @Test
    void convertsRoadSegmentToFilledSurfaceOperations() {
        PlanElementId roadId = id("road");
        SettlementPlan plan = planWithRoad(roadId, point(0, 0), point(4, 0), 3);

        DebugPlacementPlan placementPlan = converter.convert(plan);

        assertEquals(15, placementPlan.size());
        assertOperation(placementPlan, point(0, -1), DebugPlacementRole.ROAD_SURFACE, roadId);
        assertOperation(placementPlan, point(4, 1), DebugPlacementRole.ROAD_SURFACE, roadId);
    }

    @Test
    void convertsParcelsAndBuildingSlotsToOutlineOperations() {
        Parcel parcel = parcel(id("parcel"), bounds(10, 10, 4, 4));
        BuildingSlot buildingSlot = buildingSlot(id("slot"), parcel.id(), bounds(11, 11, 2, 2));
        SettlementPlan plan = plan(RoadGraph.empty(), List.of(parcel), List.of(buildingSlot));

        DebugPlacementPlan placementPlan = converter.convert(plan);

        assertEquals(16, placementPlan.size());
        assertOperation(placementPlan, point(10, 10), DebugPlacementRole.PARCEL_MARKER, parcel.id());
        assertOperation(placementPlan, point(13, 13), DebugPlacementRole.PARCEL_MARKER, parcel.id());
        assertOperation(placementPlan, point(11, 11), DebugPlacementRole.BUILDING_SLOT_MARKER, buildingSlot.id());
        assertOperation(placementPlan, point(12, 12), DebugPlacementRole.BUILDING_SLOT_MARKER, buildingSlot.id());
    }

    @Test
    void keepsFirstOperationWhenPlacementPointsOverlap() {
        PlanElementId roadId = id("road");
        Parcel parcel = parcel(id("parcel"), bounds(0, -1, 5, 3));
        SettlementPlan plan = plan(roadGraph(roadId, point(0, 0), point(4, 0), 3), List.of(parcel), List.of());

        DebugPlacementPlan placementPlan = converter.convert(plan);

        assertEquals(15, placementPlan.size());
        assertOperation(placementPlan, point(0, -1), DebugPlacementRole.ROAD_SURFACE, roadId);
    }

    @Test
    void rejectsDiagonalRoadSegments() {
        SettlementPlan plan = planWithRoad(id("road"), point(0, 0), point(4, 4), 3);

        assertThrows(IllegalArgumentException.class, () -> converter.convert(plan));
    }

    private static void assertOperation(
            DebugPlacementPlan placementPlan,
            GridPoint point,
            DebugPlacementRole role,
            PlanElementId sourceElementId
    ) {
        DebugBlockPlacementOperation operation = operationsByPoint(placementPlan).get(point);

        assertEquals(role, operation.role());
        assertEquals(sourceElementId, operation.sourceElementId());
    }

    private static Map<GridPoint, DebugBlockPlacementOperation> operationsByPoint(DebugPlacementPlan placementPlan) {
        return placementPlan.operations()
                .stream()
                .collect(Collectors.toMap(DebugBlockPlacementOperation::point, Function.identity()));
    }

    private static SettlementPlan planWithRoad(PlanElementId roadId, GridPoint start, GridPoint end, int width) {
        return plan(roadGraph(roadId, start, end, width), List.of(), List.of());
    }

    private static RoadGraph roadGraph(PlanElementId roadId, GridPoint start, GridPoint end, int width) {
        PlanElementId startId = id("start");
        PlanElementId endId = id("end");

        return new RoadGraph(
                List.of(roadNode(startId, start), roadNode(endId, end)),
                List.of(roadSegment(roadId, startId, endId, width))
        );
    }

    private static SettlementPlan plan(RoadGraph roadGraph, List<Parcel> parcels, List<BuildingSlot> buildingSlots) {
        return new SettlementPlan(
                id("settlement"),
                roadGraph,
                parcels,
                buildingSlots,
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static RoadNode roadNode(PlanElementId id, GridPoint point) {
        return new RoadNode(id, point, Set.of(), PlanProperties.empty());
    }

    private static RoadSegment roadSegment(
            PlanElementId id,
            PlanElementId startNodeId,
            PlanElementId endNodeId,
            int width
    ) {
        return new RoadSegment(id, startNodeId, endNodeId, width, Set.of(), PlanProperties.empty());
    }

    private static Parcel parcel(PlanElementId id, GridBounds bounds) {
        return new Parcel(id, bounds, Set.of(), PlanProperties.empty());
    }

    private static BuildingSlot buildingSlot(PlanElementId id, PlanElementId parcelId, GridBounds bounds) {
        return new BuildingSlot(id, parcelId, bounds, Set.of(), PlanProperties.empty());
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(point(x, z), new GridSize(width, depth));
    }

    private static GridPoint point(int x, int z) {
        return new GridPoint(x, z);
    }

    private static PlanElementId id(String value) {
        return new PlanElementId("test/" + value);
    }
}
