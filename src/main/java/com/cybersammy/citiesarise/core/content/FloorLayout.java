package com.cybersammy.citiesarise.core.content;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.content.ModuleDefinition.*;
import java.util.*;

/** Author-owned floor footprint and mandatory connection constraints; no block registry dependency. */
public record FloorLayout(Vec size,List<GridPoint> footprint,List<String> rooms,List<String> fillers,
        List<Integer> rotations,List<Port> ports,String entrance,Map<String,Integer> minimumCounts,Map<String,Integer> maximumCounts) {
    public record Port(String id,String type,Vec at,Face face,int width,int height,boolean walkable) {
        public Port {Objects.requireNonNull(id);Objects.requireNonNull(type);Objects.requireNonNull(at);Objects.requireNonNull(face);
            if(id.isBlank() || type.isBlank() || width<1 || width>16 || height<1 || height>16)throw new IllegalArgumentException("Invalid floor port");}
        Joint joint(){return new Joint(id,type,Set.of(type),at,face,width,height,false,true,"");}
    }
    public FloorLayout {
        Objects.requireNonNull(size);Objects.requireNonNull(entrance);
        footprint=List.copyOf(footprint);rooms=List.copyOf(rooms);fillers=List.copyOf(fillers);rotations=List.copyOf(rotations);ports=List.copyOf(ports);
        minimumCounts=minimumCounts==null?Map.of():Map.copyOf(minimumCounts);maximumCounts=maximumCounts==null?Map.of():Map.copyOf(maximumCounts);
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
        for(var counts:List.of(minimumCounts,maximumCounts))for(var e:counts.entrySet())if(!pool.contains(e.getKey()) || e.getValue()<0 || e.getValue()>16)throw new IllegalArgumentException("Invalid floor room count");
        for(var e:minimumCounts.entrySet())if(e.getValue()>maximumCounts.getOrDefault(e.getKey(),16))throw new IllegalArgumentException("Conflicting floor room counts");
    }
    void validateModules(Map<String,ModuleDefinition> modules,List<String> roots,List<String> attachments) {
        var pool=new HashSet<>(rooms);pool.addAll(fillers);
        for(var id:pool)if(!modules.containsKey(id))throw new IllegalArgumentException("Unknown floor room: "+id);
        if(!pool.containsAll(roots) || !pool.containsAll(attachments))throw new IllegalArgumentException("Composition pool outside floor room pool");
        for(var id:pool)if(modules.get(id).size().y()!=size.y())throw new IllegalArgumentException("Room height differs from floor height");
    }
    /** A downward access anchor denotes internal arrival from the floor below. */
    boolean acceptsRootAccess(Joint joint) {
        var access=ports.stream().filter(p->p.id().equals(entrance)).findFirst().orElseThrow();
        return joint.face()==access.face() && joint.type().equals(access.type())
                && joint.width()==access.width() && joint.height()==access.height()
                && (access.face()==Face.DOWN ? !joint.external() : joint.external());
    }
}
