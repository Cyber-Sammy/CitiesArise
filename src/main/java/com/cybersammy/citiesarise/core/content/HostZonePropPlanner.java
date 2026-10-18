package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.building.BuildingContent;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.parcel.ParcelBoundaryPlan;
import com.cybersammy.citiesarise.core.road.RoadCorridor;
import java.util.*;

/** Bounded optional placement on approved hosts, using the same prop modules and frozen output. */
final class HostZonePropPlanner {
    private static final Comparator<GridPoint> ORDER=Comparator.comparingInt(GridPoint::z).thenComparingInt(GridPoint::x);
    private static final int MAX_ATTEMPTS=32768,MAX_PROPS=256;
    private record Pose(ResolvedComposition composition,Vec size,Joint access) { }
    private record HostKey(PlanElementId source,String rule) { }
    private HostZonePropPlanner() { }
    static List<ResolvedProp> plan(SettlementPlan plan,TerrainPreparationPlan preparation,List<PropRule> rules,
            PlacementZonePlanner.Result zones,Map<PlanElementId,BuildingContent> buildings,
            Map<PlanElementId,ResolvedParcelContent> parcels,ParcelBoundaryPlan boundaries,long seed) {
        if(zones.zones().isEmpty() || rules.stream().noneMatch(PropRule::zoneAnchor))return List.of();
        var columns=new HashMap<GridPoint,TerrainPreparationColumn>();preparation.columns().forEach(c->columns.put(c.point(),c));
        var occupied=new HashSet<>(zones.roadBlocked());occupied.addAll(zones.walking());occupied.addAll(boundaries.reserved());
        var hard=new HashSet<>(zones.roadBlocked());var goals=new HashSet<>(zones.walking());
        var hosts=new HashMap<PlanElementId,GridBounds>();plan.parcels().forEach(p->hosts.put(p.id(),p.bounds()));
        var nodes=new HashMap<PlanElementId,GridPoint>();plan.roadGraph().nodes().forEach(n->nodes.put(n.id(),n.point()));
        plan.roadGraph().segments().forEach(r->hosts.put(r.id(),RoadCorridor.bounds(nodes.get(r.startNodeId()),nodes.get(r.endNodeId()),r.width(),r.properties())));
        for(var slot:plan.buildingSlots())addRectangle(slot.bounds(),hard);
        boundaries.fences().forEach(f->hard.add(f.point()));boundaries.alleys().forEach(a->goals.add(a.point()));
        for(var column:preparation.columns())if(column.type()!=TerrainPreparationColumnType.PLATFORM) {
            occupied.add(column.point());
            if(column.type()==TerrainPreparationColumnType.BUILDING_ACCESS)goals.add(column.point());
        }
        for(var parcel:parcels.values()) {
            occupied.addAll(parcel.outdoor().free());hard.addAll(parcel.outdoor().free());
            parcel.outdoor().paths().forEach(p->goals.add(p.point()));
        }
        for(var slot:plan.buildingSlots()) {
            var building=buildings.get(slot.id());if(building==null)continue;
            for(var entry:building.resolvedEntrances()) {
                occupied.addAll(entry.reserved());
                entry.composition().cells().forEach(c->hard.add(new GridPoint(slot.bounds().minX()+c.position().x(),slot.bounds().minZ()+c.position().z())));
                entry.path().forEach(p->goals.add(p.point()));
            }
        }
        // Free authored paths remain usable goals, while fences and building volumes stay blocked.
        for(var parcel:parcels.values())parcel.outdoor().paths().forEach(p->hard.remove(p.point()));
        occupied.addAll(hard);occupied.addAll(goals);
        var result=new ArrayList<ResolvedProp>();var zoneCounts=new HashMap<HostKey,Integer>();var hostCounts=new HashMap<HostKey,Integer>();int attempts=0;
        for(var rule:rules.stream().filter(PropRule::zoneAnchor).sorted(Comparator.comparing(PropRule::id)).toList()) {
            var options=rule.hostZone();
            for(var zone:zones.zones()) {
                if(!options.zones().contains(zone.rule().id()) || !zone.rule().categories().contains(options.category()))continue;
                var zoneKey=new HostKey(zone.source(),zone.rule().id());var hostKey=new HostKey(zone.source(),rule.id());
                long localSeed=seed^(zone.source().value()+"/"+zone.rule().id()).hashCode()^rule.id().hashCode();
                if(new Random(localSeed).nextInt(100)>=rule.chance())continue;
                var candidates=new ArrayList<>(zone.allowed().stream().filter(p->Math.floorMod(p.x()-zone.bounds().minX(),rule.spacing())==0
                        && Math.floorMod(p.z()-zone.bounds().minZ(),rule.spacing())==0).sorted(ORDER).toList());Collections.shuffle(candidates,new Random(localSeed));
                var modules=weighted(rule,localSeed);var poses=new HashMap<String,Optional<Pose>>();
                int visited=0;
                for(var origin:candidates) {
                    if(visited++>=128 || zoneCounts.getOrDefault(zoneKey,0)>=zone.rule().maximumCount() || hostCounts.getOrDefault(hostKey,0)>=rule.maxCount())break;
                    boolean accepted=false;
                    for(var module:modules) {
                        if(module.size().y()>Math.min(options.maximumHeight(),zone.rule().maximumHeight()))continue;
                        for(int turn=0;turn<4;turn++) {
                            if(++attempts>MAX_ATTEMPTS || result.size()>=MAX_PROPS)return List.copyOf(result);
                            final int rotation=turn;
                            var pose=poses.computeIfAbsent(module.id()+"/"+turn,k->pose(rule,module,rotation,localSeed));
                            if(pose.isEmpty())continue;var placed=pose.orElseThrow();
                            var column=columns.get(origin);if(column==null)continue;int y=column.targetElevation();
                            var footprint=footprint(origin,placed.size());
                            if(footprint.stream().anyMatch(p->!zone.allowed().contains(p) || occupied.contains(p) || !level(columns.get(p),y)))continue;
                            var clearance=new HashSet<GridPoint>();
                            for(var p:footprint)for(int dz=-rule.clearance();dz<=rule.clearance();dz++)for(int dx=-rule.clearance();dx<=rule.clearance();dx++)
                                clearance.add(new GridPoint(p.x()+dx,p.z()+dz));
                            if(clearance.stream().anyMatch(p->occupied.contains(p) || !hosts.get(zone.source()).contains(p)))continue;
                            var body=ModuleDefinition.jointCells(placed.access()).getFirst();var normal=ModuleBoundary.normal(placed.access().face());
                            var start=new GridPoint(origin.x()+body.x()+normal.x(),origin.z()+body.z()+normal.z());
                            var path=route(start,hosts.get(zone.source()),columns,hard,footprint,goals,y,options.maximumPathLength());
                            if(path.isEmpty())continue;
                            var reserved=new HashSet<>(clearance);reserved.addAll(path.orElseThrow());
                            var access=path.orElseThrow().stream().map(p->new ResolvedParcelOutdoor.PathCell(p,y,options.pathMaterial())).toList();
                            result.add(new ResolvedProp(rule.id(),zone.source(),new Vec(origin.x(),0,origin.z()),y,placed.composition(),rule.materials(),
                                    Optional.of(new ResolvedProp.ZonePlacement(zone.rule().id(),zone.rule().host(),reserved,access,rule.airMaterial()))));
                            zoneCounts.merge(zoneKey,1,Integer::sum);hostCounts.merge(hostKey,1,Integer::sum);
                            occupied.addAll(reserved);hard.addAll(footprint);goals.addAll(path.orElseThrow());accepted=true;break;
                        }
                        if(accepted)break;
                    }
                }
            }
        }
        return List.copyOf(result);
    }
    private static Optional<Pose> pose(PropRule rule,ModuleDefinition module,int turn,long seed) {
        if(!module.tags().containsAll(rule.requiredTags()) || !Collections.disjoint(module.tags(),rule.forbiddenTags()))return Optional.empty();
        var composition=PropCompositionResolver.resolve(rule,module,turn,seed);if(composition.isEmpty())return Optional.empty();
        var c=composition.orElseThrow();var size=CompositionAssembler.rotatedSize(module.size(),turn);
        if(c.cells().stream().anyMatch(cell->cell.position().x()<0 || cell.position().x()>=size.x() || cell.position().z()<0
                || cell.position().z()>=size.z() || cell.position().y()<0 || cell.position().y()>=size.y()))return Optional.empty();
        var root=c.modules().getFirst();var access=module.jointsFor(root.variant(),root.layout()).stream()
                .filter(j->j.id().equals(rule.hostZone().accessJoint()) && j.external() && j.face().ordinal()<4 && j.height()>=2 && j.position().y()==1).findFirst();
        if(access.isEmpty())return Optional.empty();var rotated=CompositionAssembler.rotate(access.orElseThrow(),module.size(),turn);
        var cells=new HashMap<Vec,String>();c.cells().forEach(cell->cells.put(cell.position(),cell.material()));
        for(var p:ModuleDefinition.jointCells(rotated)) {
            if(!rule.materialRules().isPassable(cells.getOrDefault(p,""),rule.airMaterial()))return Optional.empty();
            if(p.y()==1 && !rule.materialRules().supportive().contains(cells.getOrDefault(p.add(new Vec(0,-1,0)),"")))return Optional.empty();
        }
        return Optional.of(new Pose(c,size,rotated));
    }
    private static List<ModuleDefinition> weighted(PropRule rule,long seed) {
        var remaining=new ArrayList<>(rule.pool().stream().sorted(Comparator.comparing(ModuleDefinition::id)).toList());
        var result=new ArrayList<ModuleDefinition>();var random=new Random(seed);
        while(!remaining.isEmpty()) {
            int ticket=random.nextInt(remaining.stream().mapToInt(m->rule.hostZone().weights().getOrDefault(m.id(),1)).sum());
            for(var module:remaining)if((ticket-=rule.hostZone().weights().getOrDefault(module.id(),1))<0) {result.add(module);remaining.remove(module);break;}
        }
        return result;
    }
    private static boolean level(TerrainPreparationColumn c,int y) {
        return c!=null && c.targetElevation()==y && c.type()==TerrainPreparationColumnType.PLATFORM;
    }
    private static boolean walkable(GridPoint p,GridBounds host,Map<GridPoint,TerrainPreparationColumn> columns,Set<GridPoint> blocked,Set<GridPoint> footprint,int y) {
        var c=columns.get(p);
        return host!=null && host.contains(p) && !blocked.contains(p) && !footprint.contains(p) && c!=null && c.targetElevation()==y
                && (c.type()==TerrainPreparationColumnType.PLATFORM || c.type()==TerrainPreparationColumnType.BUILDING_ACCESS);
    }
    private static Optional<List<GridPoint>> route(GridPoint start,GridBounds host,Map<GridPoint,TerrainPreparationColumn> columns,
            Set<GridPoint> blocked,Set<GridPoint> footprint,Set<GridPoint> goals,int y,int length) {
        if(goals.isEmpty() || !walkable(start,host,columns,blocked,footprint,y))return Optional.empty();
        var queue=new ArrayDeque<GridPoint>();var previous=new HashMap<GridPoint,GridPoint>();var distances=new HashMap<GridPoint,Integer>();queue.add(start);distances.put(start,0);
        while(!queue.isEmpty() && distances.size()<=4096) {
            var p=queue.removeFirst();
            if(goals.contains(p)) {
                var path=new ArrayList<GridPoint>();for(var at=p;at!=null;at=previous.get(at))path.add(at);Collections.reverse(path);return Optional.of(List.copyOf(path));
            }
            if(distances.get(p)>=length)continue;
            for(var d:List.of(new GridPoint(0,-1),new GridPoint(1,0),new GridPoint(0,1),new GridPoint(-1,0))) {
                var next=new GridPoint(p.x()+d.x(),p.z()+d.z());
                if(!distances.containsKey(next) && walkable(next,host,columns,blocked,footprint,y)) {distances.put(next,distances.get(p)+1);previous.put(next,p);queue.addLast(next);}
            }
        }
        return Optional.empty();
    }
    private static Set<GridPoint> footprint(GridPoint origin,Vec size) {
        var points=new HashSet<GridPoint>();for(int z=0;z<size.z();z++)for(int x=0;x<size.x();x++)points.add(new GridPoint(origin.x()+x,origin.z()+z));return points;
    }
    private static void addRectangle(GridBounds bounds,Set<GridPoint> points) {
        for(int z=bounds.minZ();z<bounds.maxZExclusive();z++)for(int x=bounds.minX();x<bounds.maxXExclusive();x++)points.add(new GridPoint(x,z));
    }
}
