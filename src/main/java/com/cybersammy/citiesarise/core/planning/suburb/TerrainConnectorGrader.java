package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Grades a routed chain against terrain, keeping complete endpoint decks and configured spacing between steps. */
final class TerrainConnectorGrader {
    private TerrainConnectorGrader() { }

    static Optional<RoadGraph> grade(SuburbPlanningRequest request, RoadGraph graph, int startY, int endY) {
        return grade(request, graph, startY, endY, List.of());
    }

    static Optional<RoadGraph> grade(SuburbPlanningRequest request, RoadGraph graph, int startY, int endY,
            List<GridBounds> occupied) {
        if (graph.segments().isEmpty()) return Optional.empty();
        var points = new HashMap<PlanElementId, GridPoint>();
        graph.nodes().forEach(n -> points.put(n.id(), n.point()));
        var preparedBounds = new ArrayList<>(occupied);
        graph.segments().forEach(s -> preparedBounds.add(com.cybersammy.citiesarise.core.road.RoadCorridor.bounds(
                points.get(s.startNodeId()),points.get(s.endNodeId()), s.width(), s.properties())));
        var profiles = new ArrayList<int[]>();
        var lengths = new ArrayList<Integer>();
        var shoulderCeilings = new ArrayList<Integer>();
        var endpointLevels = new ArrayList<OptionalInt>();
        var firstSegment=graph.segments().getFirst();var lastSegment=graph.segments().getLast();
        var startJunction=junction(points.get(firstSegment.startNodeId()),firstSegment.width());
        var endJunction=junction(points.get(lastSegment.endNodeId()),lastSegment.width());
        for (var segment : graph.segments()) {
            var a = points.get(segment.startNodeId()); var b = points.get(segment.endNodeId());
            var bounds = com.cybersammy.citiesarise.core.road.RoadCorridor.bounds(a, b, segment.width(), segment.properties());
            boolean startOverlap=bounds.intersects(startJunction),endOverlap=bounds.intersects(endJunction);
            if(request.settings().districts().elevation().enabled() && startOverlap && endOverlap && startY!=endY)return Optional.empty();
            endpointLevels.add(!request.settings().districts().elevation().enabled()?OptionalInt.empty():
                    startOverlap?OptionalInt.of(startY):endOverlap?OptionalInt.of(endY):OptionalInt.empty());
            if (!request.survey().bounds().contains(bounds)) return Optional.empty();
            int[] heights = new int[bounds.size().width()*bounds.size().depth()];
            int index=0;
            for (int z=bounds.minZ(); z<bounds.maxZExclusive(); z++) for (int x=bounds.minX(); x<bounds.maxXExclusive(); x++) {
                var cell = request.survey().findCell(new GridPoint(x,z)).orElseThrow();
                if (cell.water()) return Optional.empty();
                heights[index++] = cell.height()-1;
            }
            profiles.add(heights);
            int ceiling = Integer.MAX_VALUE;
            int radius = request.settings().terrainTransitions().roadShoulderRadius();
            for (int z=bounds.minZ()-radius; z<bounds.maxZExclusive()+radius; z++)
                for (int x=bounds.minX()-radius; x<bounds.maxXExclusive()+radius; x++) {
                    var point = new GridPoint(x,z);
                    if (!com.cybersammy.citiesarise.core.earthwork.RoadTerrainShoulderPolicy.contains(bounds,point,radius)
                            || preparedBounds.stream().anyMatch(reservation -> reservation.contains(point))) continue;
                    var cell = request.survey().findCell(point);
                    if (cell.isEmpty()) continue;
                    // Shoulders descend one block per lateral row and have their own fill limit.
                    int offset = -com.cybersammy.citiesarise.core.earthwork.RoadTerrainShoulderPolicy.targetElevation(bounds,point,0);
                    ceiling = Math.min(ceiling, cell.orElseThrow().height()-1+offset
                            +request.settings().terrainTransitions().roadShoulderMaxFillDepth());
                }
            shoulderCeilings.add(ceiling);
            lengths.add(Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z()));
        }
        Comparator<Key> order = Comparator.comparingInt(Key::y).thenComparingInt(Key::sinceStep);
        var policy=request.settings().districts().elevation();
        int run=policy.enabled()?policy.roadRunPerRise():6;
        Map<Key, State> states = new TreeMap<>(order);
        long initialCost = cost(request, profiles.getFirst(), startY);
        if (initialCost == Long.MAX_VALUE || startY>shoulderCeilings.getFirst()) return Optional.empty();
        states.put(new Key(startY,0), new State(startY, initialCost, null));
        for (int i=1; i<profiles.size(); i++) {
            Map<Key, State> next = new TreeMap<>(order);
            for (var entry : states.entrySet()) {
                int distance = Math.min(run, entry.getKey().sinceStep()+lengths.get(i-1));
                for (int delta=-1; delta<=1; delta++) {
                    if (delta!=0 && distance<run) continue;
                    int y = entry.getKey().y()+delta;
                    // A short final segment can leave the previous stair inside the endpoint
                    // junction. Keep the entire shared deck at the adjoining street's level.
                    if(endpointLevels.get(i).isPresent() && endpointLevels.get(i).getAsInt()!=y)continue;
                    if (y>shoulderCeilings.get(i)) continue;
                    if (Math.abs((long)y-endY)>profiles.size()-i-1) continue;
                    long earthwork = cost(request, profiles.get(i), y);
                    if (earthwork==Long.MAX_VALUE) continue;
                    var key = new Key(y, delta==0 ? distance : 0);
                    var state = new State(y, entry.getValue().cost()+earthwork
                            +(policy.enabled() && delta!=0?policy.levelChangeCost():0), entry.getValue());
                    if (!next.containsKey(key) || state.cost()<next.get(key).cost()) next.put(key,state);
                }
            }
            if (next.isEmpty()) return Optional.empty();
            states=next;
        }
        var best = states.values().stream().filter(s -> s.y()==endY).min(Comparator.comparingLong(State::cost));
        if (best.isEmpty()) return Optional.empty();
        int[] levels = new int[profiles.size()];
        var state=best.orElseThrow();
        for (int i=levels.length-1;i>=0;i--) { levels[i]=state.y(); state=state.previous(); }
        var segments = new ArrayList<RoadSegment>();
        for (int i=0;i<levels.length;i++) {
            var s=graph.segments().get(i);
            segments.add(new RoadSegment(s.id(),s.startNodeId(),s.endNodeId(),s.width(),s.tags(),
                    s.properties().with(PlanPropertyKeys.PLATFORM_Y,Integer.toString(levels[i]))));
        }
        return Optional.of(new RoadGraph(graph.nodes(),segments));
    }

    private static GridBounds junction(GridPoint point,int width) {
        return new GridBounds(new GridPoint(point.x()-width/2,point.z()-width/2),new GridSize(width,width));
    }

    private static long cost(SuburbPlanningRequest request, int[] heights, int level) {
        long sum=0;
        for (int height:heights) {
            long delta=(long)level-height;
            if (delta>request.settings().maxFillDepth() || -delta>request.settings().maxCutDepth()) return Long.MAX_VALUE;
            sum+=Math.abs(delta);
        }
        return sum;
    }
    private record Key(int y,int sinceStep) { }
    private record State(int y,long cost,State previous) { }
}
