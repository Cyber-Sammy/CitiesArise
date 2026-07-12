package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanPropertyKey;
import com.cybersammy.citiesarise.core.model.PlanTag;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RoadGraphSegmenterTest {
    private static final PlanPropertyKey SURFACE = new PlanPropertyKey("surface");
    private static final PlanTag MAIN_ROAD = new PlanTag("main_road");

    @Test
    void splitsLongRoadIntoDeterministicBoundedSegments() {
        RoadGraph source = graph(10);

        RoadGraph first = RoadGraphSegmenter.splitLongSegments(source, 3);
        RoadGraph second = RoadGraphSegmenter.splitLongSegments(source, 3);

        assertEquals(first, second);
        assertEquals(4, first.segments().size());
        assertTrue(first.segments().stream().allMatch(segment -> length(first, segment) <= 3));
        assertTrue(first.segments().stream().allMatch(segment -> segment.tags().contains(MAIN_ROAD)));
        assertTrue(first.segments().stream().allMatch(segment -> "stone".equals(
                segment.properties().find(SURFACE).orElseThrow()
        )));
    }

    @Test
    void keepsShortRoadUnchanged() {
        RoadGraph source = graph(3);

        RoadGraph segmented = RoadGraphSegmenter.splitLongSegments(source, 3);

        assertEquals(1, segmented.segments().size());
        assertSame(source.segments().getFirst(), segmented.segments().getFirst());
    }

    private static int length(RoadGraph graph, RoadSegment segment) {
        Map<PlanElementId, RoadNode> nodesById = new HashMap<>();
        for (RoadNode node : graph.nodes()) {
            nodesById.put(node.id(), node);
        }
        GridPoint start = nodesById.get(segment.startNodeId()).point();
        GridPoint end = nodesById.get(segment.endNodeId()).point();
        return Math.abs(end.x() - start.x()) + Math.abs(end.z() - start.z());
    }

    private static RoadGraph graph(int length) {
        RoadNode start = new RoadNode(id("start"), new GridPoint(0, 0), Set.of(), PlanProperties.empty());
        RoadNode end = new RoadNode(id("end"), new GridPoint(length, 0), Set.of(), PlanProperties.empty());
        RoadSegment segment = new RoadSegment(
                id("road"),
                start.id(),
                end.id(),
                3,
                Set.of(MAIN_ROAD),
                PlanProperties.of(SURFACE, "stone")
        );
        return new RoadGraph(List.of(start, end), List.of(segment));
    }

    private static PlanElementId id(String value) {
        return new PlanElementId("test/" + value);
    }
}
