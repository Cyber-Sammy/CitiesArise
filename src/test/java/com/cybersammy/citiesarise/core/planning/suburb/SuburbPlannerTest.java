package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanTag;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.validation.PlanValidator;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SuburbPlannerTest {
    private static final PlanElementId SETTLEMENT_ID = new PlanElementId("settlement/test");

    private final SuburbPlanner planner = SuburbPlanner.defaults();
    private final PlanValidator validator = new PlanValidator();

    @Test
    void createsValidSuburbPlanOnFlatTerrain() {
        SuburbPlanningResult result = planner.plan(request(flatSurvey(40, 30), 100L, SuburbPlanningSettings.defaults()));

        assertTrue(result.successful());
        SettlementPlan plan = result.plan().orElseThrow();
        assertTrue(validator.validate(plan).isEmpty());
        assertFalse(plan.roadGraph().nodes().isEmpty());
        assertFalse(plan.roadGraph().segments().isEmpty());
        assertEquals(6, plan.parcels().size());
        assertEquals(6, plan.buildingSlots().size());
    }

    @Test
    void producesSamePlanForSameSeedAndSurvey() {
        SuburbPlanningRequest request = request(flatSurvey(40, 30), 123L, SuburbPlanningSettings.defaults());

        SuburbPlanningResult first = planner.plan(request);
        SuburbPlanningResult second = planner.plan(request);

        assertEquals(first, second);
    }

    @Test
    void differentSeedsCanChangeRoadLayout() {
        SuburbPlanningResult first = planner.plan(request(flatSurvey(40, 30), 1L, SuburbPlanningSettings.defaults()));
        SuburbPlanningResult second = planner.plan(request(flatSurvey(40, 30), 3L, SuburbPlanningSettings.defaults()));

        assertTrue(first.successful());
        assertTrue(second.successful());
        assertFalse(first.plan().orElseThrow().roadGraph().equals(second.plan().orElseThrow().roadGraph()));
    }

    @Test
    void rejectsWaterTerrain() {
        SuburbPlanningResult result = planner.plan(request(waterSurvey(40, 30), 100L, SuburbPlanningSettings.defaults()));

        assertFalse(result.successful());
        assertEquals(Optional.of(SuburbPlanningFailureReason.UNSUITABLE_TERRAIN), result.failureReason());
    }

    @Test
    void rejectsSteepTerrain() {
        SuburbPlanningResult result = planner.plan(request(steepSurvey(40, 30), 100L, SuburbPlanningSettings.defaults()));

        assertFalse(result.successful());
        assertEquals(Optional.of(SuburbPlanningFailureReason.UNSUITABLE_TERRAIN), result.failureReason());
    }

    @Test
    void rejectsTooSmallSurvey() {
        SuburbPlanningResult result = planner.plan(request(flatSurvey(20, 20), 100L, SuburbPlanningSettings.defaults()));

        assertFalse(result.successful());
        assertEquals(Optional.of(SuburbPlanningFailureReason.SURVEY_TOO_SMALL), result.failureReason());
    }

    @Test
    void generatedRoadGraphIsConnected() {
        SettlementPlan plan = planner.plan(request(flatSurvey(40, 30), 100L, SuburbPlanningSettings.defaults()))
                .plan()
                .orElseThrow();

        assertTrue(isConnected(plan.roadGraph()));
    }

    @Test
    void generatedRoadGraphHasDeadEndSideRoad() {
        SettlementPlan plan = planner.plan(request(flatSurvey(40, 30), 100L, SuburbPlanningSettings.defaults()))
                .plan()
                .orElseThrow();

        assertTrue(hasDeadEndSegment(plan.roadGraph()));
    }

    @Test
    void buildingSlotsStayInsideParcels() {
        SettlementPlan plan = planner.plan(request(flatSurvey(40, 30), 100L, SuburbPlanningSettings.defaults()))
                .plan()
                .orElseThrow();

        for (BuildingSlot buildingSlot : plan.buildingSlots()) {
            assertTrue(parcelContainsBuildingSlot(plan, buildingSlot));
        }
    }

    @Test
    void supportsSingleParcelTarget() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(3, 0.25, 1);
        SuburbPlanningResult result = planner.plan(request(flatSurvey(40, 30), 100L, settings));

        assertTrue(result.successful());
        assertEquals(1, result.plan().orElseThrow().parcels().size());
    }

    @Test
    void rejectsWhenTargetParcelCountDoesNotFitSurvey() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(3, 0.25, 100);
        SuburbPlanningResult result = planner.plan(request(flatSurvey(40, 30), 100L, settings));

        assertFalse(result.successful());
        assertEquals(Optional.of(SuburbPlanningFailureReason.NOT_ENOUGH_PARCEL_SPACE), result.failureReason());
    }

    private static boolean isConnected(RoadGraph roadGraph) {
        Map<PlanElementId, Set<PlanElementId>> adjacency = adjacency(roadGraph);
        PlanElementId startId = roadGraph.nodes().getFirst().id();
        Set<PlanElementId> visited = visitConnectedNodes(adjacency, startId);

        return visited.size() == roadGraph.nodes().size();
    }

    private static Map<PlanElementId, Set<PlanElementId>> adjacency(RoadGraph roadGraph) {
        Map<PlanElementId, Set<PlanElementId>> adjacency = new HashMap<>();

        for (RoadSegment segment : roadGraph.segments()) {
            adjacency.computeIfAbsent(segment.startNodeId(), key -> new HashSet<>()).add(segment.endNodeId());
            adjacency.computeIfAbsent(segment.endNodeId(), key -> new HashSet<>()).add(segment.startNodeId());
        }

        return adjacency;
    }

    private static Set<PlanElementId> visitConnectedNodes(
            Map<PlanElementId, Set<PlanElementId>> adjacency,
            PlanElementId startId
    ) {
        Set<PlanElementId> visited = new HashSet<>();
        Queue<PlanElementId> queue = new ArrayDeque<>();
        queue.add(startId);

        while (!queue.isEmpty()) {
            PlanElementId currentId = queue.remove();

            if (!visited.add(currentId)) {
                continue;
            }

            queue.addAll(adjacency.getOrDefault(currentId, Set.of()));
        }

        return visited;
    }

    private static boolean hasDeadEndSegment(RoadGraph roadGraph) {
        for (RoadSegment segment : roadGraph.segments()) {
            if (segment.tags().contains(new PlanTag("dead_end"))) {
                return true;
            }
        }

        return false;
    }

    private static boolean parcelContainsBuildingSlot(SettlementPlan plan, BuildingSlot buildingSlot) {
        return plan.parcels().stream()
                .filter(parcel -> parcel.id().equals(buildingSlot.parcelId()))
                .anyMatch(parcel -> parcel.bounds().contains(buildingSlot.bounds()));
    }

    private static SuburbPlanningRequest request(
            TerrainSurvey survey,
            long seed,
            SuburbPlanningSettings settings
    ) {
        return new SuburbPlanningRequest(SETTLEMENT_ID, survey, seed, settings);
    }

    private static TerrainSurvey flatSurvey(int width, int depth) {
        return survey(width, depth, false, 0.0, TerrainCategory.BUILDABLE);
    }

    private static TerrainSurvey waterSurvey(int width, int depth) {
        return survey(width, depth, true, 0.0, TerrainCategory.BUILDABLE);
    }

    private static TerrainSurvey steepSurvey(int width, int depth) {
        return survey(width, depth, false, 0.5, TerrainCategory.BUILDABLE);
    }

    private static TerrainSurvey survey(
            int width,
            int depth,
            boolean water,
            double slope,
            TerrainCategory terrainCategory
    ) {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(width, depth));

        return TerrainSurvey.sample(
                bounds,
                point -> Optional.of(new TerrainCell(
                        point,
                        64,
                        water,
                        slope,
                        BiomeCategory.PLAINS,
                        terrainCategory
                ))
        );
    }
}
