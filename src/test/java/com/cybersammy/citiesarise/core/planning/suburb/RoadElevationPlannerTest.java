package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanPropertyKeys;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class RoadElevationPlannerTest {
    @Test
    void producesSameElevationsWhenSegmentOrderChanges() {
        RoadGraph graph = cyclicGraph();
        List<RoadSegment> reversedSegments = new ArrayList<>(graph.segments());
        Collections.reverse(reversedSegments);
        RoadGraph reorderedGraph = new RoadGraph(graph.nodes(), reversedSegments);

        RoadGraph first = RoadElevationPlanner.apply(request(), graph);
        RoadGraph second = RoadElevationPlanner.apply(request(), reorderedGraph);

        assertEquals(elevationsById(first), elevationsById(second));
    }

    @Test
    void keepsCyclicRoadComponentTransitionsWithinOneBlock() {
        RoadGraph elevated = RoadElevationPlanner.apply(request(), cyclicGraph());

        for (RoadSegment first : elevated.segments()) {
            for (RoadSegment second : elevated.segments()) {
                if (connect(first, second)) {
                    int difference = Math.abs(elevation(first) - elevation(second));
                    assertTrue(difference <= 1);
                }
            }
        }
    }

    private static RoadGraph cyclicGraph() {
        RoadNode northWest = node("north_west", 2, 2);
        RoadNode northEast = node("north_east", 12, 2);
        RoadNode southEast = node("south_east", 12, 7);
        RoadNode southWest = node("south_west", 2, 7);
        return new RoadGraph(
                List.of(northWest, northEast, southEast, southWest),
                List.of(
                        segment("north", northWest, northEast),
                        segment("east", northEast, southEast),
                        segment("south", southEast, southWest),
                        segment("west", southWest, northWest)
                )
        );
    }

    private static SuburbPlanningRequest request() {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(16, 10));
        TerrainSurvey survey = TerrainSurvey.sample(
                bounds,
                point -> Optional.of(new TerrainCell(
                        point,
                        64 + (point.x() / 4),
                        false,
                        0.0,
                        BiomeCategory.PLAINS,
                        TerrainCategory.BUILDABLE
                ))
        );
        return new SuburbPlanningRequest(
                new PlanElementId("settlement/test"),
                survey,
                100L,
                SuburbPlanningSettings.defaults()
        );
    }

    private static RoadNode node(String name, int x, int z) {
        return new RoadNode(id(name), new GridPoint(x, z), Set.of(), PlanProperties.empty());
    }

    private static RoadSegment segment(String name, RoadNode start, RoadNode end) {
        return new RoadSegment(id(name), start.id(), end.id(), 1, Set.of(), PlanProperties.empty());
    }

    private static PlanElementId id(String name) {
        return new PlanElementId("road/" + name);
    }

    private static Map<PlanElementId, Integer> elevationsById(RoadGraph graph) {
        return graph.segments().stream().collect(Collectors.toMap(
                RoadSegment::id,
                segment -> Integer.parseInt(
                        segment.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow()
                )
        ));
    }

    private static boolean connect(RoadSegment first, RoadSegment second) {
        if (first.id().equals(second.id())) {
            return false;
        }
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

    private static int elevation(RoadSegment segment) {
        return Integer.parseInt(segment.properties().find(PlanPropertyKeys.PLATFORM_Y).orElseThrow());
    }
}
