package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RoadElevationPlanner {
    private RoadElevationPlanner() {
    }

    static RoadGraph apply(SuburbPlanningRequest request, RoadGraph roadGraph) {
        return apply(request,roadGraph,List.of(),false);
    }

    static RoadGraph apply(SuburbPlanningRequest request,RoadGraph roadGraph,
            List<GridBounds> occupiedPads,boolean fitEarthworks) {
        return apply(request,roadGraph,occupiedPads,fitEarthworks,null);
    }
    static RoadGraph apply(SuburbPlanningRequest request,RoadGraph roadGraph,
            List<GridBounds> occupiedPads,boolean fitEarthworks,Integer target) {
        Map<PlanElementId, RoadNode> nodesById = nodesById(roadGraph);
        Map<PlanElementId, Integer> desiredElevations = desiredElevations(request, roadGraph, nodesById);
        boolean locked=request.settings().districts().elevation().enabled() && roadGraph.segments().stream()
                .anyMatch(s->s.properties().find(GradeAwareRoadPlanner.GRADED).isPresent());
        var intervals = fitEarthworks || target!=null || locked || request.settings().districts().grid().enabled() ? feasibleElevations(request,roadGraph,nodesById,occupiedPads) : Map.<PlanElementId,Interval>of();
        if(locked) {
            var fixed=new HashMap<>(intervals);
            var desired=new HashMap<>(desiredElevations);
            for(var s:roadGraph.segments())if(s.properties().find(GradeAwareRoadPlanner.GRADED).isPresent()) {
                int level=TerrainPlatform.requiredElevation(s.properties());desired.put(s.id(),level);
                var hard=fixed.get(s.id());fixed.put(s.id(),new Interval(Math.max(hard.minimum(),level),Math.min(hard.maximum(),level)));
            }
            desiredElevations=Map.copyOf(desired);intervals=Map.copyOf(fixed);
        }
        if(target!=null || request.settings().districts().grid().enabled()) {
            var limits=new HashMap<PlanElementId,NeighborhoodLevels.Interval>();
            int depth=request.settings().districts().elevation().preferredDepth();
            for(var segment:roadGraph.segments()) {
                var bounds=com.cybersammy.citiesarise.core.road.RoadCorridor.bounds(nodesById.get(segment.startNodeId()).point(),nodesById.get(segment.endNodeId()).point(), segment.width(), segment.properties());
                int low=Integer.MIN_VALUE,high=Integer.MAX_VALUE;
                for(int z=bounds.minZ();z<bounds.maxZExclusive();z++)for(int x=bounds.minX();x<bounds.maxXExclusive();x++) {
                    var cell=request.survey().findCell(new com.cybersammy.citiesarise.core.geometry.GridPoint(x,z));
                    if(cell.isEmpty())continue;
                    int ground=cell.orElseThrow().height()-1;
                    low=Math.max(low,ground-depth);high=Math.min(high,ground+depth);
                }
                var hard=intervals.get(segment.id());low=Math.max(low,hard.minimum());high=Math.min(high,hard.maximum());
                limits.put(segment.id(),new NeighborhoodLevels.Interval(low<=high?low:hard.minimum(),low<=high?high:hard.maximum(),desiredElevations.get(segment.id())));
            }
            var adjacency=new ArrayList<NeighborhoodLevels.Edge>();
            for(var neighbors:segmentsByNode(roadGraph).values())for(int i=0;i<neighbors.size();i++)for(int j=i+1;j<neighbors.size();j++)
                adjacency.add(new NeighborhoodLevels.Edge(neighbors.get(i),neighbors.get(j)));
            desiredElevations=target==null?NeighborhoodLevels.chooseLocally(limits,adjacency):NeighborhoodLevels.choose(limits,adjacency,target);
        }
        Map<PlanElementId, Integer> componentElevations = componentElevations(roadGraph, desiredElevations,intervals);
        if(request.settings().districts().grid().enabled() && hasDisconnectedGrade(roadGraph,componentElevations)) {
            // Inconsistent hard intervals must not mix clamped and fallback profiles into a vertical jump.
            // Keep the continuous preferred grade; preparation reports the actual cut/fill violation for block pruning.
            componentElevations=componentElevations(roadGraph,desiredElevations,Map.of());
        }
        List<RoadSegment> elevatedSegments = new ArrayList<>();

        for (RoadSegment segment : roadGraph.segments()) {
            elevatedSegments.add(withElevation(segment, componentElevations.get(segment.id())));
        }

        return new RoadGraph(roadGraph.nodes(), elevatedSegments);
    }

    private static boolean hasDisconnectedGrade(RoadGraph graph,Map<PlanElementId,Integer> elevations) {
        for(var neighbors:segmentsByNode(graph).values())for(int i=0;i<neighbors.size();i++)for(int j=i+1;j<neighbors.size();j++)
            if(Math.abs((long)elevations.get(neighbors.get(i))-elevations.get(neighbors.get(j)))>1)return true;
        return false;
    }

    private static Map<PlanElementId, Integer> desiredElevations(
            SuburbPlanningRequest request,
            RoadGraph graph,
            Map<PlanElementId, RoadNode> nodesById
    ) {
        Map<PlanElementId, Integer> elevations = new HashMap<>();
        for (RoadSegment segment : graph.segments()) {
            RoadNode start = requiredNode(nodesById, segment.startNodeId());
            RoadNode end = requiredNode(nodesById, segment.endNodeId());
            GridBounds bounds = com.cybersammy.citiesarise.core.road.RoadCorridor.bounds(start.point(), end.point(), segment.width(), segment.properties());
            elevations.put(segment.id(), TerrainPlatform.medianElevation(request, bounds));
        }
        return Map.copyOf(elevations);
    }

    private static Map<PlanElementId, Integer> componentElevations(
            RoadGraph graph,
            Map<PlanElementId, Integer> desiredElevations,Map<PlanElementId,Interval> intervals
    ) {
        var neighbors = new HashMap<PlanElementId, Set<PlanElementId>>();
        graph.segments().forEach(segment -> neighbors.put(segment.id(), new HashSet<>()));
        for (var joined : segmentsByNode(graph).values()) {
            for (var id : joined) neighbors.get(id).addAll(joined);
        }
        // These are exactly max(desired-distance) and min(desired+distance),
        // computed together instead of a breadth-first search from every segment.
        var lower = RoadElevationEnvelope.propagate(desiredElevations, neighbors, true);
        var upper = RoadElevationEnvelope.propagate(desiredElevations, neighbors, false);
        var minimumSources = new HashMap<PlanElementId, Integer>();
        var maximumSources = new HashMap<PlanElementId, Integer>();
        intervals.forEach((id, interval) -> {
            minimumSources.put(id, interval.minimum());
            maximumSources.put(id, interval.maximum());
        });
        var minimum = RoadElevationEnvelope.propagate(minimumSources, neighbors, true);
        var maximum = RoadElevationEnvelope.propagate(maximumSources, neighbors, false);
        var elevations = new HashMap<PlanElementId, Integer>();
        for (var segment : graph.segments()) {
            var id = segment.id();
            long preferred = Math.floorDiv(lower.get(id) + upper.get(id), 2L);
            long low = minimum.getOrDefault(id, Long.MIN_VALUE);
            long high = maximum.getOrDefault(id, Long.MAX_VALUE);
            elevations.put(id, Math.toIntExact(low <= high ? Math.clamp(preferred, low, high) : preferred));
        }
        return elevations;
    }

    private static Map<PlanElementId,Interval> feasibleElevations(SuburbPlanningRequest request,RoadGraph graph,
            Map<PlanElementId,RoadNode> nodes,List<GridBounds> pads) {
        var roads=new HashMap<PlanElementId,GridBounds>();
        graph.segments().forEach(s -> roads.put(s.id(),com.cybersammy.citiesarise.core.road.RoadCorridor.bounds(
                nodes.get(s.startNodeId()).point(),nodes.get(s.endNodeId()).point(), s.width(), s.properties())));
        var occupied=new ArrayList<>(pads); occupied.addAll(roads.values());
        var result=new HashMap<PlanElementId,Interval>();
        int radius=request.settings().terrainTransitions().roadShoulderRadius();
        for(var entry:roads.entrySet()) {
            var bounds=entry.getValue(); int minimum=Integer.MIN_VALUE,maximum=Integer.MAX_VALUE;
            for(int z=bounds.minZ()-radius;z<bounds.maxZExclusive()+radius;z++)
                for(int x=bounds.minX()-radius;x<bounds.maxXExclusive()+radius;x++) {
                    var point=new com.cybersammy.citiesarise.core.geometry.GridPoint(x,z);
                    var cell=request.survey().findCell(point);
                    if(cell.isEmpty()) continue;
                    int ground=cell.orElseThrow().height()-1;
                    if(bounds.contains(point)) {
                        minimum=Math.max(minimum,ground-request.settings().maxCutDepth());
                        maximum=Math.min(maximum,ground+request.settings().maxFillDepth());
                    } else if(occupied.stream().noneMatch(b -> b.contains(point))) {
                        int offset=-com.cybersammy.citiesarise.core.earthwork.RoadTerrainShoulderPolicy.targetElevation(bounds,point,0);
                        maximum=Math.min(maximum,ground+offset+request.settings().terrainTransitions().roadShoulderMaxFillDepth());
                    }
                }
            result.put(entry.getKey(),new Interval(minimum,maximum));
        }
        return result;
    }
    private record Interval(int minimum,int maximum) { }

    private static Map<PlanElementId, List<PlanElementId>> segmentsByNode(RoadGraph graph) {
        Map<PlanElementId, List<PlanElementId>> segments = new HashMap<>();
        for (RoadSegment segment : graph.segments()) {
            addNodeSegment(segments, segment.startNodeId(), segment.id());
            addNodeSegment(segments, segment.endNodeId(), segment.id());
        }
        return segments;
    }

    private static void addNodeSegment(
            Map<PlanElementId, List<PlanElementId>> segmentsByNode,
            PlanElementId nodeId,
            PlanElementId segmentId
    ) {
        segmentsByNode.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(segmentId);
    }

    static RoadNode requiredNode(Map<PlanElementId, RoadNode> nodesById, PlanElementId id) {
        RoadNode node = nodesById.get(id);
        if (node == null) {
            throw new IllegalStateException("road segment references missing node: " + id.value());
        }
        return node;
    }

    private static Map<PlanElementId, RoadNode> nodesById(RoadGraph graph) {
        Map<PlanElementId, RoadNode> nodes = new HashMap<>();
        for (RoadNode node : graph.nodes()) {
            nodes.put(node.id(), node);
        }
        return nodes;
    }

    private static RoadSegment withElevation(RoadSegment segment, int elevation) {
        return new RoadSegment(
                segment.id(),
                segment.startNodeId(),
                segment.endNodeId(),
                segment.width(),
                segment.tags(),
                TerrainPlatform.withElevation(segment.properties(), elevation)
        );
    }

}
