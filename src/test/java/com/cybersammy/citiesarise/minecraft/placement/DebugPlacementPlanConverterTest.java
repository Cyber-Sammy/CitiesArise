package com.cybersammy.citiesarise.minecraft.placement;

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
import com.cybersammy.citiesarise.core.model.PlanTag;
import com.cybersammy.citiesarise.core.model.PlanTags;
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

        assertEquals(30, placementPlan.size());
        assertOperation(placementPlan, point(0, -1), 0, DebugPlacementRole.ROAD_SURFACE, roadId);
        assertOperation(placementPlan, point(4, 1), 0, DebugPlacementRole.ROAD_SURFACE, roadId);
        assertOperation(placementPlan, point(0, -1), -1, DebugPlacementRole.FOUNDATION, roadId);
    }

    @Test
    void convertsWornRoadSegmentToWornSurfaceOperations() {
        PlanElementId roadId = id("road");
        SettlementPlan plan = planWithRoad(roadId, point(0, 0), point(4, 0), 3, Set.of(PlanTags.WORN));

        DebugPlacementPlan placementPlan = converter.convert(plan);

        assertOperation(placementPlan, point(0, -1), 0, DebugPlacementRole.WORN_ROAD_SURFACE, roadId);
        assertOperation(placementPlan, point(4, 1), 0, DebugPlacementRole.WORN_ROAD_SURFACE, roadId);
    }

    @Test
    void carriesSemanticPlatformElevationIntoPlacementOperations() {
        PlanElementId roadId = id("road");
        PlanElementId startId = id("start");
        PlanElementId endId = id("end");
        RoadSegment segment = new RoadSegment(
                roadId,
                startId,
                endId,
                3,
                Set.of(),
                PlanProperties.of(PlanPropertyKeys.PLATFORM_Y, "72")
        );
        SettlementPlan plan = plan(
                new RoadGraph(
                        List.of(roadNode(startId, point(0, 0)), roadNode(endId, point(4, 0))),
                        List.of(segment)
                ),
                List.of(),
                List.of()
        );

        DebugPlacementPlan placementPlan = converter.convert(plan);

        assertEquals(72, placementPlan.operations().getFirst().platformY().orElseThrow());
        assertTrue(placementPlan.operations().stream().allMatch(operation -> operation.platformY().orElseThrow() == 72));
    }

    @Test
    void rejectsNonIntegerPlatformElevation() {
        PlanElementId roadId = id("road");
        PlanElementId startId = id("start");
        PlanElementId endId = id("end");
        RoadSegment segment = new RoadSegment(
                roadId,
                startId,
                endId,
                3,
                Set.of(),
                PlanProperties.of(PlanPropertyKeys.PLATFORM_Y, "invalid")
        );
        SettlementPlan plan = plan(
                new RoadGraph(
                        List.of(roadNode(startId, point(0, 0)), roadNode(endId, point(4, 0))),
                        List.of(segment)
                ),
                List.of(),
                List.of()
        );

        assertThrows(IllegalArgumentException.class, () -> converter.convert(plan));
    }

    @Test
    void convertsParcelsAndBuildingSlotsToFoundationAndHouseOperations() {
        Parcel parcel = parcel(id("parcel"), bounds(10, 10, 4, 4));
        BuildingSlot buildingSlot = buildingSlot(id("slot"), parcel.id(), bounds(11, 11, 2, 2));
        SettlementPlan plan = plan(RoadGraph.empty(), List.of(parcel), List.of(buildingSlot));

        DebugPlacementPlan placementPlan = converter.convert(plan);

        assertEquals(38, placementPlan.size());
        assertOperation(placementPlan, point(10, 10), 0, DebugPlacementRole.PARCEL_BOUNDARY, parcel.id());
        assertOperation(placementPlan, point(12, 12), 0, DebugPlacementRole.BUILDING_FLOOR, buildingSlot.id());
        assertOperation(placementPlan, point(11, 11), -1, DebugPlacementRole.FOUNDATION, buildingSlot.id());
        assertOperation(placementPlan, point(11, 11), 1, DebugPlacementRole.BUILDING_WALL, buildingSlot.id());
        assertOperation(placementPlan, point(11, 11), 3, DebugPlacementRole.BUILDING_WALL, buildingSlot.id());
        assertOperation(placementPlan, point(12, 11), 1, DebugPlacementRole.BUILDING_DOORWAY, buildingSlot.id());
        assertOperation(placementPlan, point(12, 12), 4, DebugPlacementRole.BUILDING_ROOF, buildingSlot.id());
        assertOperation(placementPlan, point(11, 12), 5, DebugPlacementRole.BUILDING_ROOF, buildingSlot.id());
    }

    @Test
    void convertsDecayedBuildingSlotToDecayedHouseOperations() {
        Parcel parcel = parcel(id("parcel"), bounds(10, 10, 4, 4));
        BuildingSlot buildingSlot = decayedBuildingSlot(id("slot"), parcel.id(), bounds(11, 11, 2, 2));
        SettlementPlan plan = plan(RoadGraph.empty(), List.of(parcel), List.of(buildingSlot));

        DebugPlacementPlan placementPlan = converter.convert(plan);

        assertOperation(placementPlan, point(11, 11), 1, DebugPlacementRole.DECAYED_BUILDING_WALL, buildingSlot.id());
        assertOperation(placementPlan, point(12, 12), 4, DebugPlacementRole.DECAYED_BUILDING_ROOF, buildingSlot.id());
        assertOperation(placementPlan, point(11, 12), 5, DebugPlacementRole.DECAYED_BUILDING_ROOF, buildingSlot.id());
    }

    @Test
    void rendersRoofRidgeAlongLongerBuildingAxis() {
        Parcel parcel = parcel(id("parcel"), bounds(10, 10, 14, 16));
        BuildingSlot buildingSlot = buildingSlot(id("slot"), parcel.id(), bounds(12, 12, 10, 12));
        SettlementPlan plan = plan(RoadGraph.empty(), List.of(parcel), List.of(buildingSlot));

        DebugPlacementPlan placementPlan = converter.convert(plan);

        assertOperation(placementPlan, point(17, 12), 5, DebugPlacementRole.BUILDING_ROOF, buildingSlot.id());
        assertOperation(placementPlan, point(17, 23), 5, DebugPlacementRole.BUILDING_ROOF, buildingSlot.id());
        assertOperation(placementPlan, point(17, 12), 1, DebugPlacementRole.BUILDING_DOORWAY, buildingSlot.id());
    }

    @Test
    void keepsRoadSurfaceWhenPlacementPointsOverlap() {
        PlanElementId roadId = id("road");
        Parcel parcel = parcel(id("parcel"), bounds(0, -1, 5, 3));
        SettlementPlan plan = plan(roadGraph(roadId, point(0, 0), point(4, 0), 3), List.of(parcel), List.of());

        DebugPlacementPlan placementPlan = converter.convert(plan);

        assertEquals(30, placementPlan.size());
        assertOperation(placementPlan, point(0, -1), 0, DebugPlacementRole.ROAD_SURFACE, roadId);
        assertOperation(placementPlan, point(0, -1), -1, DebugPlacementRole.FOUNDATION, roadId);
    }

    @Test
    void rejectsDiagonalRoadSegments() {
        SettlementPlan plan = planWithRoad(id("road"), point(0, 0), point(4, 4), 3);

        assertThrows(IllegalArgumentException.class, () -> converter.convert(plan));
    }

    private static void assertOperation(
            DebugPlacementPlan placementPlan,
            GridPoint point,
            int verticalOffset,
            DebugPlacementRole role,
            PlanElementId sourceElementId
    ) {
        DebugBlockPlacementOperation operation = operationsByPosition(placementPlan)
                .get(new DebugPlacementPosition(point, verticalOffset));

        assertEquals(role, operation.role());
        assertEquals(sourceElementId, operation.sourceElementId());
    }

    private static Map<DebugPlacementPosition, DebugBlockPlacementOperation> operationsByPosition(
            DebugPlacementPlan placementPlan
    ) {
        return placementPlan.operations()
                .stream()
                .collect(Collectors.toMap(DebugBlockPlacementOperation::position, Function.identity()));
    }

    private static SettlementPlan planWithRoad(PlanElementId roadId, GridPoint start, GridPoint end, int width) {
        return planWithRoad(roadId, start, end, width, Set.of());
    }

    private static SettlementPlan planWithRoad(
            PlanElementId roadId,
            GridPoint start,
            GridPoint end,
            int width,
            Set<PlanTag> roadTags
    ) {
        return plan(roadGraph(roadId, start, end, width, roadTags), List.of(), List.of());
    }

    private static RoadGraph roadGraph(PlanElementId roadId, GridPoint start, GridPoint end, int width) {
        return roadGraph(roadId, start, end, width, Set.of());
    }

    private static RoadGraph roadGraph(
            PlanElementId roadId,
            GridPoint start,
            GridPoint end,
            int width,
            Set<PlanTag> roadTags
    ) {
        PlanElementId startId = id("start");
        PlanElementId endId = id("end");

        return new RoadGraph(
                List.of(roadNode(startId, start), roadNode(endId, end)),
                List.of(roadSegment(roadId, startId, endId, width, roadTags))
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
        return roadSegment(id, startNodeId, endNodeId, width, Set.of());
    }

    private static RoadSegment roadSegment(
            PlanElementId id,
            PlanElementId startNodeId,
            PlanElementId endNodeId,
            int width,
            Set<PlanTag> tags
    ) {
        return new RoadSegment(id, startNodeId, endNodeId, width, tags, PlanProperties.empty());
    }

    private static Parcel parcel(PlanElementId id, GridBounds bounds) {
        return new Parcel(id, bounds, Set.of(), PlanProperties.empty());
    }

    private static BuildingSlot buildingSlot(PlanElementId id, PlanElementId parcelId, GridBounds bounds) {
        return new BuildingSlot(id, parcelId, bounds, Set.of(), PlanProperties.empty());
    }

    private static BuildingSlot decayedBuildingSlot(PlanElementId id, PlanElementId parcelId, GridBounds bounds) {
        return new BuildingSlot(id, parcelId, bounds, Set.of(PlanTags.DECAYED), PlanProperties.empty());
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
