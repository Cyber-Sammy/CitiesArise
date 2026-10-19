package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import java.util.*;

/** Author-owned floor footprint and mandatory connection constraints; no block registry dependency. */
public record FloorLayout(Vec size,List<GridPoint> footprint,List<String> rooms,List<String> fillers,
        List<Integer> rotations,List<Port> ports,String entrance,Map<String,Integer> minimumCounts,Map<String,Integer> maximumCounts,
        Mode mode,List<Placement> placements,List<Terrace> terraces) {
    public enum Mode { AUTOMATIC, FIXED, MIXED }
    public record Placement(String module,Vec at,int rotation,String layout) {
        public Placement {
            Objects.requireNonNull(module);Objects.requireNonNull(at);layout=layout==null?"":layout;
            if(module.isBlank() || at.x()<0 || at.x()>31 || at.y()!=0 || at.z()<0 || at.z()>31 || rotation<0 || rotation>3)
                throw new IllegalArgumentException("Invalid authored room placement");
        }
    }
    /** The rectangle includes its outer guard row; access is a standing cell at deckY + 1. */
    public record Terrace(String id,GridPoint minimum,GridPoint maximum,int deckY,Vec access) {
        public Terrace {
            Objects.requireNonNull(id);Objects.requireNonNull(minimum);Objects.requireNonNull(maximum);Objects.requireNonNull(access);
            if(id.isBlank() || minimum.x()<0 || minimum.z()<0 || maximum.x()<minimum.x() || maximum.z()<minimum.z() || deckY<0 || deckY>13
                    || access.y()!=deckY+1 || access.x()<minimum.x() || access.x()>maximum.x() || access.z()<minimum.z() || access.z()>maximum.z())
                throw new IllegalArgumentException("Invalid terrace zone");
        }
    }
    public FloorLayout(Vec size,List<GridPoint> footprint,List<String> rooms,List<String> fillers,List<Integer> rotations,
            List<Port> ports,String entrance,Map<String,Integer> minimumCounts,Map<String,Integer> maximumCounts) {
        this(size,footprint,rooms,fillers,rotations,ports,entrance,minimumCounts,maximumCounts,Mode.AUTOMATIC,List.of(),List.of());
    }
    public record Port(String id,String type,Vec at,Face face,int width,int height,boolean walkable) {
        public Port {Objects.requireNonNull(id);Objects.requireNonNull(type);Objects.requireNonNull(at);Objects.requireNonNull(face);
            if(id.isBlank() || type.isBlank() || width<1 || width>16 || height<1 || height>16)throw new IllegalArgumentException("Invalid floor port");}
        Joint joint(){return new Joint(id,type,Set.of(type),at,face,width,height,false,true,"");}
    }
    public FloorLayout {
        Objects.requireNonNull(size);Objects.requireNonNull(entrance);
        footprint=List.copyOf(footprint);rooms=List.copyOf(rooms);fillers=List.copyOf(fillers);rotations=List.copyOf(rotations);ports=List.copyOf(ports);
        minimumCounts=minimumCounts==null?Map.of():Map.copyOf(minimumCounts);maximumCounts=maximumCounts==null?Map.of():Map.copyOf(maximumCounts);
        mode=mode==null?Mode.AUTOMATIC:mode;placements=placements==null?List.of():List.copyOf(placements);terraces=terraces==null?List.of():List.copyOf(terraces);
        if(placements.size()>16 || terraces.size()>8 || mode==Mode.AUTOMATIC && !placements.isEmpty() || mode!=Mode.AUTOMATIC && placements.isEmpty()
                || new HashSet<>(placements.stream().map(Placement::at).toList()).size()!=placements.size()
                || terraces.stream().map(Terrace::id).distinct().count()!=terraces.size())throw new IllegalArgumentException("Invalid authored floor layout");
        if(size.x()<1 || size.x()>32 || size.z()<1 || size.z()>32 || size.y()<3 || size.y()>16
                || footprint.isEmpty() || footprint.size()>1024 || new HashSet<>(footprint).size()!=footprint.size()
                || rooms.isEmpty() || rooms.size()+fillers.size()>128 || ports.isEmpty() || ports.size()>32
                || rotations.isEmpty() || rotations.size()>4 || new HashSet<>(rotations).size()!=rotations.size() || rotations.stream().anyMatch(r->r<0 || r>3))
            throw new IllegalArgumentException("Invalid floor layout budget");
        var mask=new HashSet<>(footprint);
        for(var p:mask)if(p.x()<0 || p.z()<0 || p.x()>=size.x() || p.z()>=size.z())throw new IllegalArgumentException("Floor footprint outside envelope");
        var seen=new HashSet<GridPoint>();var queue=new ArrayDeque<GridPoint>();queue.add(footprint.getFirst());seen.add(footprint.getFirst());
        while(!queue.isEmpty()) {var p=queue.remove();for(var d:List.of(new GridPoint(1,0),new GridPoint(-1,0),new GridPoint(0,1),new GridPoint(0,-1))) {
            var q=new GridPoint(p.x()+d.x(),p.z()+d.z());if(mask.contains(q) && seen.add(q))queue.add(q);
        }}
        if(seen.size()!=mask.size())throw new IllegalArgumentException("Disconnected floor footprint");
        var ids=new HashSet<String>();
        for(var port:ports) {
            if(!ids.add(port.id()))throw new IllegalArgumentException("Duplicate floor port");
            for(var p:ModuleDefinition.jointCells(port.joint())) {
                if(!ModuleDefinition.inside(p,size) || !mask.contains(new GridPoint(p.x(),p.z())))throw new IllegalArgumentException("Floor port outside footprint");
                var n=ModuleBoundary.normal(port.face());
                if(port.face().ordinal()<4 && mask.contains(new GridPoint(p.x()+n.x(),p.z()+n.z())))throw new IllegalArgumentException("Floor facade port is not exterior");
                if(port.face()==Face.UP && port.at().y()!=size.y() || port.face()==Face.DOWN && port.at().y()!=0)throw new IllegalArgumentException("Vertical port must be on floor boundary");
            }
        }
        var entry=ports.stream().filter(p->p.id().equals(entrance)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown floor entrance"));
        if(entry.face()==Face.UP || !entry.walkable())throw new IllegalArgumentException("Floor access must be walkable and horizontal or downward");
        if(new HashSet<>(rooms).size()!=rooms.size() || new HashSet<>(fillers).size()!=fillers.size() || !Collections.disjoint(rooms,fillers))throw new IllegalArgumentException("Duplicate floor room or filler identity");
        var pool=new HashSet<>(rooms);pool.addAll(fillers);
        for(var p:placements)if(!pool.contains(p.module()) || !rotations.contains(p.rotation()))throw new IllegalArgumentException("Authored room outside floor pool or rotations");
        var terraceCells=new HashSet<GridPoint>();
        for(var t:terraces) {
            if(t.maximum().x()>=size.x() || t.maximum().z()>=size.z() || t.deckY()+2>=size.y())throw new IllegalArgumentException("Terrace outside floor envelope");
            for(int x=t.minimum().x();x<=t.maximum().x();x++)for(int z=t.minimum().z();z<=t.maximum().z();z++) {
                var p=new GridPoint(x,z);if(!mask.contains(p) || !terraceCells.add(p))throw new IllegalArgumentException("Terrace outside floor mask or overlaps another terrace");
            }
        }
        for(var counts:List.of(minimumCounts,maximumCounts))for(var e:counts.entrySet())if(!pool.contains(e.getKey()) || e.getValue()<0 || e.getValue()>16)throw new IllegalArgumentException("Invalid floor room count");
        for(var e:minimumCounts.entrySet())if(e.getValue()>maximumCounts.getOrDefault(e.getKey(),16))throw new IllegalArgumentException("Conflicting floor room counts");
    }
    void validateModules(Map<String,ModuleDefinition> modules,List<String> roots,List<String> attachments) {
        var pool=new HashSet<>(rooms);pool.addAll(fillers);
        for(var id:pool)if(!modules.containsKey(id))throw new IllegalArgumentException("Unknown floor room: "+id);
        if(!pool.containsAll(roots) || !pool.containsAll(attachments))throw new IllegalArgumentException("Composition pool outside floor room pool");
        for(var id:pool)if(modules.get(id).size().y()!=size.y())throw new IllegalArgumentException("Room height differs from floor height");
        for(var p:placements) {
            var m=modules.get(p.module());var extent=CompositionAssembler.rotatedSize(m.size(),p.rotation());
            if(!p.layout().isEmpty() && !p.layout().equals("base") && m.layouts().stream().noneMatch(l->l.id().equals(p.layout())))throw new IllegalArgumentException("Unknown authored room layout");
            for(int x=p.at().x();x<p.at().x()+extent.x();x++)for(int z=p.at().z();z<p.at().z()+extent.z();z++)
                if(!footprint.contains(new GridPoint(x,z)))throw new IllegalArgumentException("Authored room outside floor footprint");
            if(placements.stream().filter(q->q.module().equals(p.module())).count()>maximumCounts.getOrDefault(p.module(),16))throw new IllegalArgumentException("Authored room count exceeds maximum");
        }
    }
    /** A downward access anchor denotes internal arrival from the floor below. */
    boolean acceptsRootAccess(Joint joint) {
        var access=ports.stream().filter(p->p.id().equals(entrance)).findFirst().orElseThrow();
        return joint.face()==access.face() && joint.type().equals(access.type())
                && joint.width()==access.width() && joint.height()==access.height()
                && (access.face()==Face.DOWN ? !joint.external() : joint.external());
    }
}
