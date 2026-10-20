package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import java.util.*;
import java.util.function.Function;

/** Replaces one straight arterial, then solves ground access again before accepting any 3D geometry. */
public final class CityOverpassPlanner {
    private CityOverpassPlanner() { }
    private record Candidate(RoadNode a,RoadNode b,boolean alongX,int firstCross,int lastCross,List<RoadSegment> replaced) { }
    public static SuburbPlanningResult attach(SuburbPlanningRequest request,SuburbPlanningResult original,
            Function<SettlementPlan,SuburbPlanningResult> rebuild, java.util.function.Predicate<SuburbPlanningResult> acceptance) {
        var settings=request.settings().terrainTransitions().cityOverpasses();
        if(!settings.enabled() || !original.successful() || original.terrainPreparationPlan().isEmpty()
                || !original.plan().orElseThrow().overpasses().isEmpty())return original;
        var plan=original.plan().orElseThrow();var prep=original.terrainPreparationPlan().orElseThrow();
        var heights=new HashMap<GridPoint,Integer>();prep.columns().forEach(c->heights.put(c.point(),c.targetElevation()));
        int checked=0;
        var modules=new ArrayList<>(settings.modules());
        modules.sort(Comparator.<CityOverpassSettings.Module>comparingDouble(m->-Math.log(unit(request.seed(),m.id()))/m.weight()).thenComparing(CityOverpassSettings.Module::id));
        for(var candidate:candidates(plan,request.seed()))for(var module:modules) {
            var layout=module.layout();int width=layout.streets().corridorWidth(layout.carriageway()),half=width/2;
            int landing=half+1, run=module.rise()*layout.runPerRise()+2*landing;
            int start=coordinate(candidate.a().point(),candidate.alongX()),end=coordinate(candidate.b().point(),candidate.alongX());
            if(candidate.firstCross()-landing-start<run || end-candidate.lastCross()-landing<run
                    || candidate.replaced().stream().anyMatch(e->e.width()!=width))continue;
            var base=heights.get(candidate.a().point());
            if(base==null || !base.equals(heights.get(candidate.b().point())))continue;
            if(++checked>settings.maximumCandidateChecks())return original;
            var roads=plan.roadGraph().segments().stream().filter(e->!candidate.replaced().contains(e)).toList();
            var used=new HashSet<PlanElementId>();roads.forEach(e->{used.add(e.startNodeId());used.add(e.endNodeId());});
            plan.roadGraph().bridges().forEach(e->{used.add(e.startNodeId());used.add(e.endNodeId());});
            if(!used.contains(candidate.a().id()) || !used.contains(candidate.b().id()))continue;
            var ground=new SettlementPlan(plan.id(),new RoadGraph(plan.roadGraph().nodes().stream().filter(n->used.contains(n.id())).toList(),roads,plan.roadGraph().bridges()),
                    plan.parcels(),plan.buildingSlots(),plan.tags(),plan.properties(),plan.districts());
            var rebuilt=rebuild.apply(ground);
            if(!rebuilt.successful() || rebuilt.terrainPreparationPlan().isEmpty())continue;
            try {
                var selected=fit(request,rebuilt,module,candidate,base);
                var enriched=rebuilt.plan().orElseThrow().withOverpasses(List.of(selected));
                var result=SuburbPlanningResult.success(enriched,rebuilt.terrainPreparationPlan().orElseThrow(),rebuilt.siteAssessment().orElseThrow());
                result=result.withContent(rebuilt.content());
                if(acceptance.test(result))return result;
            } catch(IllegalArgumentException rejected) { /* Optional topology keeps the fully valid ground city on failure. */ }
        }
        return original;
    }
    private static double unit(long seed,String id) {
        long h=seed^id.hashCode();h=(h^(h>>>30))*0xbf58476d1ce4e5b9L;h=(h^(h>>>27))*0x94d049bb133111ebL;
        return ((h^(h>>>31))>>>11)*0x1.0p-53+0x1.0p-54;
    }
    private static int coordinate(GridPoint p,boolean alongX) {return alongX?p.x():p.z();}
    private static List<Candidate> candidates(SettlementPlan plan,long seed) {
        var nodes=new HashMap<PlanElementId,RoadNode>();plan.roadGraph().nodes().forEach(n->nodes.put(n.id(),n));
        var adjacent=new HashMap<PlanElementId,List<RoadSegment>>();
        for(var e:plan.roadGraph().segments()) {adjacent.computeIfAbsent(e.startNodeId(),k->new ArrayList<>()).add(e);adjacent.computeIfAbsent(e.endNodeId(),k->new ArrayList<>()).add(e);}
        var candidates=new ArrayList<Candidate>();
        for(boolean axis:List.of(true,false)) {
            int axisStart=candidates.size();
            var lines=new TreeMap<Integer,List<RoadNode>>();
            for(var n:nodes.values()) {
                var incident=adjacent.getOrDefault(n.id(),List.of());
                if(incident.size()!=2 || incident.stream().anyMatch(e->coordinate(nodes.get(e.startNodeId()).point(),!axis)!=coordinate(nodes.get(e.endNodeId()).point(),!axis)))continue;
                lines.computeIfAbsent(coordinate(n.point(),!axis),k->new ArrayList<>()).add(n);
            }
            for(var line:lines.values()) {
                line.sort(Comparator.<RoadNode>comparingInt(n->coordinate(n.point(),axis)).thenComparing(n->n.id().value()));
                pairs: for(var a:line)for(var b:line) {
                    if(candidates.size()-axisStart>=128)break pairs;
                    int start=coordinate(a.point(),axis),end=coordinate(b.point(),axis),cross=coordinate(a.point(),!axis);
                    if(end-start<160 || end-start>480)continue;
                    var replaced=new ArrayList<RoadSegment>();var crossings=new TreeSet<Integer>();
                    for(var e:plan.roadGraph().segments()) {
                        var p=nodes.get(e.startNodeId()).point();var q=nodes.get(e.endNodeId()).point();
                        int p0=coordinate(p,axis),q0=coordinate(q,axis),p1=coordinate(p,!axis),q1=coordinate(q,!axis);
                        if(p1==cross && q1==cross && Math.min(p0,q0)>=start && Math.max(p0,q0)<=end)replaced.add(e);
                        else if(p0==q0 && p0>start && p0<end && Math.min(p1,q1)<=cross && Math.max(p1,q1)>=cross)crossings.add(p0);
                    }
                    long covered=replaced.stream().mapToLong(e->Math.abs(coordinate(nodes.get(e.startNodeId()).point(),axis)-coordinate(nodes.get(e.endNodeId()).point(),axis))).sum();
                    if(covered!=end-start || crossings.isEmpty() || crossings.last()-crossings.first()>240)continue;
                    candidates.add(new Candidate(a,b,axis,crossings.first(),crossings.last(),List.copyOf(replaced)));
                    if(candidates.size()-axisStart>=128)break;
                }
                if(candidates.size()-axisStart>=128)break;
            }
        }
        candidates.sort(Comparator.<Candidate>comparingDouble(c->unit(seed,c.a().id().value()+"/"+c.b().id().value())).thenComparing(c->c.a().id().value()).thenComparing(c->c.b().id().value()));
        return candidates.stream().limit(256).toList();
    }
    private static CityOverpassPlan fit(SuburbPlanningRequest request,SuburbPlanningResult ground,CityOverpassSettings.Module module,Candidate c,int base) {
        var template=module.layout();int width=template.streets().corridorWidth(template.carriageway()),half=width/2,landing=half+1;
        int a=coordinate(c.a().point(),c.alongX()),b=coordinate(c.b().point(),c.alongX()),center=Math.floorDiv(a+b,2),cross=coordinate(c.a().point(),!c.alongX());
        var origin=c.alongX()?new GridPoint(center,cross):new GridPoint(cross,center);
        var id=request.settlementId().child("city-overpass");
        int[] positions={a,c.firstCross()-landing,c.lastCross()+landing,b};
        var localNodes=new ArrayList<RoadNode>();var roads=new ArrayList<RoadSegment>();
        for(int i=0;i<4;i++) {
            var p=c.alongX()?new GridPoint(positions[i]-center,0):new GridPoint(0,positions[i]-center);
            localNodes.add(new RoadNode(id.child("node-"+i),p,Set.of(),PlanProperties.of(LayeredRoadLayout.ELEVATION,""+(i==0 || i==3?0:module.rise()))));
            if(i>0)roads.add(new RoadSegment(id.child("span-"+i),localNodes.get(i-1).id(),localNodes.get(i).id(),width,Set.of(),PlanProperties.of(LayeredRoadLayout.ELEVATED,"true")));
        }
        var layout=new LayeredRoadLayout(new RoadGraph(localNodes,roads),template.streets(),template.carriageway(),template.deckDepth(),template.clearance(),
                template.runPerRise(),template.pierSpacing(),template.maximumSpan(),template.maximumSupportDepth(),template.roadMaterial(),template.deckMaterial(),
                template.pierMaterial(),template.railMaterial(),template.stairMaterial(),template.rampSurface(),template.slabMaterial(),template.sidewalkSlabMaterial());
        var pending=new CityOverpassPlan(id,layout,origin,base,c.a().id(),c.b().id(),request.seed(),List.of());var topology=pending.topology();
        var prepared=new HashMap<GridPoint,TerrainPreparationColumn>();ground.terrainPreparationPlan().orElseThrow().columns().forEach(p->prepared.put(p.point(),p));
        var parcels=ground.plan().orElseThrow().parcels();
        for(var bridge:ground.plan().orElseThrow().roadGraph().bridges())for(int d=0;d<=bridge.length();d++)for(int lateral=-bridge.width()/2;lateral<bridge.width()-bridge.width()/2;lateral++) {
            var point=bridge.point(d,lateral);
            prepared.putIfAbsent(point,new TerrainPreparationColumn(point,bridge.id(),bridge.deckElevation(d),0,0));
        }
        var occupied=new HashMap<GridPoint,IntSummaryStatistics>();
        for(var prop:ground.content().props())for(var cell:prop.composition().cells()) {
            var point=prop.origin().add(cell.position());
            occupied.computeIfAbsent(new GridPoint(point.x(),point.z()),k->new IntSummaryStatistics()).accept(prop.platformY()+point.y());
        }
        for(var column:topology.columns()) {
            var point=pending.world(column.point());var cell=request.survey().findCell(point).orElseThrow(()->new IllegalArgumentException("Overpass outside survey"));
            if(cell.water() || cell.terrainCategory()==com.cybersammy.citiesarise.core.terrain.TerrainCategory.BLOCKED || parcels.stream().anyMatch(p->p.bounds().contains(point)))throw new IllegalArgumentException("Overpass intersects parcel/water");
            var prop=occupied.get(point);
            if(prop!=null && prop.getMax()>=base+column.bottomY() && prop.getMin()<=base+column.walkingY()+layout.clearance())
                throw new IllegalArgumentException("Overpass intersects hosted content");
            var old=prepared.get(point);
            if(conflictsWithGround(pending,column,old))throw new IllegalArgumentException("Overpass blocks ground circulation or earthwork");
            if(cell.height()-1>base+column.surfaceY())throw new IllegalArgumentException("Overpass deck intersects terrain");
        }
        var footings=new ArrayList<CityOverpassPlan.Footing>();
        long volume=topology.columns().stream().mapToLong(col->layout.deckDepth()+(col.step()?1:0)+(col.edge()?1:0)).sum();
        for(var road:roads) {
            var rows=topology.columns().stream().filter(col->col.road().id().equals(road.id()) && col.edge()).toList();int last=-1,length=rows.getFirst().length();
            for(int d=0;d<=length;d++) {
                if(d%layout.pierSpacing()!=0 && d!=length)continue;
                final int row=d;var columns=rows.stream().filter(col->col.distance()==row).toList();var rowFootings=new ArrayList<CityOverpassPlan.Footing>();boolean allowed=true;
                for(var col:columns) {
                    var p=pending.world(col.point());int top=base+col.bottomY()-1;
                    int contact=Math.min(top,request.survey().findCell(p).orElseThrow().height()-1);
                    var old=prepared.get(p);var prop=occupied.get(p);
                    if(prop!=null && prop.getMax()>=contact+1 && prop.getMin()<=top){allowed=false;break;}
                    if(top-contact>layout.maximumSupportDepth() || old!=null && circulation(old) && !pending.landing(col)
                            && top>=old.targetElevation()-1 && contact+1<=old.targetElevation()+layout.clearance()) {allowed=false;break;}
                    rowFootings.add(new CityOverpassPlan.Footing(road.id(),col.point(),contact+1-base,top-base));
                }
                if(!allowed)continue;
                if(last<0 && d!=0 || last>=0 && d-last>layout.maximumSpan())throw new IllegalArgumentException("Unsupported overpass span");
                last=d;footings.addAll(rowFootings);
            }
            if(last!=length)throw new IllegalArgumentException("Overpass endpoint has no footing");
        }
        volume+=footings.stream().mapToLong(f->f.topY()-f.bottomY()+1L).sum();
        if(volume>request.settings().terrainTransitions().cityOverpasses().maximumConstructionVolume()
                || volume+ground.terrainPreparationPlan().orElseThrow().constructionVolume()>request.settings().maxEarthworkVolume())
            throw new IllegalArgumentException("Overpass construction budget");
        return new CityOverpassPlan(id,layout,origin,base,c.a().id(),c.b().id(),request.seed(),footings);
    }
    static boolean conflictsWithGround(CityOverpassPlan module,LayeredRoadTopology.Column column,TerrainPreparationColumn old) {
        if(old==null)return false;
        if(!circulation(old)) {
            // Earthwork has solid volume, but does not reserve a vehicle/pedestrian headroom envelope.
            return old.targetElevation()>=module.baseY()+column.bottomY()
                    && !(module.landing(column) && old.targetElevation()<=module.baseY());
        }
        return !(module.landing(column) && old.targetElevation()==module.baseY())
                && module.baseY()+column.bottomY()<=old.targetElevation()+module.layout().clearance()
                && module.baseY()+column.walkingY()+module.layout().clearance()>=old.targetElevation();
    }
    private static boolean circulation(TerrainPreparationColumn column) {
        return switch(column.type()) {
            case PLATFORM,ROAD_TRANSITION_STEP,BUILDING_ACCESS,BUILDING_ACCESS_STEP -> true;
            case FILL_SLOPE,CUT_SLOPE,BUILDING_SHOULDER,PARCEL_SHOULDER,ROAD_SHOULDER,RETAINING_WALL -> false;
        };
    }
}
