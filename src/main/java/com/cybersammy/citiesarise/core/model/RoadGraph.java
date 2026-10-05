package com.cybersammy.citiesarise.core.model;

import java.util.List;

public record RoadGraph(List<RoadNode> nodes, List<RoadSegment> segments, List<BridgePlan> bridges) {
    public RoadGraph(List<RoadNode> nodes, List<RoadSegment> segments) {
        this(nodes, segments, List.of());
    }
    public RoadGraph {
        nodes = PlanCollections.immutableList(nodes, "nodes");
        segments = PlanCollections.immutableList(segments, "segments");
        bridges = PlanCollections.immutableList(bridges, "bridges");
        var points = new java.util.HashMap<PlanElementId, com.cybersammy.citiesarise.core.geometry.GridPoint>();
        nodes.forEach(node -> points.put(node.id(), node.point()));
        var ids = new java.util.HashSet<PlanElementId>();
        nodes.forEach(node -> ids.add(node.id()));
        segments.forEach(segment -> ids.add(segment.id()));
        for (var bridge : bridges) {
            if (!ids.add(bridge.id()) || !bridge.start().equals(points.get(bridge.startNodeId()))
                    || !bridge.end().equals(points.get(bridge.endNodeId()))) {
                throw new IllegalArgumentException("Bridge must have unique identity and reference matching road nodes");
            }
        }
    }

    public static RoadGraph empty() {
        return new RoadGraph(List.of(), List.of());
    }
}
