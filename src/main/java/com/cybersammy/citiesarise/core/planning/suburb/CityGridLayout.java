package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import java.util.*;

/** One shared street lattice; plot rejection preserves safe neighbors and never rotates a block. */
final class CityGridLayout {
    static Optional<SettlementPlan> create(SuburbPlanningRequest request) {
        var s=request.settings();var g=s.districts().grid();var survey=request.survey().bounds();
        int spacing=s.parcelLayout().minimumSpacing();
        int shoulder=s.terrainTransitions().roadShoulderRadius();
        int pitchX=g.parcelsAcross()*s.parcelWidth()+(g.parcelsAcross()-1)*spacing+s.corridorWidth()+2*shoulder;
        int pitchZ=g.parcelsDeep()*s.parcelDepth()+(g.parcelsDeep()-1)*spacing+s.corridorWidth()+2*shoulder;
        int width=g.columns()*pitchX+s.corridorWidth(),depth=g.rows()*pitchZ+s.corridorWidth();
        if(width+2*shoulder>survey.size().width() || depth+2*shoulder>survey.size().depth())return Optional.empty();
        int x0=survey.minX()+(survey.size().width()-width)/2+s.corridorWidth()/2;
        int z0=survey.minZ()+(survey.size().depth()-depth)/2+s.corridorWidth()/2;
        var nodes=new ArrayList<RoadNode>();var roads=new ArrayList<RoadSegment>();
        var parcels=new ArrayList<Parcel>();var slots=new ArrayList<BuildingSlot>();var blocks=new ArrayList<DistrictPlan>();
        var tags=Set.of(new PlanTag("urban"));
        for(int z=0;z<=g.rows();z++)for(int x=0;x<=g.columns();x++) {
            var id=request.settlementId().child("grid-node-"+x+"-"+z);
            nodes.add(new RoadNode(id,new GridPoint(x0+x*pitchX,z0+z*pitchZ),tags,PlanProperties.empty()));
            if(x>0)roads.add(new RoadSegment(id.child("west"),request.settlementId().child("grid-node-"+(x-1)+"-"+z),id,s.corridorWidth(),tags,PlanProperties.empty()));
            if(z>0)roads.add(new RoadSegment(id.child("north"),request.settlementId().child("grid-node-"+x+"-"+(z-1)),id,s.corridorWidth(),tags,PlanProperties.empty()));
        }
        for(int z=0;z<g.rows();z++)for(int x=0;x<g.columns();x++) {
            var candidate=new ArrayList<GridBounds>();
            for(int pz=0;pz<g.parcelsDeep();pz++)for(int px=0;px<g.parcelsAcross();px++)
                candidate.add(new GridBounds(new GridPoint(x0+x*pitchX+s.corridorWidth()/2+1+shoulder+px*(s.parcelWidth()+spacing),
                    z0+z*pitchZ+s.corridorWidth()/2+1+shoulder+pz*(s.parcelDepth()+spacing)),new GridSize(s.parcelWidth(),s.parcelDepth())));
            candidate.removeIf(b->!usable(request,b));
            if(candidate.isEmpty() || parcels.size()+candidate.size()>s.targetParcelCount())continue;
            var members=new ArrayList<PlanElementId>();
            for(var bounds:candidate) {
                int i=parcels.size();var id=request.settlementId().child("parcel-"+i);members.add(id);
                parcels.add(new Parcel(id,bounds,tags,PlanProperties.empty()));
                slots.add(new BuildingSlot(request.settlementId().child("building-slot-"+i),id,SuburbParcelGeometry.buildingBounds(s,bounds),tags,PlanProperties.empty()));
            }
            blocks.add(new DistrictPlan(request.settlementId().child("block-"+x+"-"+z),
                new GridBounds(new GridPoint(x0+x*pitchX+s.corridorWidth()/2+1,z0+z*pitchZ+s.corridorWidth()/2+1),
                    new GridSize(pitchX-s.corridorWidth(),pitchZ-s.corridorWidth())),members));
        }
        if(parcels.size()<s.minimumParcelCount())return Optional.empty();
        return Optional.of(retain(new SettlementPlan(request.settlementId(),new RoadGraph(nodes,roads),parcels,slots,tags,PlanProperties.empty(),blocks),Set.of()));
    }
    static boolean borders(SettlementPlan plan,RoadSegment road,DistrictPlan block) {
        var nodes=new HashMap<PlanElementId,GridPoint>();plan.roadGraph().nodes().forEach(n->nodes.put(n.id(),n.point()));
        var a=nodes.get(road.startNodeId());var b=nodes.get(road.endNodeId());var r=block.bounds();int half=road.width()/2;
        if(a.x()==b.x())return (r.minX()==a.x()+half+1 || r.maxXExclusive()==a.x()-half)
                && Math.min(r.maxZExclusive(),Math.max(a.z(),b.z()))>Math.max(r.minZ(),Math.min(a.z(),b.z()));
        return (r.minZ()==a.z()+half+1 || r.maxZExclusive()==a.z()-half)
                && Math.min(r.maxXExclusive(),Math.max(a.x(),b.x()))>Math.max(r.minX(),Math.min(a.x(),b.x()));
    }
    static SettlementPlan retainRoads(SettlementPlan plan,Set<PlanElementId> allowed,int shoulder) {
        var roads=plan.roadGraph().segments().stream().filter(r->allowed.contains(r.id())).toList();
        var nodes=new HashMap<PlanElementId,GridPoint>();plan.roadGraph().nodes().forEach(n->nodes.put(n.id(),n.point()));
        var components=DistrictCityPlanner.components(new RoadGraph(plan.roadGraph().nodes(),roads));
        var counts=new TreeMap<Integer,Integer>();
        for(var parcel:plan.parcels())roads.stream().filter(r->serves(parcel,r,nodes,shoulder)).map(r->components.get(r.startNodeId())).distinct()
                .forEach(component->counts.merge(component,1,Integer::sum));
        int best=counts.entrySet().stream().max(Map.Entry.<Integer,Integer>comparingByValue().thenComparing(e->-e.getKey())).map(Map.Entry::getKey).orElse(-1);
        var kept=roads.stream().filter(r->components.get(r.startNodeId())==best).toList();
        var parcels=plan.parcels().stream().filter(p->kept.stream().anyMatch(r->serves(p,r,nodes,shoulder))).toList();
        var ids=new HashSet<PlanElementId>();parcels.forEach(p->ids.add(p.id()));
        var blocks=plan.districts().stream().filter(b->b.parcels().stream().anyMatch(ids::contains))
                .map(b->new DistrictPlan(b.id(),b.bounds(),b.parcels().stream().filter(ids::contains).toList(),b.footprint(),b.tags(),b.properties())).toList();
        var used=new HashSet<PlanElementId>();kept.forEach(r->{used.add(r.startNodeId());used.add(r.endNodeId());});
        return new SettlementPlan(plan.id(),new RoadGraph(plan.roadGraph().nodes().stream().filter(n->used.contains(n.id())).toList(),kept),
                parcels,plan.buildingSlots().stream().filter(b->ids.contains(b.parcelId())).toList(),plan.tags(),plan.properties(),blocks);
    }
    private static boolean serves(Parcel parcel,RoadSegment road,Map<PlanElementId,GridPoint> nodes,int shoulder) {
        var a=nodes.get(road.startNodeId());var b=nodes.get(road.endNodeId());var p=parcel.bounds();int reach=road.width()/2+1+shoulder;
        if(a.z()==b.z())return Math.min(a.x(),b.x())<=p.minX() && Math.max(a.x(),b.x())>=p.maxXExclusive()-1
                && (a.z()<p.minZ() && p.minZ()-a.z()<=reach || a.z()>=p.maxZExclusive() && a.z()-p.maxZExclusive()+1<=reach);
        return Math.min(a.z(),b.z())<=p.minZ() && Math.max(a.z(),b.z())>=p.maxZExclusive()-1
                && (a.x()<p.minX() && p.minX()-a.x()<=reach || a.x()>=p.maxXExclusive() && a.x()-p.maxXExclusive()+1<=reach);
    }
    static SettlementPlan removeParcels(SettlementPlan plan,Set<PlanElementId> removed) {
        var blocks=plan.districts().stream().filter(b->b.parcels().stream().anyMatch(id->!removed.contains(id))).map(b->new DistrictPlan(b.id(),b.bounds(),b.parcels().stream().filter(id->!removed.contains(id)).toList(),b.footprint(),b.tags(),b.properties()))
                .filter(b->!b.parcels().isEmpty()).toList();
        return retain(new SettlementPlan(plan.id(),plan.roadGraph(),plan.parcels().stream().filter(p->!removed.contains(p.id())).toList(),
                plan.buildingSlots().stream().filter(s->!removed.contains(s.parcelId())).toList(),plan.tags(),plan.properties(),blocks),Set.of());
    }
    static SettlementPlan retain(SettlementPlan plan,Set<PlanElementId> removed) {
        var blocks=plan.districts().stream().filter(b->!removed.contains(b.id())).toList();
        var members=new HashSet<PlanElementId>();blocks.forEach(b->members.addAll(b.parcels()));
        var roads=plan.roadGraph().segments().stream().filter(r->blocks.stream().anyMatch(b->borders(plan,r,b))).toList();
        // Keep the largest connected road component. Disconnected islands are not separate cities.
        var graph=new RoadGraph(plan.roadGraph().nodes(),roads);var components=DistrictCityPlanner.components(graph);
        var counts=new TreeMap<Integer,Integer>();
        for(var b:blocks)plan.roadGraph().segments().stream().filter(r->roads.contains(r) && borders(plan,r,b)).findFirst()
                .ifPresent(r->counts.merge(components.get(r.startNodeId()),b.parcels().size(),Integer::sum));
        int best=counts.entrySet().stream().max(Map.Entry.<Integer,Integer>comparingByValue().thenComparing(e->-e.getKey())).map(Map.Entry::getKey).orElse(-1);
        var keptRoads=roads.stream().filter(r->components.get(r.startNodeId())==best).toList();
        var used=new HashSet<PlanElementId>();keptRoads.forEach(r->{used.add(r.startNodeId());used.add(r.endNodeId());});
        var keptBlocks=blocks.stream().filter(b->keptRoads.stream().anyMatch(r->borders(plan,r,b))).toList();
        members.clear();keptBlocks.forEach(b->members.addAll(b.parcels()));
        return new SettlementPlan(plan.id(),new RoadGraph(plan.roadGraph().nodes().stream().filter(n->used.contains(n.id())).toList(),keptRoads),
            plan.parcels().stream().filter(p->members.contains(p.id())).toList(),plan.buildingSlots().stream().filter(s->members.contains(s.parcelId())).toList(),
            plan.tags(),plan.properties(),keptBlocks);
    }
    private static boolean usable(SuburbPlanningRequest request,GridBounds b) {
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(int z=b.minZ();z<b.maxZExclusive();z++)for(int x=b.minX();x<b.maxXExclusive();x++) {
            var c=request.survey().findCell(new GridPoint(x,z));
            if(c.isEmpty() || c.get().water() || c.get().terrainCategory()==TerrainCategory.BLOCKED)return false;
            low=Math.min(low,c.get().height());high=Math.max(high,c.get().height());
        }
        return high-low<=request.settings().maxCutDepth()+request.settings().maxFillDepth();
    }
}
