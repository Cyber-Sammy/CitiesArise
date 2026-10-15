package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.parcel.ParcelBoundaryPlan;
import java.util.*;

/** Freeze neighbor states from the complete boundary plan, so chunk order cannot change joins. */
final class ParcelBoundaryPlacement {
    private ParcelBoundaryPlacement() { }
    static DebugPlacementPlan add(DebugPlacementPlan original,ParcelBoundaryPlan boundaries) {
        if(boundaries.fences().isEmpty() && boundaries.alleys().isEmpty()) return original;
        Map<String,DebugBlockPlacementOperation> result=new LinkedHashMap<>();
        original.operations().forEach(op -> put(result,op));
        Set<String> fencePoints=new HashSet<>(); boundaries.fences().forEach(c -> fencePoints.add(key(c.point().x(),c.y(),c.point().z())));
        var edges=new HashMap<String,ParcelBoundaryPlan.Edge>();boundaries.edges().forEach(e -> edges.put(e.id(),e));
        for(var c:boundaries.fences()) {
            var op=operation(c,c.y(),DebugPlacementRole.PARCEL_FENCE,c.material());
            var data=new HashMap<>(op.providerData());data.put("cities_arise:fence_connections",Integer.toString(connections(c,fencePoints)));
            var edge=edges.get(c.edgeId());
            if(edge!=null) for(int i=0;i<edge.owners().size();i++) data.put("cities_arise:boundary_owner_"+i,edge.owners().get(i).value());
            put(result,new DebugBlockPlacementOperation(op.point(),op.verticalOffset(),op.role(),op.sourceElementId(),op.platformY(),op.material(),0,"",data));
        }
        for(var c:boundaries.alleys()) {
            put(result,operation(c,c.y(),DebugPlacementRole.PARCEL_ALLEY,c.material()));
            for(int dy=1;dy<=2;dy++) put(result,operation(c,c.y()+dy,DebugPlacementRole.PARCEL_ALLEY_CLEARANCE,"minecraft:air"));
        }
        return new DebugPlacementPlan(List.copyOf(result.values()));
    }
    private static DebugBlockPlacementOperation operation(ParcelBoundaryPlan.Cell c,int y,DebugPlacementRole role,String material) {
        return new DebugBlockPlacementOperation(c.point(),y-c.platformY(),role,c.source(),OptionalInt.of(c.platformY()),material,0,"",
                Map.of("cities_arise:boundary",c.edgeId()));
    }
    private static void put(Map<String,DebugBlockPlacementOperation> result,DebugBlockPlacementOperation op) {
        int y=op.platformY().orElse(0)+op.verticalOffset(); String key=key(op.point().x(),y,op.point().z());
        var previous=result.get(key);
        if(previous==null || previous.role().priority()<op.role().priority()) result.put(key,op);
    }
    private static String key(long x,int y,long z) { return x+":"+y+":"+z; }
    private static int connections(ParcelBoundaryPlan.Cell c,Set<String> points) {
        int x=c.point().x(),z=c.point().z(),y=c.y();
        return (points.contains(key(x,y,(long)z-1))?1:0) | (points.contains(key((long)x+1,y,z))?2:0)
                | (points.contains(key(x,y,(long)z+1))?4:0) | (points.contains(key((long)x-1,y,z))?8:0);
    }
}
