package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.Vec;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Optional decoration uses prepared surfaces only and cannot alter terrain or access decisions. */
final class ParcelOutdoorPlanner {
    private static final Comparator<GridPoint> ORDER=Comparator.comparingInt(GridPoint::z).thenComparingInt(GridPoint::x);
    private ParcelOutdoorPlanner() { }

    static ResolvedParcelOutdoor resolve(Parcel parcel, ParcelContentRule rule, SettlementPlan plan,
            TerrainPreparationPlan preparation, com.cybersammy.citiesarise.core.parcel.ParcelBoundaryPlan boundaries, long seed) {
        var connectors=new HashSet<>(boundaries.reserved());
        boundaries.fences().forEach(c -> connectors.remove(c.point()));
        return resolve(parcel,rule,plan,preparation,boundaries.reserved(),connectors,seed);
    }

    static ResolvedParcelOutdoor resolve(Parcel parcel, ParcelContentRule rule, SettlementPlan plan,
            TerrainPreparationPlan preparation, Set<GridPoint> protectedPoints, long seed) {
        return resolve(parcel,rule,plan,preparation,protectedPoints,Set.of(),seed);
    }
    private static ResolvedParcelOutdoor resolve(Parcel parcel, ParcelContentRule rule, SettlementPlan plan,
            TerrainPreparationPlan preparation, Set<GridPoint> protectedPoints, Set<GridPoint> connectors, long seed) {
        if(rule.outdoorLayouts().isEmpty()) return ResolvedParcelOutdoor.EMPTY;
        var choices=rule.outdoorLayouts().stream().sorted(Comparator.comparing(ParcelOutdoorLayout::id)).toList();
        int choice=new Random(seed^parcel.id().value().hashCode()^rule.id().hashCode()).nextInt(choices.stream().mapToInt(ParcelOutdoorLayout::weight).sum());
        var selected=choices.getFirst();
        for(var candidate:choices) { choice-=candidate.weight(); if(choice<0) {selected=candidate;break;} }
        var frame=ParcelPropLayout.of(plan,preparation,parcel);
        if(frame.isEmpty() || (long)parcel.bounds().size().width()*parcel.bounds().size().depth()>4096)
            return new ResolvedParcelOutdoor(selected.id(),List.of(),Set.of(),List.of());
        var layout=frame.orElseThrow(); var size=layout.size();
        var areas=new ArrayList<ResolvedParcelOutdoor.Area>(); var free=new HashSet<GridPoint>();
        var requested=new HashMap<GridPoint,String>();
        for(var zone:selected.zones()) {
            int x=zone.minX()*size.x()/100,z=zone.minZ()*size.z()/100;
            int w=zone.maxX()*size.x()/100-x,d=zone.maxZ()*size.z()/100-z;
            if(w==0 || d==0) continue;
            var bounds=new GridBounds(layout.world(new GridPoint(x,z),new Vec(w,1,d)),new GridSize(
                    layout.turn()%2==0?w:d,layout.turn()%2==0?d:w));
            if(zone.kind()==ParcelOutdoorLayout.Kind.CONTENT) {
                areas.add(new ResolvedParcelOutdoor.Area(zone.id(),bounds,zone.props())); continue;
            }
            for(int pz=bounds.minZ();pz<bounds.maxZExclusive();pz++) for(int px=bounds.minX();px<bounds.maxXExclusive();px++) {
                var point=new GridPoint(px,pz); free.add(point);
                if(zone.kind()==ParcelOutdoorLayout.Kind.PATH) requested.put(point,zone.material());
            }
        }
        var columns=new HashMap<GridPoint,TerrainPreparationColumn>(); var access=new HashSet<GridPoint>();
        for(var column:preparation.columns()) if(parcel.bounds().contains(column.point())) {
            columns.put(column.point(),column);
            if(column.type()==TerrainPreparationColumnType.BUILDING_ACCESS || column.type()==TerrainPreparationColumnType.BUILDING_ACCESS_STEP)
                access.add(column.point());
        }
        var buildings=plan.buildingSlots().stream().map(BuildingSlot::bounds).toList();
        var roadBounds=new ArrayList<GridBounds>();var nodes=new HashMap<PlanElementId,RoadNode>();
        plan.roadGraph().nodes().forEach(n -> nodes.put(n.id(),n));
        for(var road:plan.roadGraph().segments()) {
            var a=nodes.get(road.startNodeId()).point();var b=nodes.get(road.endNodeId()).point();int half=road.width()/2;
            roadBounds.add(new GridBounds(new GridPoint(Math.min(a.x(),b.x())-half,Math.min(a.z(),b.z())-half),
                    new GridSize(Math.abs(a.x()-b.x())+half*2+1,Math.abs(a.z()-b.z())+half*2+1)));
        }
        plan.roadGraph().bridges().forEach(b -> roadBounds.add(b.bounds()));
        var walk=new HashSet<GridPoint>();
        for(var point:connectors) if(columns.containsKey(point) && buildings.stream().noneMatch(b -> b.contains(point))
                && roadBounds.stream().noneMatch(b -> b.contains(point))
                && (columns.get(point).type()==TerrainPreparationColumnType.PLATFORM || access.contains(point))) walk.add(point);
        requested.keySet().removeIf(p -> protectedPoints.contains(p) || !columns.containsKey(p)
                || buildings.stream().anyMatch(b -> b.contains(p))
                || roadBounds.stream().anyMatch(b -> b.contains(p))
                || (columns.get(p).type()!=TerrainPreparationColumnType.PLATFORM
                    && columns.get(p).type()!=TerrainPreparationColumnType.PARCEL_SHOULDER));
        // Keep only flat authored path components touching existing building access. No new stairs or bridges.
        walk.addAll(requested.keySet());
        var connected=new HashSet<GridPoint>(); var queue=new ArrayDeque<GridPoint>();
        access.stream().sorted(ORDER).forEach(queue::add);
        while(!queue.isEmpty()) {
            var point=queue.removeFirst();
            for(var next:neighbors(point)) if(walk.contains(next) && !connected.contains(next)
                    && columns.get(point).targetElevation()==columns.get(next).targetElevation()) {
                connected.add(next);queue.addLast(next);
            }
        }
        var paths=connected.stream().filter(requested::containsKey).sorted(ORDER)
                .map(p -> new ResolvedParcelOutdoor.PathCell(p,columns.get(p).targetElevation(),requested.get(p))).toList();
        return new ResolvedParcelOutdoor(selected.id(),areas,free,paths);
    }
    private static List<GridPoint> neighbors(GridPoint p) {
        return List.of(new GridPoint(p.x(),p.z()-1),new GridPoint(p.x()+1,p.z()),new GridPoint(p.x(),p.z()+1),new GridPoint(p.x()-1,p.z()));
    }
}
