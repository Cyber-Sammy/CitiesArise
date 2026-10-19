package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.building.BuildingSelectionContext;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.road.*;
import java.util.*;

/** Resolves host permissions while keeping a continuous walking strip along every street. */
final class PlacementZonePlanner {
    record Result(List<ResolvedPlacementZone> zones,Set<GridPoint> walking,Set<GridPoint> roadBlocked) { }
    private PlacementZonePlanner() { }
    static Result resolve(SettlementPlan plan,TerrainPreparationPlan preparation,List<PlacementZoneRule> rules) {
        if(rules.isEmpty())return new Result(List.of(),Set.of(),Set.of());
        var surfaces=StreetSectionGeometry.surfaces(plan.roadGraph());
        var walking=new HashSet<GridPoint>();var roadBlocked=new HashSet<GridPoint>();
        var nodes=new HashMap<PlanElementId,GridPoint>();plan.roadGraph().nodes().forEach(n->nodes.put(n.id(),n.point()));
        var streetIds=plan.roadGraph().segments().stream().map(RoadSegment::id).collect(java.util.stream.Collectors.toSet());
        for(var road:plan.roadGraph().segments()) {
            var b=RoadCorridor.bounds(nodes.get(road.startNodeId()),nodes.get(road.endNodeId()),road.width(),road.properties());
            for(int z=b.minZ();z<b.maxZExclusive();z++)for(int x=b.minX();x<b.maxXExclusive();x++) {
                var p=new GridPoint(x,z);var cell=surfaces.get(p);
                if(cell==null || cell.kind()!=StreetSectionGeometry.Kind.SIDEWALK)roadBlocked.add(p);
            }
        }
        var sections=StreetSectionGeometry.sections(plan.roadGraph());
        for(var section:sections) {
            var road=section.road();var b=RoadCorridor.bounds(section.start(),section.end(),road.width(),road.properties());
            int width=rules.stream().filter(r->r.host()==PlacementZoneRule.Host.SIDEWALK && matches(r,plan,road))
                    .mapToInt(PlacementZoneRule::minimumWalkWidth).max().orElse(2);
            int from=section.alongX()?b.minX():b.minZ(),to=section.alongX()?b.maxXExclusive():b.maxZExclusive();
            for(int along=from;along<to;along++) {
                var sides=List.of(new ArrayList<GridPoint>(),new ArrayList<GridPoint>());
                int center=section.alongX()?section.start().z():section.start().x();
                int low=section.alongX()?b.minZ():b.minX(),high=section.alongX()?b.maxZExclusive():b.maxXExclusive();
                boolean junction=Math.min(Math.abs((long)along-section.from()),Math.abs((long)along-section.to()))<=road.width()/2;
                for(int cross=low;cross<high;cross++) {
                    var p=section.alongX()?new GridPoint(along,cross):new GridPoint(cross,along);var cell=surfaces.get(p);
                    if(cell==null || cell.kind()!=StreetSectionGeometry.Kind.SIDEWALK)continue;
                    if(junction)walking.add(p);
                    sides.get(cross<center?0:1).add(p);
                }
                for(var side:sides)side.stream().sorted(Comparator.comparingInt(p->Math.abs((section.alongX()?p.z():p.x())-center)))
                        .limit(width).forEach(walking::add);
            }
        }
        for(var bridge:plan.roadGraph().bridges())for(int z=bridge.bounds().minZ();z<bridge.bounds().maxZExclusive();z++)
            for(int x=bridge.bounds().minX();x<bridge.bounds().maxXExclusive();x++)roadBlocked.add(new GridPoint(x,z));
        var prepared=new HashSet<GridPoint>();preparation.columns().forEach(c->prepared.add(c.point()));
        var metadata=new BuildingSelectionContext(plan);var zones=new ArrayList<ResolvedPlacementZone>();
        for(var rule:rules.stream().sorted(Comparator.comparing(PlacementZoneRule::id)).toList()) {
            if(rule.host()==PlacementZoneRule.Host.SIDEWALK) {
                for(var section:sections) {
                    if(!streetIds.contains(section.road().id()))continue;
                    if(!matches(rule,plan,section.road()))continue;
                    var b=RoadCorridor.bounds(section.start(),section.end(),section.road().width(),section.road().properties());
                    var bounds=rectangle(b,rule);if(bounds.isEmpty())continue;
                    add(zones,rule,section.road().id(),bounds.orElseThrow(),p->prepared.contains(p) && !roadBlocked.contains(p)
                            && !walking.contains(p) && surfaces.containsKey(p) && surfaces.get(p).kind()==StreetSectionGeometry.Kind.SIDEWALK);
                }
            } else for(var parcel:plan.parcels().stream().sorted(Comparator.comparing(p->p.id().value())).toList()) {
                var data=metadata.forParcel(parcel.id());if(!rule.match().matches(data.tags(),data.properties()))continue;
                var frame=ParcelPropLayout.of(plan,preparation,parcel);GridBounds bounds;
                if(frame.isPresent()) {
                    var f=frame.orElseThrow();var local=rectangle(new GridBounds(new GridPoint(0,0),new GridSize(f.size().x(),f.size().z())),rule);
                    if(local.isEmpty())continue;var b=local.orElseThrow();
                    bounds=new GridBounds(f.world(b.origin(),new ModuleDefinition.Vec(b.size().width(),1,b.size().depth())),
                            new GridSize(f.turn()%2==0?b.size().width():b.size().depth(),f.turn()%2==0?b.size().depth():b.size().width()));
                } else {var b=rectangle(parcel.bounds(),rule);if(b.isEmpty())continue;bounds=b.orElseThrow();}
                add(zones,rule,parcel.id(),bounds,p->prepared.contains(p) && !roadBlocked.contains(p)
                        && !surfaces.containsKey(p) && (rule.host()!=PlacementZoneRule.Host.PARCEL_EDGE || edgeDistance(parcel.bounds(),p)<rule.edgeDepth()));
            }
        }
        return new Result(List.copyOf(zones),Collections.unmodifiableSet(walking),Collections.unmodifiableSet(roadBlocked));
    }
    private static boolean matches(PlacementZoneRule rule,SettlementPlan plan,RoadSegment road) {
        return rule.match().matches(MetadataSelector.union(plan.tags(),road.tags()),MetadataSelector.overlay(plan.properties(),road.properties()));
    }
    private static Optional<GridBounds> rectangle(GridBounds host,PlacementZoneRule rule) {
        int x=host.minX()+host.size().width()*rule.minX()/100+rule.setback(),z=host.minZ()+host.size().depth()*rule.minZ()/100+rule.setback();
        int maxX=host.minX()+host.size().width()*rule.maxX()/100-rule.setback(),maxZ=host.minZ()+host.size().depth()*rule.maxZ()/100-rule.setback();
        return maxX<=x || maxZ<=z?Optional.empty():Optional.of(new GridBounds(new GridPoint(x,z),new GridSize(maxX-x,maxZ-z)));
    }
    private static int edgeDistance(GridBounds b,GridPoint p) {
        return Math.min(Math.min(p.x()-b.minX(),b.maxXExclusive()-1-p.x()),Math.min(p.z()-b.minZ(),b.maxZExclusive()-1-p.z()));
    }
    private static void add(List<ResolvedPlacementZone> zones,PlacementZoneRule rule,PlanElementId source,GridBounds b,
            java.util.function.Predicate<GridPoint> permits) {
        if((long)b.size().width()*b.size().depth()>4096 || zones.size()>=1024)return;
        var allowed=new HashSet<GridPoint>();
        for(int z=b.minZ();z<b.maxZExclusive();z++)for(int x=b.minX();x<b.maxXExclusive();x++) {var p=new GridPoint(x,z);if(permits.test(p))allowed.add(p);}
        if(!allowed.isEmpty())zones.add(new ResolvedPlacementZone(rule,source,b,allowed));
    }
}
