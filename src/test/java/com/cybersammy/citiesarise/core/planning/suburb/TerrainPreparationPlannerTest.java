package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.earthwork.ElevationZone;
import com.cybersammy.citiesarise.core.earthwork.ElevationZoneType;
import com.cybersammy.citiesarise.core.earthwork.RegionalElevationPlan;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumnType;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanPropertyKeys;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.scoring.TerrainRejectionReason;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TerrainPreparationPlannerTest {
    @Test
    void countsIntersectingRoadColumnsOnce() {
        SuburbPlanningRequest request = new SuburbPlanningRequest(
                id("settlement"),
                flatSurvey(),
                42L,
                new SuburbPlanningSettings(1, 1.0, 1, 3, 3, 0, 20, 3, 3, 13L)
        );

        TerrainPreparationAssessment assessment = TerrainPreparationPlanner.plan(request, crossingRoadElevationPlan());

        assertEquals(13L, assessment.plan().orElseThrow().fillVolume());
        assertEquals(13, assessment.plan().orElseThrow().columns().size());
    }

    @Test
    void reportsMostExpensiveColumnWhenTotalBudgetIsExceeded() {
        GridPoint lowestPoint = new GridPoint(2, 5);
        SuburbPlanningRequest request = new SuburbPlanningRequest(
                id("settlement"),
                surveyWithLowPoint(lowestPoint),
                42L,
                new SuburbPlanningSettings(1, 1.0, 1, 3, 3, 0, 20, 3, 3, 0L)
        );

        TerrainPreparationAssessment assessment = TerrainPreparationPlanner.plan(request, crossingRoadElevationPlan());

        assertEquals(lowestPoint, assessment.diagnostic().orElseThrow().cell().point());
    }

    @Test
    void acceptsDepthAbovePreferredLimitWithinAbsoluteLimit() {
        GridPoint lowPoint = new GridPoint(2, 5);
        SuburbPlanningRequest request = new SuburbPlanningRequest(
                id("settlement"),
                surveyWithHeightAt(lowPoint, 62),
                42L,
                settings(1, 3)
        );

        TerrainPreparationAssessment assessment = TerrainPreparationPlanner.plan(request, crossingRoadElevationPlan());

        assertTrue(assessment.plan().isPresent());
        assertEquals(3, assessment.plan().orElseThrow().columns().stream()
                .filter(column -> column.point().equals(lowPoint))
                .findFirst()
                .orElseThrow()
                .fillDepth());
    }

    @Test
    void reportsElementAndLimitDeltaAboveAbsoluteLimit() {
        GridPoint lowPoint = new GridPoint(2, 5);
        SuburbPlanningRequest request = new SuburbPlanningRequest(
                id("settlement"),
                surveyWithHeightAt(lowPoint, 61),
                42L,
                settings(1, 3)
        );

        TerrainPreparationAssessment assessment = TerrainPreparationPlanner.plan(request, crossingRoadElevationPlan());
        TerrainPreparationLimitDiagnostic diagnostic = assessment.diagnostic()
                .orElseThrow()
                .optionalPreparationLimit()
                .orElseThrow();

        assertEquals(id("horizontal"), diagnostic.sourceElementId());
        assertEquals(4L, diagnostic.actualValue());
        assertEquals(1L, diagnostic.preferredLimit());
        assertEquals(3L, diagnostic.maximumLimit());
        assertEquals(1L, diagnostic.excessOverMaximum());
    }

    @Test
    void includesBuildingShouldersInEarthworkBudget() {
        SuburbPlanningRequest request = new SuburbPlanningRequest(
                id("settlement"),
                buildingShoulderSurvey(null),
                42L,
                new SuburbPlanningSettings(1, 1.0, 1, 3, 3, 0, 20, 3, 3, 43L)
        );

        TerrainPreparationAssessment assessment = TerrainPreparationPlanner.plan(
                request,
                buildingElevationPlan()
        );

        assertFalse(assessment.plan().isPresent());
        assertEquals(44L, assessment.diagnostic()
                .orElseThrow()
                .optionalPreparationLimit()
                .orElseThrow()
                .actualValue());
    }

    @Test
    void rejectsWaterInsideBuildingShoulder() {
        GridPoint waterPoint = new GridPoint(3, 4);
        SuburbPlanningRequest request = new SuburbPlanningRequest(
                id("settlement"),
                buildingShoulderSurvey(waterPoint),
                42L,
                settings(3, 3)
        );

        TerrainPreparationAssessment assessment = TerrainPreparationPlanner.plan(
                request,
                buildingElevationPlan()
        );

        assertEquals(waterPoint, assessment.diagnostic().orElseThrow().cell().point());
        assertEquals(
                TerrainRejectionReason.WATER,
                assessment.diagnostic().orElseThrow().primaryRejectionReason().orElseThrow()
        );
    }

    @Test
    void emitsTypedBuildingShoulderColumns() {
        SuburbPlanningRequest request = new SuburbPlanningRequest(
                id("settlement"),
                buildingShoulderSurvey(null),
                42L,
                settings(3, 3)
        );

        TerrainPreparationAssessment assessment = TerrainPreparationPlanner.plan(
                request,
                buildingElevationPlan()
        );

        assertEquals(32L, assessment.plan().orElseThrow().columns().stream()
                .filter(column -> column.type() == TerrainPreparationColumnType.BUILDING_SHOULDER)
                .count());
        assertEquals(44L, assessment.plan().orElseThrow().fillVolume());
    }

    private static SettlementPlan crossingRoadPlan() {
        RoadNode west = node("west", 2, 5);
        RoadNode east = node("east", 8, 5);
        RoadNode north = node("north", 5, 2);
        RoadNode south = node("south", 5, 8);
        return new SettlementPlan(
                id("settlement"),
                new RoadGraph(
                        List.of(west, east, north, south),
                        List.of(segment("horizontal", west, east), segment("vertical", north, south))
                ),
                List.of(),
                List.of(),
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static RegionalElevationPlan crossingRoadElevationPlan() {
        SettlementPlan plan = crossingRoadPlan();
        return new RegionalElevationPlan(
                List.of(
                        new ElevationZone(
                                plan.roadGraph().segments().get(0).id(),
                                ElevationZoneType.ROAD_SEGMENT,
                                new GridBounds(new GridPoint(2, 5), new GridSize(7, 1)),
                                64
                        ),
                        new ElevationZone(
                                plan.roadGraph().segments().get(1).id(),
                                ElevationZoneType.ROAD_SEGMENT,
                                new GridBounds(new GridPoint(5, 2), new GridSize(1, 7)),
                                64
                        )
                ),
                List.of()
        );
    }

    private static RoadNode node(String name, int x, int z) {
        return new RoadNode(id(name), new GridPoint(x, z), Set.of(), PlanProperties.empty());
    }

    private static RoadSegment segment(String name, RoadNode start, RoadNode end) {
        return new RoadSegment(
                id(name),
                start.id(),
                end.id(),
                1,
                Set.of(),
                PlanProperties.of(PlanPropertyKeys.PLATFORM_Y, "64")
        );
    }

    private static TerrainSurvey flatSurvey() {
        return surveyWithLowPoint(null);
    }

    private static TerrainSurvey surveyWithLowPoint(GridPoint lowPoint) {
        return surveyWithHeightAt(lowPoint, 63);
    }

    private static RegionalElevationPlan buildingElevationPlan() {
        return new RegionalElevationPlan(
                List.of(new ElevationZone(
                        id("building"),
                        ElevationZoneType.BUILDING_PAD,
                        new GridBounds(new GridPoint(4, 4), new GridSize(2, 2)),
                        64
                )),
                List.of()
        );
    }

    private static TerrainSurvey surveyWithHeightAt(GridPoint pointAtHeight, int height) {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(12, 12));
        return TerrainSurvey.sample(
                bounds,
                point -> Optional.of(new TerrainCell(
                        point,
                        point.equals(pointAtHeight) ? height : 64,
                        false,
                        0.0,
                        BiomeCategory.PLAINS,
                        TerrainCategory.BUILDABLE
                ))
        );
    }

    private static TerrainSurvey buildingShoulderSurvey(GridPoint waterPoint) {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(12, 12));
        GridBounds buildingBounds = new GridBounds(new GridPoint(4, 4), new GridSize(2, 2));
        return TerrainSurvey.sample(
                bounds,
                point -> Optional.of(new TerrainCell(
                        point,
                        buildingBounds.contains(point) ? 65 : 62,
                        point.equals(waterPoint),
                        0.0,
                        BiomeCategory.PLAINS,
                        TerrainCategory.BUILDABLE
                ))
        );
    }

    private static SuburbPlanningSettings settings(int preferredMaxFillDepth, int maxFillDepth) {
        return new SuburbPlanningSettings(
                1,
                1.0,
                1,
                3,
                3,
                0,
                20,
                1,
                preferredMaxFillDepth,
                3,
                maxFillDepth,
                1_000L
        );
    }

    private static PlanElementId id(String value) {
        return new PlanElementId("test/" + value);
    }
}
