package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanTag;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopology;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopologyAnalyzer;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class TerrainDerivedRoadSkeletonPlannerTest {
    private static final PlanElementId SETTLEMENT_ID = new PlanElementId("settlement/test");
    private static final PlanTag MAIN_ROAD = new PlanTag("main_road");
    private final TerrainDerivedRoadSkeletonPlanner planner = new TerrainDerivedRoadSkeletonPlanner();

    @Test
    void followsTheLongHorizontalAxisOfAWideFootprint() {
        RoadGraph graph = plan(DistrictFootprint.rectangle(bounds(0, 0, 40, 30)), 11L);

        assertFalse(mainSegments(graph).isEmpty());
        assertTrue(mainSegments(graph).stream().allMatch(segment -> isHorizontal(graph, segment)));
    }

    @Test
    void followsTheLongVerticalAxisOfADeepFootprint() {
        RoadGraph graph = plan(DistrictFootprint.rectangle(bounds(0, 0, 30, 60)), 11L);

        assertFalse(mainSegments(graph).isEmpty());
        assertTrue(mainSegments(graph).stream().allMatch(segment -> isVertical(graph, segment)));
    }

    @Test
    void keepsEveryNominalNodeInsideTheSelectedTerrainComponent() {
        GridBounds envelope = bounds(0, 0, 21, 15);
        DistrictFootprint footprint = DistrictFootprint.fromTopology(
                envelope,
                topology(envelope, point -> point.x() != 10 && !point.equals(new GridPoint(4, 7)))
        ).orElseThrow();

        RoadGraph graph = plan(footprint, 29L);

        assertTrue(graph.nodes().stream().allMatch(node -> footprint.contains(node.point())));
    }

    @Test
    void shiftsTheMainAxisWhenItsTerrainShoulderWouldTouchAPreservedGap() {
        GridBounds envelope = bounds(0, 0, 40, 30);
        GridPoint preserved = new GridPoint(0, 13);
        DistrictFootprint footprint = DistrictFootprint.fromTopology(
                envelope,
                topology(envelope, point -> !point.equals(preserved))
        ).orElseThrow();

        RoadGraph graph = plan(footprint, 42L);
        Map<PlanElementId, RoadNode> nodes = nodesById(graph);

        assertTrue(mainSegments(graph).stream().allMatch(segment -> {
            GridPoint start = nodes.get(segment.startNodeId()).point();
            GridPoint end = nodes.get(segment.endNodeId()).point();
            return start.z() == end.z() && Math.abs(start.z() - preserved.z()) > 2;
        }));
    }

    @Test
    void producesTheSameSkeletonForTheSameSeed() {
        DistrictFootprint footprint = DistrictFootprint.rectangle(bounds(0, 0, 40, 30));

        assertEquals(plan(footprint, 73L), plan(footprint, 73L));
    }

    private RoadGraph plan(DistrictFootprint footprint, long seed) {
        return planner.plan(SETTLEMENT_ID, footprint, SuburbPlanningSettings.defaults(), seed);
    }

    private static java.util.List<RoadSegment> mainSegments(RoadGraph graph) {
        return graph.segments().stream()
                .filter(segment -> segment.tags().contains(MAIN_ROAD))
                .toList();
    }

    private static boolean isHorizontal(RoadGraph graph, RoadSegment segment) {
        Map<PlanElementId, RoadNode> nodes = nodesById(graph);
        return nodes.get(segment.startNodeId()).point().z() == nodes.get(segment.endNodeId()).point().z();
    }

    private static boolean isVertical(RoadGraph graph, RoadSegment segment) {
        Map<PlanElementId, RoadNode> nodes = nodesById(graph);
        return nodes.get(segment.startNodeId()).point().x() == nodes.get(segment.endNodeId()).point().x();
    }

    private static Map<PlanElementId, RoadNode> nodesById(RoadGraph graph) {
        return graph.nodes().stream().collect(Collectors.toMap(RoadNode::id, node -> node));
    }

    private static TerrainTopology topology(GridBounds bounds, Predicate<GridPoint> developable) {
        TerrainSurvey survey = TerrainSurvey.sample(
                bounds,
                point -> Optional.of(new TerrainCell(
                        point,
                        64,
                        false,
                        0.0,
                        BiomeCategory.PLAINS,
                        developable.test(point) ? TerrainCategory.BUILDABLE : TerrainCategory.BLOCKED
                ))
        );
        return new TerrainTopologyAnalyzer().analyze(
                survey,
                cell -> cell.terrainCategory() == TerrainCategory.BUILDABLE
        );
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(new GridPoint(x, z), new GridSize(width, depth));
    }
}
