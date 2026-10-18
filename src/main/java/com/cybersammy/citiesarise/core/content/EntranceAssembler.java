package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.content.ResolvedComposition.*;
import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;

/** Attaches independent modules to declared ground-level facade interfaces after floor assembly. */
public final class EntranceAssembler {
    public record Result(ResolvedComposition building,List<ResolvedEntrance> entrances) { }
    private record Socket(String id,Joint joint) { }
    private EntranceAssembler() { }

    public static Result assemble(EntranceSettings settings,CompositionSettings buildingSettings,ResolvedComposition building,
            BuildingSlot slot,Parcel parcel,SettlementPlan city,TerrainPreparationPlan preparation,Vec primary,Face primaryFace,long seed) {
        var base=new LinkedHashMap<Vec,PlacedCell>();building.cells().forEach(c->base.put(c.position(),c));
        var occupied=new HashSet<GridPoint>();var protectedPaths=new HashSet<GridPoint>();var placed=new ArrayList<ResolvedEntrance>();var used=new HashSet<Joint>();
        for(var other:city.buildingSlots())if(!other.id().equals(slot.id()) && other.bounds().intersects(parcel.bounds()))
            for(int z=other.bounds().minZ();z<other.bounds().maxZExclusive();z++)for(int x=other.bounds().minX();x<other.bounds().maxXExclusive();x++)
                occupied.add(new GridPoint(x,z));
        var columns=new HashMap<GridPoint,TerrainPreparationColumn>();preparation.columns().forEach(c->columns.put(c.point(),c));
        int platform=preparation.elevationPlan().zones().stream().filter(z->z.sourceElementId().equals(slot.id())).findFirst().orElseThrow().targetElevation();
        var sockets=sockets(building,buildingSettings.modules(),primary.y());
        var inside=reachable(base,primary.add(inward(primaryFace)),buildingSettings.materialRules(),buildingSettings.airMaterial());
        var merged=new LinkedHashMap<>(base);
        var caps=new ArrayList<>(building.cappedJoints());
        var requests=settings.requests().stream().sorted(Comparator.comparing((EntranceSettings.Request r)->!r.socket().equals("primary")).thenComparing(EntranceSettings.Request::id)).toList();
        int attempts=0;
        for(var request:requests) {
            var failures=new TreeMap<String,Integer>();
            var matches=sockets.stream().filter(s->!used.contains(s.joint()) && (request.socket().equals("primary")
                    ?s.joint().position().equals(primary) && s.joint().face()==primaryFace:s.id().equals(request.socket()))).toList();
            ResolvedEntrance accepted=null;Joint acceptedSocket=null;
            for(var socket:matches) {
                if(!ModuleDefinition.jointCells(socket.joint()).stream().filter(p->p.y()==primary.y())
                        .allMatch(p->inside.contains(p.add(inward(socket.joint().face()))))) {
                    failures.merge("interior route",1,Integer::sum);continue;
                }
                for(var choice:order(request.pool(),seed^request.id().hashCode())) {
                    var module=settings.modules().get(choice.module());
                    for(var raw:CompositionAssembler.standaloneAlternatives(module,request.connectionJoint(),settings.damageSeverity(),settings.airMaterial(),seed^request.id().hashCode())) {
                        var nested=new NestedCompositionResolver(settings.modules(),settings.materialRules(),m->true,settings.airMaterial(),settings.damageSeverity(),seed^request.id().hashCode()).resolve(raw);
                        if(nested.isEmpty() || nested.orElseThrow().cells().size()>16384)continue;
                        var pose=raw.modules().getFirst();
                        var back=module.jointsFor(pose.variant(),pose.layout()).stream().filter(j->j.id().equals(request.connectionJoint())).findFirst().orElseThrow();
                        var outside=module.jointsFor(pose.variant(),pose.layout()).stream().filter(j->j.id().equals(request.outsideJoint())).findFirst();
                        if(outside.isEmpty() || !outside.orElseThrow().external() || outside.orElseThrow().height()<2
                                || outside.orElseThrow().face()!=back.face().opposite())continue;
                        var passage=new ModuleContract.Route("entrance",ModuleDefinition.jointCells(back).getFirst(),ModuleDefinition.jointCells(outside.orElseThrow()).getFirst(),2);
                        if(!CompositionGeometryValidator.connects(nested.orElseThrow(),module,pose,passage,settings.materialRules(),settings.airMaterial()))continue;
                        for(int turn=0;turn<4;turn++) {
                            if(++attempts>512)throw new IllegalArgumentException("Entrance search budget exceeded");
                            var joint=CompositionAssembler.rotate(back,module.size(),turn);
                            if(!socket.joint().matches(joint))continue;
                            // Share exactly one facade layer, rather than placing two walls beside each other.
                            var origin=socket.joint().position().add(inward(socket.joint().face())).subtract(joint.position());
                            var transformed=translate(ParcelPropLayout.rotate(nested.orElseThrow(),module.size(),turn,settings.modules()),origin);
                            var entry=translate(CompositionAssembler.rotate(outside.orElseThrow(),module.size(),turn),origin);
                            var protectedSpace=new HashSet<>(occupied);protectedSpace.addAll(protectedPaths);
                            String fitFailure=fitFailure(transformed,socket.joint(),merged,slot,parcel,columns,protectedSpace,platform,settings);
                            if(entry.position().y()!=primary.y() || fitFailure!=null) {
                                failures.merge(fitFailure==null?"entrance elevation":fitFailure,1,Integer::sum);continue;
                            }
                            var combined=new LinkedHashMap<>(merged);transformed.cells().forEach(c->combined.put(c.position(),c));
                            var body=ModuleDefinition.jointCells(entry).stream().filter(p->p.y()==primary.y()).toList();
                            if(body.stream().anyMatch(p->!standing(combined,p,settings.materialRules(),settings.airMaterial())))continue;
                            var outsidePoint=body.getFirst().subtract(inward(entry.face()));
                            var anchor=world(slot,outsidePoint);var reservations=footprint(slot,transformed);
                            var blocked=new HashSet<>(occupied);blocked.addAll(reservations);blocked.addAll(footprint(slot,building));
                            var route=EntranceYardRouter.route(anchor,world(slot,body.getFirst()),parcel,slot,city,preparation,platform,blocked,request.maximumPathLength(),request.pathWidth());
                            if(route.isEmpty()){failures.merge("yard route",1,Integer::sum);continue;}
                            var path=new LinkedHashSet<GridPoint>();
                            for(var p:route.orElseThrow())for(int dz=-request.pathWidth()/2;dz<=request.pathWidth()/2;dz++)for(int dx=-request.pathWidth()/2;dx<=request.pathWidth()/2;dx++)
                                path.add(new GridPoint(p.x()+dx,p.z()+dz));
                            reservations.addAll(path);
                            accepted=new ResolvedEntrance(request.id(),request.role(),request.socket(),transformed,anchor,platform,
                                    path.stream().map(p->new ResolvedEntrance.PathCell(p,columns.get(p).targetElevation(),request.pathMaterial())).toList(),reservations);
                            acceptedSocket=socket.joint();break;
                        }
                        if(accepted!=null)break;
                    }
                    if(accepted!=null)break;
                }
                if(accepted!=null)break;
            }
            if(accepted==null) {
                if(request.required())throw new IllegalArgumentException("No compatible reachable entrance "+request.id()+" for "+slot.id().value()
                        +"; sockets="+matches.size()+", rejected="+failures);
                for(var s:matches)for(var p:ModuleDefinition.jointCells(s.joint())) {
                    var old=base.get(p);
                    if(old!=null && !old.providerData().isEmpty())throw new IllegalArgumentException("Cannot cap occupied optional facade socket");
                    var cap=new PlacedCell(p,request.capMaterial(),0);base.put(p,cap);merged.put(p,cap);
                }
                caps.add("entrance:"+request.id()+":unavailable");
                continue;
            }
            used.add(acceptedSocket);placed.add(accepted);occupied.addAll(footprint(slot,accepted.composition()));
            accepted.path().forEach(p->protectedPaths.add(p.point()));
            accepted.composition().cells().forEach(c->merged.put(c.position(),c));
        }
        var resolved=new ResolvedComposition(building.modules(),List.copyOf(base.values()),building.connections(),caps,building.damageDecisions());
        var check=new ResolvedComposition(building.modules(),List.copyOf(merged.values()),building.connections(),building.cappedJoints(),building.damageDecisions());
        var error=CompositionGeometryValidator.failure(check,buildingSettings.modules(),buildingSettings.materialRules(),buildingSettings.airMaterial());
        if(error.isPresent())throw new IllegalArgumentException("Entrance blocks building circulation: "+error.orElseThrow());
        return new Result(resolved,List.copyOf(placed));
    }
    private static String fitFailure(ResolvedComposition module,Joint socket,Map<Vec,PlacedCell> cells,BuildingSlot slot,Parcel parcel,
            Map<GridPoint,TerrainPreparationColumn> columns,Set<GridPoint> occupied,int platform,EntranceSettings settings) {
        int plane=switch(socket.face()){case NORTH,SOUTH->ModuleDefinition.jointCells(socket).getFirst().z();default->ModuleDefinition.jointCells(socket).getFirst().x();};
        for(var c:module.cells()) {
            var p=c.position();var point=world(slot,p);var column=columns.get(point);
            if(!parcel.bounds().contains(point) || column==null || column.targetElevation()!=platform || occupied.contains(point)
                    || p.y()<0 || p.y()>15)return "prepared surface";
            var old=cells.get(p);
            if(column.type()!=TerrainPreparationColumnType.PLATFORM && column.type()!=TerrainPreparationColumnType.BUILDING_ACCESS
                    && !(column.type()==TerrainPreparationColumnType.BUILDING_ACCESS_STEP && old!=null))return "surface type "+column.type();
            if(old==null)continue;
            int value=socket.face()==Face.NORTH || socket.face()==Face.SOUTH?p.z():p.x();
            if(value!=plane || p.y()<socket.position().y()-1 || p.y()>socket.position().y()+socket.height()
                    || !old.providerData().isEmpty() || settings.materialRules().climbable().contains(old.material()))return "building overlap";
        }
        return null;
    }
    private static List<Socket> sockets(ResolvedComposition building,Map<String,ModuleDefinition> definitions,int y) {
        var result=new ArrayList<Socket>();
        for(var placed:building.modules()) {
            var module=definitions.get(placed.id());
            for(var j:module.jointsFor(placed.variant(),placed.layout()))if(j.external() && j.face().ordinal()<4 && j.height()>=2) {
                var world=translate(CompositionAssembler.rotate(j,module.size(),placed.rotation()),placed.origin());
                if(world.position().y()==y)result.add(new Socket(j.id(),world));
            }
        }
        result.sort(Comparator.comparingInt((Socket s)->s.joint().position().z()).thenComparingInt(s->s.joint().position().x()).thenComparing(Socket::id));
        return result;
    }
    private static Set<Vec> reachable(Map<Vec,PlacedCell> cells,Vec start,MaterialRules rules,String air) {
        var seen=new HashSet<Vec>();var queue=new ArrayDeque<Vec>();
        if(standing(cells,start,rules,air)){seen.add(start);queue.add(start);}
        while(!queue.isEmpty() && seen.size()<=4096) {
            var p=queue.removeFirst();for(var d:List.of(new Vec(0,0,-1),new Vec(1,0,0),new Vec(0,0,1),new Vec(-1,0,0))) {
                var next=p.add(d);if(!seen.contains(next) && standing(cells,next,rules,air)){seen.add(next);queue.addLast(next);}
            }
        }
        return seen;
    }
    private static boolean standing(Map<Vec,PlacedCell> cells,Vec p,MaterialRules rules,String air) {
        for(int y=0;y<2;y++){var c=cells.get(p.add(new Vec(0,y,0)));if(c==null || !rules.isPassable(c.material(),air))return false;}
        var floor=cells.get(p.add(new Vec(0,-1,0)));return floor!=null && rules.supportive().contains(floor.material());
    }
    private static Vec inward(Face face) {return switch(face){case NORTH->new Vec(0,0,1);case SOUTH->new Vec(0,0,-1);case WEST->new Vec(1,0,0);case EAST->new Vec(-1,0,0);default->throw new IllegalArgumentException("Horizontal facade required");};}
    private static GridPoint world(BuildingSlot slot,Vec p) {return new GridPoint(slot.bounds().minX()+p.x(),slot.bounds().minZ()+p.z());}
    private static HashSet<GridPoint> footprint(BuildingSlot slot,ResolvedComposition composition) {
        var points=new HashSet<GridPoint>();composition.cells().forEach(c->points.add(world(slot,c.position())));return points;
    }
    private static Joint translate(Joint j,Vec at) {return new Joint(j.id(),j.type(),j.accepts(),j.position().add(at),j.face(),j.width(),j.height(),j.optional(),j.external(),j.capMaterial());}
    private static ResolvedComposition translate(ResolvedComposition c,Vec at) {
        return new ResolvedComposition(c.modules().stream().map(m->new PlacedModule(m.id(),m.variant(),m.origin().add(at),m.rotation(),m.layout())).toList(),
                c.cells().stream().map(p->new PlacedCell(p.position().add(at),p.material(),p.rotation(),p.providerData())).toList(),c.connections(),c.cappedJoints(),c.damageDecisions());
    }
    private static List<EntranceSettings.Choice> order(List<EntranceSettings.Choice> pool,long seed) {
        var remaining=new ArrayList<>(pool.stream().sorted(Comparator.comparing(EntranceSettings.Choice::module)).toList());
        var result=new ArrayList<EntranceSettings.Choice>();var random=new Random(seed);
        while(!remaining.isEmpty()) {
            int ticket=random.nextInt(remaining.stream().mapToInt(EntranceSettings.Choice::weight).sum());
            for(var c:remaining)if((ticket-=c.weight())<0){result.add(c);remaining.remove(c);break;}
        }
        return result;
    }
}
