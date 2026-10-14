package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.StructuralSupportPlanner;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;

/** Explicit saved removals above occupied floors; never creates substrate below a suspended deck. */
final class FinalTerrainClearance {
    static int add(Map<String,DebugBlockPlacementOperation> operations, DebugPlacementPlan original,
            List<StructuralSupportPlanner.Envelope> envelopes,StructuralSupportPlanner.Ground ground, int height) {
        var floors=new LinkedHashMap<GridPoint,DebugBlockPlacementOperation>();
        for(var op:original.operations()) if(op.platformY().isPresent() && floor(op.role())) {
            var previous=floors.get(op.point());
            if(previous==null || y(op)<y(previous)) floors.put(op.point(),op);
        }
        for(var e:envelopes) if(e.kind()==StructuralSupportPlanner.Kind.PARCEL) {
            var b=e.bounds();
            for(int z=b.minZ();z<b.maxZExclusive();z++) for(int x=b.minX();x<b.maxXExclusive();x++) {
                var point=new GridPoint(x,z);
                floors.putIfAbsent(point,new DebugBlockPlacementOperation(point,0,DebugPlacementRole.PARCEL_YARD,e.source(),OptionalInt.of(e.platformY()),"",0));
            }
        }
        int count=0;
        for(var floor:floors.values()) {
            if(ground.solid(floor.point().x(),y(floor)+height+1,floor.point().z())) return -1;
            for(int y=y(floor)+1;y<=y(floor)+height;y++) {
            if(!ground.solid(floor.point().x(),y,floor.point().z())) continue;
            var op=new DebugBlockPlacementOperation(floor.point(),0,DebugPlacementRole.TERRAIN_CLEARANCE,
                    floor.sourceElementId(),OptionalInt.of(y),"minecraft:air",0);
            if(operations.putIfAbsent(key(op),op)==null) count++;
            }
        }
        return count;
    }
    static String key(DebugBlockPlacementOperation op) {return op.point().x()+","+y(op)+","+op.point().z();}
    private static int y(DebugBlockPlacementOperation op) {return op.platformY().orElseThrow()+op.verticalOffset();}
    private static boolean floor(DebugPlacementRole role) {
        return switch(role) {
            case PARCEL_YARD,PARCEL_BOUNDARY,BUILDING_FLOOR,ROAD_SURFACE,WORN_ROAD_SURFACE,ROAD_TRANSITION_STEP,
                    BUILDING_ACCESS_SURFACE,BUILDING_ACCESS_STEP,BRIDGE_DECK,BRIDGE_STEP -> true;
            default -> false;
        };
    }
}
