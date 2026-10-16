package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.road.RoadTerrainEvaluationCache;
import java.util.*;

/** Tries the direct route, then at most twelve bounded switchback corridors with validated grades. */
final class GradeAwareRoadPlanner {
    static final PlanPropertyKey GRADED=new PlanPropertyKey("graded_road");
    private GradeAwareRoadPlanner() { }
    static Optional<RoadGraph> route(SuburbPlanningRequest request,GridBounds bounds,RoadGraph source,
            List<GridBounds> reserved,int startY,int endY) {
        return route(request,bounds,source,reserved,startY,endY,g->true);
    }
    static Optional<RoadGraph> route(SuburbPlanningRequest request,GridBounds bounds,RoadGraph source,
            List<GridBounds> reserved,int startY,int endY,java.util.function.Predicate<RoadGraph> acceptance) {
        if(source.segments().size()!=1 || bounds.size().width()>128 || bounds.size().depth()>128)return Optional.empty();
        var adaptation=new com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlanner()
                .plan(request.survey(),request.settings().maxBuildableSlope(),request.terrainResponsePolicy());
        return route(request,bounds,source,reserved,startY,endY,acceptance,adaptation);
    }
    static Optional<RoadGraph> route(SuburbPlanningRequest request,GridBounds bounds,RoadGraph source,
            List<GridBounds> reserved,int startY,int endY,java.util.function.Predicate<RoadGraph> acceptance,
            com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationPlan adaptation) {
        if(source.segments().size()!=1)return Optional.empty();
        if(bounds.size().width()>128 || bounds.size().depth()>128)return Optional.empty();
        var edge=source.segments().getFirst();var nodes=new HashMap<PlanElementId,RoadNode>();source.nodes().forEach(n->nodes.put(n.id(),n));
        var start=nodes.get(edge.startNodeId());var end=nodes.get(edge.endNodeId());var a=start.point();var b=end.point();
        var policy=request.settings().districts().elevation();
        int direct=distance(a,b),maximum=direct+policy.maximumDetourLength();
        var paths=new ArrayList<List<GridPoint>>();paths.add(List.of(a,b));
        var offsets=new LinkedHashSet<Integer>(List.of(policy.roadRunPerRise()*2,policy.roadRunPerRise()*4,policy.maximumDetourLength()/2));
        for(int offset:offsets)for(int sign:new int[]{-1,1})for(boolean lateralX:new boolean[]{true,false}) {
            if(offset<=0)continue;
            int cross=lateralX?(sign<0?Math.min(a.x(),b.x())-offset:Math.max(a.x(),b.x())+offset)
                    :(sign<0?Math.min(a.z(),b.z())-offset:Math.max(a.z(),b.z())+offset);
            var p=lateralX?new GridPoint(cross,a.z()):new GridPoint(a.x(),cross);
            var q=lateralX?new GridPoint(cross,b.z()):new GridPoint(b.x(),cross);
            if(p.equals(q))continue;
            var path=List.of(a,p,q,b);
            int length=distance(a,p)+distance(p,q)+distance(q,b);
            if(length<=maximum && path.stream().allMatch(bounds::contains))paths.add(path);
        }
        var router=new TerrainAwareRoadGraphRouter();
        var context=router.routingContext(request.terrainResponsePolicy(),adaptation);var cache=new RoadTerrainEvaluationCache();
        int index=0;
        for(var path:paths) {
            var candidate=source;
            if(path.size()>2) {
                var waypoints=new ArrayList<RoadNode>();waypoints.add(start);
                for(int i=1;i<path.size()-1;i++)waypoints.add(new RoadNode(edge.id().child("detour-"+index+"-node-"+i),path.get(i),Set.of(),PlanProperties.empty()));
                waypoints.add(end);var links=new ArrayList<RoadSegment>();
                for(int i=1;i<waypoints.size();i++)links.add(new RoadSegment(edge.id().child("detour-"+index+"-part-"+i),
                        waypoints.get(i-1).id(),waypoints.get(i).id(),edge.width(),edge.tags(),edge.properties()));
                candidate=new RoadGraph(waypoints,links);
            }
            index++;
            var routed=path.size()==2?router.routePlanar(request,bounds,candidate,reserved,context,cache)
                    :router.routeCorridor(request,bounds,candidate,reserved,context,cache);
            if(routed.isEmpty() || path.size()>2 && !simple(routed.orElseThrow()))continue;
            var graph=routed.orElseThrow();var points=new HashMap<PlanElementId,GridPoint>();graph.nodes().forEach(n->points.put(n.id(),n.point()));
            long length=graph.segments().stream().mapToLong(s->distance(points.get(s.startNodeId()),points.get(s.endNodeId()))).sum();
            if(path.size()>2 && length>maximum || length<(long)Math.abs((long)startY-endY)*policy.roadRunPerRise())continue;
            var graded=TerrainConnectorGrader.grade(request,RoadGraphSegmenter.splitLongSegments(graph,6),startY,endY,reserved);
            if(graded.isPresent()) {
                var accepted=graded.orElseThrow();
                var candidateGraph=new RoadGraph(accepted.nodes(),accepted.segments().stream().map(s->new RoadSegment(s.id(),s.startNodeId(),s.endNodeId(),s.width(),s.tags(),
                        s.properties().with(GRADED,"true"))).toList());
                if(acceptance.test(candidateGraph))return Optional.of(candidateGraph);
            }
        }
        return Optional.empty();
    }
    private static boolean simple(RoadGraph graph) {
        var nodes=new HashMap<PlanElementId,GridPoint>();graph.nodes().forEach(n->nodes.put(n.id(),n.point()));
        for(int i=0;i<graph.segments().size();i++)for(int j=i+1;j<graph.segments().size();j++) {
            var a=graph.segments().get(i);var b=graph.segments().get(j);
            if(a.startNodeId().equals(b.startNodeId()) || a.startNodeId().equals(b.endNodeId())
                    || a.endNodeId().equals(b.startNodeId()) || a.endNodeId().equals(b.endNodeId()))continue;
            var x=AxisAlignedGridCorridor.bounds(nodes.get(a.startNodeId()),nodes.get(a.endNodeId()),a.width());
            var y=AxisAlignedGridCorridor.bounds(nodes.get(b.startNodeId()),nodes.get(b.endNodeId()),b.width());
            if(x.minX()<y.maxXExclusive() && y.minX()<x.maxXExclusive() && x.minZ()<y.maxZExclusive() && y.minZ()<x.maxZExclusive())return false;
        }
        return true;
    }
    private static int distance(GridPoint a,GridPoint b) {return Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z());}
}
