package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import com.cybersammy.citiesarise.core.content.ResolvedComposition.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.*;

/** One anchored, rotated floor mask for a bounded composition search. */
final class FloorLayoutFrame {
    final FloorLayout layout;final Vec origin;final int rotation;final Set<GridPoint> mask;final List<FloorLayout.Port> ports;
    FloorLayoutFrame(FloorLayout layout,Vec entrance,Face face) {
        this.layout=layout;var entry=layout.ports().stream().filter(p->p.id().equals(layout.entrance())).findFirst().orElseThrow();
        int turn=0;while(entry.face().rotate(turn)!=face && turn<4)turn++;
        if(turn==4)throw new IllegalArgumentException("Invalid floor entrance orientation");rotation=turn;
        var rotated=CompositionAssembler.rotate(entry.joint(),layout.size(),turn);origin=entrance.subtract(rotated.position());
        var positions=new HashSet<GridPoint>();
        for(var p:layout.footprint()) {var q=CompositionAssembler.rotateCell(new Vec(p.x(),0,p.z()),layout.size(),turn).add(origin);positions.add(new GridPoint(q.x(),q.z()));}
        mask=Collections.unmodifiableSet(positions);var transformed=new ArrayList<FloorLayout.Port>();
        for(var p:layout.ports()) {var q=CompositionAssembler.translate(CompositionAssembler.rotate(p.joint(),layout.size(),turn),origin);
            transformed.add(new FloorLayout.Port(p.id(),p.type(),q.position(),q.face(),q.width(),q.height(),p.walkable()));}
        ports=List.copyOf(transformed);
    }
    boolean fitsSlot(int width,int depth,int maximumHeight) {return origin.y()>=0 && origin.y()+layout.size().y()<=maximumHeight
            && mask.stream().allMatch(p->p.x()>=0 && p.z()>=0 && p.x()<width && p.z()<depth);}
    boolean permits(ModuleDefinition module,Vec at,int turn,List<PlacedModule> placed) {
        if(!layout.rotations().contains(Math.floorMod(turn-rotation,4)) || at.y()!=origin.y()
                || placed.stream().filter(m->m.id().equals(module.id())).count()>=layout.maximumCounts().getOrDefault(module.id(),16))return false;
        var size=CompositionAssembler.rotatedSize(module.size(),turn);
        for(int x=at.x();x<at.x()+size.x();x++)for(int z=at.z();z<at.z()+size.z();z++)if(!mask.contains(new GridPoint(x,z)))return false;
        return true;
    }
    boolean preservesFixedPorts(ModuleDefinition module,List<Joint> joints,Vec at,int turn) {
        var size=CompositionAssembler.rotatedSize(module.size(),turn);
        for(var port:ports) {
            boolean occupies=ModuleDefinition.jointCells(port.joint()).stream().anyMatch(p->ModuleDefinition.inside(p.subtract(at),size));
            if(occupies && joints.stream().map(j->CompositionAssembler.translate(CompositionAssembler.rotate(j,module.size(),turn),at))
                    .noneMatch(j->j.type().equals(port.type()) && j.position().equals(port.at()) && j.face()==port.face() && j.width()==port.width() && j.height()==port.height()))return false;
        }
        return true;
    }
    boolean terminal(Joint joint) {return ports.stream().anyMatch(p->p.type().equals(joint.type()) && p.at().equals(joint.position())
            && p.face()==joint.face() && p.width()==joint.width() && p.height()==joint.height());}
    boolean facade(Joint joint) {
        if(joint.face().ordinal()>=4)return terminal(joint);
        var normal=ModuleBoundary.normal(joint.face());
        return ModuleDefinition.jointCells(joint).stream().allMatch(p->!mask.contains(new GridPoint(p.x()+normal.x(),p.z()+normal.z())));
    }
    Optional<String> coverage(List<PlacedModule> modules,Map<String,ModuleDefinition> definitions) {
        var covered=new HashSet<GridPoint>();
        for(var m:modules) {var size=CompositionAssembler.rotatedSize(definitions.get(m.id()).size(),m.rotation());
            for(int x=m.origin().x();x<m.origin().x()+size.x();x++)for(int z=m.origin().z();z<m.origin().z()+size.z();z++)covered.add(new GridPoint(x,z));}
        if(!covered.equals(mask))return Optional.of("Floor footprint not completely packed");
        for(var e:layout.minimumCounts().entrySet())if(modules.stream().filter(m->m.id().equals(e.getKey())).count()<e.getValue())return Optional.of("Missing required floor room: "+e.getKey());
        return Optional.empty();
    }
    Optional<String> validate(ResolvedComposition result,CompositionSettings settings,int structuralCount) {
        return validate(result,settings,structuralCount,Set.of());
    }
    Optional<String> validate(ResolvedComposition result,CompositionSettings settings,int structuralCount,Set<String> closedPorts) {
        var cells=new HashMap<Vec,PlacedCell>();result.cells().forEach(c->cells.put(c.position(),c));
        for(var p:cells.keySet())if(!mask.contains(new GridPoint(p.x(),p.z())) || p.y()<origin.y() || p.y()>=origin.y()+layout.size().y())return Optional.of("Floor content outside footprint");
        var actualPorts=new ArrayList<Joint>();var targets=new ArrayList<Vec>();
        for(int roomIndex=0;roomIndex<structuralCount;roomIndex++) {
            var m=result.modules().get(roomIndex);var module=settings.modules().get(m.id());
            String owner=module.id()+"#"+roomIndex;
            var joints=new ArrayList<>(module.jointsFor(m.variant(),m.layout()));
            joints.removeIf(j->result.damageDecisions().contains(owner+":closed:"+j.id()));
            module.damage().optionalJoints().stream().filter(j->result.damageDecisions().contains(owner+":opened:"+j.id())).forEach(joints::add);
            var walkPoints=new ArrayList<Vec>();
            for(var j:joints) {
                if(module.boundariesFor(m.layout()).stream().anyMatch(b->b.joint().equals(j.id()) && !b.passage())) continue;
                var q=CompositionAssembler.translate(CompositionAssembler.rotate(j,module.size(),m.rotation()),m.origin());actualPorts.add(q);
                if(q.external() && !facade(q))return Optional.of("Exterior floor opening faces another room");
                boolean declaredWalk=ports.stream().anyMatch(p->p.walkable() && p.type().equals(q.type()) && p.at().equals(q.position()) && p.face()==q.face());
                boolean declaredNonWalk=ports.stream().anyMatch(p->!p.walkable() && p.type().equals(q.type()) && p.at().equals(q.position()) && p.face()==q.face());
                if(q.face().ordinal()>=4 && declaredWalk && ports.stream().noneMatch(p->closedPorts.contains(p.id()) && p.at().equals(q.position()) && p.face()==q.face())) {
                    var p=ModuleDefinition.jointCells(q).getFirst();
                    walkPoints.add(q.face()==Face.UP?p.add(new Vec(0,-1,0)):p);
                }
                if(q.face().ordinal()<4 && q.height()>=2 && (!q.external() || declaredWalk) && !declaredNonWalk) {
                    var p=ModuleDefinition.jointCells(q).getFirst();
                    // A capped optional opening is not a circulation requirement.
                    String cap=owner+"/"+q.id();
                    if(!result.cappedJoints().contains(cap)) {
                        walkPoints.add(p);
                        if(!layout.fillers().contains(m.id())) {
                            var n=ModuleBoundary.normal(q.face());walkPoints.add(p.subtract(n));
                        }
                    }
                }
            }
            if(!layout.fillers().contains(m.id())) {
                if(walkPoints.isEmpty())return Optional.of("Room has no circulation port");
            }
            targets.addAll(walkPoints);
        }
        for(var port:ports) {
            if(actualPorts.stream().noneMatch(j->j.type().equals(port.type()) && j.position().equals(port.at()) && j.face()==port.face()
                    && j.width()==port.width() && j.height()==port.height()))return Optional.of("Missing fixed floor port: "+port.id());
            if(port.walkable() && !closedPorts.contains(port.id())) {
                for(var p:ModuleDefinition.jointCells(port.joint())) {
                    if(!passable(cells,p,settings))return Optional.of("Blocked fixed floor port: "+port.id());
                }
                var p=ModuleDefinition.jointCells(port.joint()).getFirst();targets.add(port.face()==Face.UP?p.add(new Vec(0,-1,0)):p);
            }
        }
        var entry=ports.stream().filter(p->p.id().equals(layout.entrance())).findFirst().orElseThrow();var start=ModuleDefinition.jointCells(entry.joint()).getFirst();
        var reachable=new HashSet<Vec>();var queue=new ArrayDeque<Vec>();
        if(!standing(cells,start,settings))return Optional.of("Floor entrance is not traversable");reachable.add(start);queue.add(start);
        while(!queue.isEmpty() && reachable.size()<=16384) {
            var p=queue.remove();
            for(var d:List.of(new Vec(1,0,0),new Vec(-1,0,0),new Vec(0,0,1),new Vec(0,0,-1)))for(int dy=-1;dy<=1;dy++) {
                var q=p.add(d).add(new Vec(0,dy,0));
                if(dy>0 && !passable(cells,p.add(new Vec(0,2,0)),settings) || dy<0 && !passable(cells,q.add(new Vec(0,2,0)),settings))continue;
                enqueue(cells,q,settings,reachable,queue);
            }
            for(int dy:new int[]{-1,1}) {var q=p.add(new Vec(0,dy,0));if(climbable(cells,p,settings) && climbable(cells,q,settings))enqueue(cells,q,settings,reachable,queue);}
        }
        if(reachable.size()>16384)return Optional.of("Floor circulation budget exceeded");
        if(!reachable.containsAll(targets))return Optional.of("Floor room or connector disconnected from entrance");
        return Optional.empty();
    }
    private void enqueue(Map<Vec,PlacedCell> cells,Vec p,CompositionSettings cfg,Set<Vec> seen,ArrayDeque<Vec> queue) {
        if(p.y()>=origin.y() && p.y()<origin.y()+layout.size().y() && mask.contains(new GridPoint(p.x(),p.z())) && !seen.contains(p) && standing(cells,p,cfg)) {seen.add(p);queue.add(p);}
    }
    private static boolean passable(Map<Vec,PlacedCell> cells,Vec p,CompositionSettings cfg) {var c=cells.get(p);return c!=null && cfg.materialRules().isPassable(c.material(),cfg.airMaterial());}
    private static boolean climbable(Map<Vec,PlacedCell> cells,Vec p,CompositionSettings cfg) {var c=cells.get(p);return c!=null && cfg.materialRules().climbable().contains(c.material());}
    private static boolean standing(Map<Vec,PlacedCell> cells,Vec p,CompositionSettings cfg) {
        var support=cells.get(p.add(new Vec(0,-1,0)));
        return passable(cells,p,cfg) && passable(cells,p.add(new Vec(0,1,0)),cfg)
                && (climbable(cells,p,cfg) || support!=null && cfg.materialRules().supportive().contains(support.material()));
    }
}
