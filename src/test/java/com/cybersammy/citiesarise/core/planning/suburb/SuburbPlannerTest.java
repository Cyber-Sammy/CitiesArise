package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.config.DebugSuburbPlanningConfig;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationStatus;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanPropertyKey;
import com.cybersammy.citiesarise.core.model.PlanTag;
import com.cybersammy.citiesarise.core.model.PlanPropertyKeys;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitabilityContribution;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitabilityContext;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitabilityRule;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainSuitabilityScorer;
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
    private static final PlanPropertyKey EXISTING_PROPERTY = new PlanPropertyKey("existing_property");

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
        assertTerrainDiagnostic(result, TerrainRejectionReason.WATER);
    }

    @Test
    void ignoresBadTerrainOutsidePlannedFootprint() {
        TerrainSurvey survey = surveyWithSingleWaterCell(40, 30, new GridPoint(0, 0));
        SuburbPlanningResult result = planner.plan(request(survey, 100L, SuburbPlanningSettings.defaults()));

        assertTrue(result.successful());
    }

    @Test
    void acceptsSteepTerrainWhenPlatformsStayWithinEarthworkLimits() {
        SuburbPlanningResult result = planner.plan(request(steepSurvey(40, 30), 100L, SuburbPlanningSettings.defaults()));

        assertTrue(result.successful());
        assertEquals(TerrainPreparationStatus.ACCEPTED, result.terrainPreparationPlan().orElseThrow().status());
    }

    @Test
    void acceptsGentleSlopeWhenSettingsAllowIt() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(3, 0.75, 6);
        SuburbPlanningResult result = planner.plan(request(steepSurvey(40, 30), 100L, settings));

        assertTrue(result.successful());
    }

    @Test
    void rejectsMountainTerrainThroughBoundedEarthwork() {
        TerrainSurvey survey = elevationSurvey(40, 30, 1);

        SuburbPlanningResult result = planner.plan(request(survey, 100L, SuburbPlanningSettings.defaults()));

        assertFalse(result.successful());
        assertEquals(Optional.of(SuburbPlanningFailureReason.UNSUITABLE_TERRAIN), result.failureReason());
        assertTrue(result.terrainDiagnostic().orElseThrow().primaryRejectionReason().isPresent());
        TerrainRejectionReason reason = result.terrainDiagnostic().orElseThrow().primaryRejectionReason().orElseThrow();
        assertTrue(reason == TerrainRejectionReason.EXCESSIVE_CUT || reason == TerrainRejectionReason.EXCESSIVE_FILL);
    }

    @Test
    void acceptsRollingTerrainWithinEarthworkLimits() {
        TerrainSurvey survey = elevationSurvey(40, 30, 5);

        SuburbPlanningResult result = planner.plan(request(survey, 100L, SuburbPlanningSettings.defaults()));

        assertTrue(result.successful());
        assertEquals(
                TerrainPreparationStatus.ACCEPTED_WITH_EARTHWORKS,
                result.terrainPreparationPlan().orElseThrow().status()
        );
        assertTrue(result.terrainPreparationPlan().orElseThrow().totalVolume() > 0L);
    }

    @Test
    void acceptsDeterministicMarginalDryTerrainWithinAbsoluteLimits() {
        TerrainSurvey survey = surveyWithHeightAt(40, 30, new GridPoint(10, 15), 59);
        SuburbPlanningSettings settings = new SuburbPlanningSettings(
                3,
                0.75,
                6,
                6,
                7,
                1,
                12,
                3,
                3,
                6,
                8,
                20_000L
        );
        SuburbPlanningRequest request = request(survey, 100L, settings);

        SuburbPlanningResult first = planner.plan(request);
        SuburbPlanningResult second = planner.plan(request);

        assertTrue(first.successful(), first.toString());
        assertEquals(first, second);
        assertEquals(
                TerrainPreparationStatus.ACCEPTED_WITH_EARTHWORKS,
                first.terrainPreparationPlan().orElseThrow().status()
        );
    }

    @Test
    void acceptsGradualTerrainBeyondLegacyGlobalElevationRange() {
        TerrainSurvey survey = elevationSurvey(40, 30, 3);

        SuburbPlanningResult result = planner.plan(request(survey, 100L, SuburbPlanningSettings.defaults()));

        assertTrue(result.successful(), result.toString());
        assertEquals(
                TerrainPreparationStatus.ACCEPTED_WITH_EARTHWORKS,
                result.terrainPreparationPlan().orElseThrow().status()
        );
    }

    @Test
    void rejectsTerrainWhenTotalEarthworkBudgetIsExceeded() {
        TerrainSurvey survey = elevationSurvey(40, 30, 5);
        SuburbPlanningSettings settings = new SuburbPlanningSettings(
                3,
                0.25,
                6,
                6,
                7,
                1,
                12,
                3,
                3,
                0L
        );

        SuburbPlanningResult result = planner.plan(request(survey, 100L, settings));

        assertFalse(result.successful());
        assertTerrainDiagnostic(result, TerrainRejectionReason.EXCESSIVE_EARTHWORK);
    }

    @Test
    void allowsProfileToIncreaseEarthworkDepth() {
        TerrainSurvey survey = elevationSurvey(40, 30, 1);
        SuburbPlanningSettings settings = new SuburbPlanningSettings(
                3,
                0.25,
                6,
                6,
                7,
                1,
                SuburbPlanningSettings.DEFAULT_MAX_ELEVATION_RANGE,
                40,
                40
        );

        SuburbPlanningResult result = planner.plan(request(survey, 100L, settings));

        assertTrue(result.successful());
    }

    @Test
    void assignsPlatformElevationToRoadSegmentsAndBuildingSlots() {
        SettlementPlan plan = planner.plan(request(elevationSurvey(40, 30, 5), 100L, SuburbPlanningSettings.defaults()))
                .plan()
                .orElseThrow();

        assertTrue(plan.roadGraph().segments().stream().allMatch(segment -> hasPlatformElevation(segment.properties())));
        assertTrue(plan.buildingSlots().stream().allMatch(slot -> hasPlatformElevation(slot.properties())));
    }

    @Test
    void placesBuildingPlatformAtHighestTerrainPointInFootprint() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(3, 0.75, 6, 6, 7, 1, 100, 3, 3);
        BuildingSlot flatSlot = planner.plan(request(flatSurvey(40, 30), 100L, settings))
                .plan()
                .orElseThrow()
                .buildingSlots()
                .getFirst();
        GridPoint highPoint = flatSlot.bounds().origin();
        TerrainSurvey slopedSurvey = surveyWithHeightAt(40, 30, highPoint, 66);

        SuburbPlanningResult result = planner.plan(request(slopedSurvey, 100L, settings));

        assertTrue(result.successful(), result.toString());
        BuildingSlot elevatedSlot = result.plan().orElseThrow().buildingSlots().stream()
                .filter(slot -> slot.id().equals(flatSlot.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(65, platformY(elevatedSlot));
        var buildingColumns = result.terrainPreparationPlan().orElseThrow().columns().stream()
                .filter(column -> column.sourceElementId().equals(elevatedSlot.id()))
                .toList();
        assertTrue(buildingColumns.stream().allMatch(column -> column.targetElevation() == 65));
        assertTrue(buildingColumns.stream().allMatch(column -> column.cutDepth() == 0));
        assertTrue(buildingColumns.stream().anyMatch(column -> column.fillDepth() == 2));
    }

    @Test
    void rejectsBuildingFoundationDeeperThanFillLimit() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(3, 0.75, 6, 6, 7, 1, 100, 3, 1);
        BuildingSlot flatSlot = planner.plan(request(flatSurvey(40, 30), 100L, settings))
                .plan()
                .orElseThrow()
                .buildingSlots()
                .getFirst();
        TerrainSurvey slopedSurvey = surveyWithHeightAt(40, 30, flatSlot.bounds().origin(), 66);

        SuburbPlanningResult result = planner.plan(request(slopedSurvey, 100L, settings));

        assertFalse(result.successful());
        assertTerrainDiagnostic(result, TerrainRejectionReason.EXCESSIVE_FILL);
    }

    @Test
    void rejectsPlatformThatWouldBridgeADeepRavine() {
        TerrainSurvey survey = surveyWithHeightAt(40, 30, new GridPoint(10, 15), 40);
        SuburbPlanningSettings settings = new SuburbPlanningSettings(3, 0.75, 6, 6, 7, 1, 100, 3, 3);

        SuburbPlanningResult result = planner.plan(request(survey, 100L, settings));

        assertFalse(result.successful());
        assertTerrainDiagnostic(result, TerrainRejectionReason.EXCESSIVE_FILL);
    }

    @Test
    void rejectsPlatformThatWouldRequireAnExcessiveCut() {
        TerrainSurvey survey = surveyWithHeightAt(40, 30, new GridPoint(10, 15), 90);
        SuburbPlanningSettings settings = new SuburbPlanningSettings(3, 0.75, 6, 6, 7, 1, 100, 3, 3);

        SuburbPlanningResult result = planner.plan(request(survey, 100L, settings));

        assertFalse(result.successful());
        assertTerrainDiagnostic(result, TerrainRejectionReason.EXCESSIVE_CUT);
    }

    @Test
    void keepsConnectedRoadSegmentsWithinOneBlock() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(3, 0.75, 6, 6, 7, 1, 40, 40, 40);
        SettlementPlan plan = planner.plan(request(elevationSurvey(40, 30, 2), 100L, settings))
                .plan()
                .orElseThrow();

        for (RoadSegment first : plan.roadGraph().segments()) {
            for (RoadSegment second : plan.roadGraph().segments()) {
                if (first.id().equals(second.id())) {
                    continue;
                }
                if (segmentsConnect(first, second)) {
                    int difference = Math.abs(platformY(first) - platformY(second));
                    assertTrue(difference <= 1);
                }
            }
        }
    }

    @Test
    void acceptsRoughTerrainWhenScoreRemainsUsable() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(3, 0.75, 6);
        SuburbPlanningResult result = planner.plan(request(roughSurvey(40, 30), 100L, settings));

        assertTrue(result.successful());
    }

    @Test
    void reportsBlockedTerrainDiagnostic() {
        SuburbPlanningResult result = planner.plan(request(blockedSurvey(40, 30), 100L, SuburbPlanningSettings.defaults()));

        assertFalse(result.successful());
        assertEquals(Optional.of(SuburbPlanningFailureReason.UNSUITABLE_TERRAIN), result.failureReason());
        assertTerrainDiagnostic(result, TerrainRejectionReason.BLOCKED_TERRAIN);
    }

    @Test
    void reportsLowScoreTerrainDiagnostic() {
        SuburbPlanner lowScorePlanner = new SuburbPlanner(
                new TerrainSuitabilityScorer(List.of(lowScoreRule())),
                validator
        );
        SuburbPlanningResult result = lowScorePlanner.plan(request(flatSurvey(40, 30), 100L, SuburbPlanningSettings.defaults()));

        assertFalse(result.successful());
        assertEquals(Optional.of(SuburbPlanningFailureReason.UNSUITABLE_TERRAIN), result.failureReason());
        assertEquals(Optional.empty(), result.terrainDiagnostic().orElseThrow().primaryRejectionReason());
    }

    @Test
    void rejectsTooSmallSurvey() {
        SuburbPlanningResult result = planner.plan(request(flatSurvey(8, 12), 100L, SuburbPlanningSettings.defaults()));

        assertFalse(result.successful());
        assertEquals(Optional.of(SuburbPlanningFailureReason.SURVEY_TOO_SMALL), result.failureReason());
    }

    @Test
    void rejectsRoadWidthWiderThanSurvey() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(41, 0.25, 6);
        SuburbPlanningResult result = planner.plan(request(flatSurvey(40, 30), 100L, settings));

        assertFalse(result.successful());
        assertEquals(Optional.of(SuburbPlanningFailureReason.SURVEY_TOO_SMALL), result.failureReason());
    }

    @Test
    void rejectsRoadWidthDeeperThanSurvey() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(31, 0.25, 6);
        SuburbPlanningResult result = planner.plan(request(flatSurvey(40, 30), 100L, settings));

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
    void customScaleControlsParcelAndBuildingSlotSize() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(5, 0.75, 4, 12, 14, 3);
        SettlementPlan plan = planner.plan(request(flatSurvey(72, 48), 100L, settings))
                .plan()
                .orElseThrow();

        Parcel parcel = plan.parcels().getFirst();
        BuildingSlot buildingSlot = plan.buildingSlots().getFirst();

        assertEquals(new GridSize(12, 14), parcel.bounds().size());
        assertEquals(new GridSize(6, 8), buildingSlot.bounds().size());
    }

    @Test
    void debugDefaultsCreateHabitableBuildingSlots() {
        DebugSuburbPlanningConfig config = DebugSuburbPlanningConfig.defaults();
        SuburbPlanningResult result = planner.plan(request(
                flatSurvey(config.surveyWidth(), config.surveyDepth()),
                100L,
                config.toSuburbPlanningSettings()
        ));

        assertTrue(result.successful());

        SettlementPlan plan = result.plan().orElseThrow();
        BuildingSlot buildingSlot = plan.buildingSlots().getFirst();

        assertEquals(8, plan.parcels().size());
        assertEquals(new GridSize(10, 12), buildingSlot.bounds().size());
    }

    @Test
    void settlementProfileControlsPlannerScale() {
        SettlementProfile profile = new SettlementProfile(
                new SettlementProfileId("test:large_suburb"),
                new GridSize(96, 64),
                new SuburbPlanningSettings(5, 0.75, 5, 18, 20, 4)
        );
        SuburbPlanningResult result = planner.plan(request(
                flatSurvey(profile.surveySize().width(), profile.surveySize().depth()),
                100L,
                profile.suburbPlanningSettings()
        ));

        assertTrue(result.successful());

        SettlementPlan plan = result.plan().orElseThrow();
        Parcel parcel = plan.parcels().getFirst();
        BuildingSlot buildingSlot = plan.buildingSlots().getFirst();

        assertEquals(5, plan.parcels().size());
        assertEquals(new GridSize(18, 20), parcel.bounds().size());
        assertEquals(new GridSize(10, 12), buildingSlot.bounds().size());
    }

    @Test
    void rejectsSurveyTooSmallForConfiguredScale() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(5, 0.75, 4, 30, 28, 4);
        SuburbPlanningResult result = planner.plan(request(flatSurvey(32, 32), 100L, settings));

        assertFalse(result.successful());
        assertEquals(Optional.of(SuburbPlanningFailureReason.SURVEY_TOO_SMALL), result.failureReason());
    }

    @Test
    void rejectsSurveyTooShallowForNorthAndSouthParcels() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(5, 0.75, 2, 12, 14, 3);
        SuburbPlanningResult result = planner.plan(request(flatSurvey(72, 30), 100L, settings));

        assertFalse(result.successful());
        assertEquals(Optional.of(SuburbPlanningFailureReason.SURVEY_TOO_SMALL), result.failureReason());
    }

    @Test
    void parcelsDoNotOverlapSideRoadCorridors() {
        SuburbPlanningSettings settings = SuburbPlanningSettings.defaults();
        SettlementPlan plan = planner.plan(request(flatSurvey(40, 30), 100L, settings))
                .plan()
                .orElseThrow();
        List<GridBounds> sideRoadCorridors = sideRoadCorridors(plan.roadGraph(), settings.roadWidth());

        for (Parcel parcel : plan.parcels()) {
            assertFalse(intersectsAny(parcel.bounds(), sideRoadCorridors));
        }
    }

    @Test
    void supportsSingleParcelTarget() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(3, 0.25, 1);
        SuburbPlanningResult result = planner.plan(request(flatSurvey(40, 30), 100L, settings));

        assertTrue(result.successful());
        assertTrue(result.terrainDiagnostic().isEmpty());
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

    private static List<GridBounds> sideRoadCorridors(RoadGraph roadGraph, int roadWidth) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(roadGraph);

        return roadGraph.segments().stream()
                .filter(SuburbPlannerTest::isSideRoadSegment)
                .map(segment -> sideRoadCorridor(segment, nodesById, roadWidth))
                .toList();
    }

    private static Map<PlanElementId, RoadNode> nodesById(RoadGraph roadGraph) {
        Map<PlanElementId, RoadNode> nodesById = new HashMap<>();

        for (RoadNode node : roadGraph.nodes()) {
            nodesById.put(node.id(), node);
        }

        return nodesById;
    }

    private static boolean isSideRoadSegment(RoadSegment segment) {
        return segment.tags().contains(new PlanTag("side_road"));
    }

    private static GridBounds sideRoadCorridor(
            RoadSegment segment,
            Map<PlanElementId, RoadNode> nodesById,
            int roadWidth
    ) {
        RoadNode startNode = nodesById.get(segment.startNodeId());
        RoadNode endNode = nodesById.get(segment.endNodeId());
        int roadX = startNode.point().x() - (roadWidth / 2);
        int minZ = Math.min(startNode.point().z(), endNode.point().z());
        int maxZ = Math.max(startNode.point().z(), endNode.point().z()) + 1;

        return new GridBounds(new GridPoint(roadX, minZ), new GridSize(roadWidth, maxZ - minZ));
    }

    private static boolean intersectsAny(GridBounds bounds, List<GridBounds> otherBounds) {
        for (GridBounds otherBound : otherBounds) {
            if (bounds.intersects(otherBound)) {
                return true;
            }
        }

        return false;
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

    private static TerrainSurvey blockedSurvey(int width, int depth) {
        return survey(width, depth, false, 0.0, TerrainCategory.BLOCKED);
    }

    private static TerrainSurvey roughSurvey(int width, int depth) {
        return survey(width, depth, false, 0.5, TerrainCategory.ROUGH);
    }

    private static TerrainSurvey elevationSurvey(int width, int depth, int horizontalScale) {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(width, depth));

        return TerrainSurvey.sample(
                bounds,
                point -> Optional.of(new TerrainCell(
                        point,
                        64 + (point.x() / horizontalScale),
                        false,
                        0.0,
                        BiomeCategory.PLAINS,
                        TerrainCategory.BUILDABLE
                ))
        );
    }

    private static TerrainSurvey surveyWithHeightAt(int width, int depth, GridPoint target, int targetHeight) {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(width, depth));
        return TerrainSurvey.sample(
                bounds,
                point -> Optional.of(new TerrainCell(
                        point,
                        point.equals(target) ? targetHeight : 64,
                        false,
                        0.0,
                        BiomeCategory.PLAINS,
                        TerrainCategory.BUILDABLE
                ))
        );
    }

    private static boolean segmentsConnect(RoadSegment first, RoadSegment second) {
        if (first.startNodeId().equals(second.startNodeId())) {
            return true;
        }
        if (first.startNodeId().equals(second.endNodeId())) {
            return true;
        }
        if (first.endNodeId().equals(second.startNodeId())) {
            return true;
        }
        return first.endNodeId().equals(second.endNodeId());
    }

    private static int platformY(RoadSegment segment) {
        return Integer.parseInt(segment.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow());
    }

    private static int platformY(BuildingSlot slot) {
        return Integer.parseInt(slot.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow());
    }

    private static TerrainSuitabilityRule lowScoreRule() {
        return new TerrainSuitabilityRule() {
            @Override
            public String name() {
                return "low_score";
            }

            @Override
            public TerrainSuitabilityContribution evaluate(TerrainCell cell, TerrainSuitabilityContext context) {
                return TerrainSuitabilityContribution.multiplier(0.2);
            }
        };
    }

    @Test
    void platformElevationPreservesExistingProperties() {
        PlanProperties properties = PlanProperties.of(EXISTING_PROPERTY, "kept");

        PlanProperties elevated = TerrainPlatform.withElevation(properties, 72);

        assertEquals("kept", elevated.find(EXISTING_PROPERTY).orElseThrow());
        assertEquals("72", elevated.find(PlanPropertyKeys.PLATFORM_Y).orElseThrow());
    }

    private static boolean hasPlatformElevation(PlanProperties properties) {
        return properties.find(PlanPropertyKeys.PLATFORM_Y).isPresent();
    }

    private static void assertTerrainDiagnostic(
            SuburbPlanningResult result,
            TerrainRejectionReason expectedReason
    ) {
        SuburbTerrainDiagnostic diagnostic = result.terrainDiagnostic().orElseThrow();

        assertEquals(Optional.of(expectedReason), diagnostic.primaryRejectionReason());
    }

    private static TerrainSurvey surveyWithSingleWaterCell(int width, int depth, GridPoint waterPoint) {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(width, depth));

        return TerrainSurvey.sample(
                bounds,
                point -> Optional.of(new TerrainCell(
                        point,
                        64,
                        point.equals(waterPoint),
                        0.0,
                        BiomeCategory.PLAINS,
                        TerrainCategory.BUILDABLE
                ))
        );
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
