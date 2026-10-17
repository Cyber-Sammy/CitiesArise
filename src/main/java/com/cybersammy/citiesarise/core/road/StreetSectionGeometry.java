package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.geometry.*;
import java.util.*;

/** Pure, frozen road-relative surfaces. Width variation never exceeds reserved geometry. */
public final class StreetSectionGeometry {
    public enum Kind { VERGE, SIDEWALK, CARRIAGEWAY, CENTER }
    public record Cell(Kind kind,String module,String material) { }
    public record Section(RoadSegment road,GridPoint start,GridPoint end,StreetSectionSettings settings,int carriageway,long seed,int from,int to) {
        public boolean alongX() {return start.z()==end.z();}
        public Cell sample(GridPoint point) {
            int along=alongX()?point.x():point.z(),cross=alongX()?point.z()-start.z():point.x()-start.x();
            int center=alongX()?start.z():start.x();
            int index=Math.floorDiv(along,settings.sectionLength()),run=Math.floorMod(along,settings.sectionLength());
            var first=choose(index,center);var next=choose(index+1,center);
            if(!first.carriageway()) {
                int taper=(road.width()-first.pathWidth()+1)/2;
                int edgeDistance=Math.min(run,settings.sectionLength()-1-run);
                int pedestrianWidth=Math.min(road.width(),first.pathWidth()+2*Math.max(0,taper-edgeDistance));
                return cross>=-pedestrianWidth/2 && cross<pedestrianWidth-pedestrianWidth/2
                        ?new Cell(Kind.SIDEWALK,first.id(),first.material()):new Cell(Kind.VERGE,first.id(),settings.vergeMaterial());
            }
            int left=width(first.leftWidth(),next.carriageway()?next.leftWidth():settings.sideReservation(),run),
                    right=width(first.rightWidth(),next.carriageway()?next.rightWidth():settings.sideReservation(),run);
            int endpointDistance=Math.min(Math.abs(along-from),Math.abs(along-to));
            if(endpointDistance<=road.width()/2)left=right=settings.sideReservation();
            int low=-carriageway/2,high=low+carriageway-1;
            if(cross>=low && cross<=high) {
                boolean mark=cross==0 && !settings.centerMaterial().isEmpty() && endpointDistance>road.width()/2;
                return new Cell(mark?Kind.CENTER:Kind.CARRIAGEWAY,first.id(),mark?settings.centerMaterial():"");
            }
            if(cross>=low-left && cross<low || cross>high && cross<=high+right)return new Cell(Kind.SIDEWALK,first.id(),first.material());
            return new Cell(Kind.VERGE,first.id(),settings.vergeMaterial());
        }
        private int width(int a,int b,int run) {return Math.floorDiv(a*(settings.sectionLength()-run)+b*run,settings.sectionLength());}
        private StreetSectionSettings.Module choose(int index,int center) {
            long x=seed^((long)index*0x9e3779b97f4a7c15L)^((long)center*0xbf58476d1ce4e5b9L)^(alongX()?17:31);
            x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;x^=x>>>31;
            int total=0;for(var m:settings.modules())total+=m.weight();
            int value=(int)Math.floorMod(x,total);
            for(var m:settings.modules()) {if(value<m.weight())return m;value-=m.weight();}throw new IllegalStateException();
        }
        public GridPoint accessStart(GridPoint anchor) {
            var b=AxisAlignedGridCorridor.bounds(start,end,road.width());
            var edge=new GridPoint(Math.clamp(anchor.x(),b.minX(),b.maxXExclusive()-1),Math.clamp(anchor.z(),b.minZ(),b.maxZExclusive()-1));
            int dx=alongX()?0:Integer.signum(start.x()-edge.x()),dz=alongX()?Integer.signum(start.z()-edge.z()):0;
            for(int n=0;n<road.width();n++) {
                if(sample(edge).kind()!=Kind.VERGE)return edge;
                edge=new GridPoint(edge.x()+dx,edge.z()+dz);
            }
            throw new IllegalStateException("Street has no pedestrian or carriageway access surface");
        }
    }
    private StreetSectionGeometry() { }
    public static RoadGraph annotate(RoadGraph graph,StreetSectionSettings settings,int carriageway,long seed) {
        if(!settings.enabled())return graph;
        var nodes=new HashMap<PlanElementId,GridPoint>();graph.nodes().forEach(n->nodes.put(n.id(),n.point()));
        return new RoadGraph(graph.nodes(),graph.segments().stream().map(r->{
            var a=nodes.get(r.startNodeId());var b=nodes.get(r.endNodeId());boolean x=a.z()==b.z();
            return new RoadSegment(r.id(),r.startNodeId(),r.endNodeId(),r.width(),r.tags(),settings.annotate(r.properties(),carriageway,seed,x?a.x():a.z(),x?b.x():b.z()));
        }).toList(),graph.bridges());
    }
    public static List<Section> sections(RoadGraph graph) {
        var nodes=new HashMap<PlanElementId,GridPoint>();graph.nodes().forEach(n->nodes.put(n.id(),n.point()));
        var result=new ArrayList<Section>();
        for(var r:graph.segments()) {
            var s=StreetSectionSettings.read(r.properties());if(!s.enabled())continue;
            result.add(new Section(r,nodes.get(r.startNodeId()),nodes.get(r.endNodeId()),s,StreetSectionSettings.integer(r.properties(),"carriageway"),
                    Long.parseLong(StreetSectionSettings.value(r.properties(),"seed")),StreetSectionSettings.integer(r.properties(),"from"),StreetSectionSettings.integer(r.properties(),"to")));
        }
        // Explicit bank-to-bank links are separate from road segments. Inherit the
        // frozen street contract of a connected frontage, retaining the bridge width.
        var roads=List.copyOf(result);
        for(var bridge:graph.bridges()) {
            var frontage=roads.stream().filter(s->s.road().startNodeId().equals(bridge.startNodeId())
                    || s.road().endNodeId().equals(bridge.startNodeId()) || s.road().startNodeId().equals(bridge.endNodeId())
                    || s.road().endNodeId().equals(bridge.endNodeId()))
                    .min(Comparator.comparing(s->s.road().id().value()));
            if(frontage.isEmpty())continue;
            var source=frontage.orElseThrow();boolean x=bridge.alongX();
            var road=new RoadSegment(bridge.id(),bridge.startNodeId(),bridge.endNodeId(),bridge.width(),Set.of(),PlanProperties.empty());
            result.add(new Section(road,bridge.start(),bridge.end(),source.settings(),source.carriageway(),source.seed(),
                    Math.min(x?bridge.start().x():bridge.start().z(),x?bridge.end().x():bridge.end().z()),
                    Math.max(x?bridge.start().x():bridge.start().z(),x?bridge.end().x():bridge.end().z())));
        }
        result.sort(Comparator.comparing(s->s.road().id().value()));
        return List.copyOf(result);
    }
    public static Map<GridPoint,Cell> surfaces(RoadGraph graph) {
        var result=new HashMap<GridPoint,Cell>();
        for(var section:sections(graph)) {
            var b=AxisAlignedGridCorridor.bounds(section.start(),section.end(),section.road().width());
            for(int z=b.minZ();z<b.maxZExclusive();z++)for(int x=b.minX();x<b.maxXExclusive();x++) {
                var p=new GridPoint(x,z);var cell=section.sample(p);var old=result.get(p);
                if(old==null || cell.kind().ordinal()>old.kind().ordinal())result.put(p,cell);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
