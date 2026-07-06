package com.cybersammy.citiesarise.core.model;

import java.util.List;

public record RoadGraph(List<RoadNode> nodes, List<RoadSegment> segments) {
    public RoadGraph {
        nodes = PlanCollections.immutableList(nodes, "nodes");
        segments = PlanCollections.immutableList(segments, "segments");
    }

    public static RoadGraph empty() {
        return new RoadGraph(List.of(), List.of());
    }
}
