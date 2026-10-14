package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.minecraft.placement.*;
import java.util.*;

final class FinalRetainingJunctions {
    record Result(DebugPlacementPlan plan,int additionalCells) { }
    static Result apply(DebugPlacementPlan original,StructuralSupportBlueprint blueprint,Set<GridPoint> suspended,
            StructuralSupportPlanner.Ground ground) {
        var bySource=new HashMap<PlanElementId,StructuralSupportPlanner.Envelope>();
        blueprint.envelopes().stream().filter(e->e.kind()==StructuralSupportPlanner.Kind.PARCEL).forEach(e->bySource.put(e.source(),e));
        var edges=new HashSet<RetainingJunctionPlanner.Edge>();
        for(var op:original.operations()) if(op.role()==DebugPlacementRole.TERRAIN_RETAINING_WALL) {
            var e=bySource.get(op.sourceElementId());
            if(e!=null) RetainingJunctionPlanner.outwardSides(e.bounds(),op.point()).forEach(s->edges.add(new RetainingJunctionPlanner.Edge(e.source(),s)));
        }
        if(edges.isEmpty()) return new Result(original,0);
        var operations=new LinkedHashMap<String,DebugBlockPlacementOperation>();
        for(var op:original.operations()) {
            var e=bySource.get(op.sourceElementId());
            boolean outer=e!=null && RetainingJunctionPlanner.outwardSides(e.bounds(),op.point()).stream()
                    .anyMatch(side->edges.contains(new RetainingJunctionPlanner.Edge(e.source(),side)));
            if(outer && (op.role()==DebugPlacementRole.TERRAIN_SURFACE || op.role()==DebugPlacementRole.TERRAIN_RETAINING_WALL
                    || op.role()==DebugPlacementRole.SUPPORT_LINING)) continue;
            operations.put(FinalTerrainClearance.key(op),op);
        }
        int added=0;String material=blueprint.materials().getOrDefault("TERRAIN_RETAINING_WALL","minecraft:cobblestone");
        for(var face:RetainingJunctionPlanner.plan(blueprint.envelopes(),edges,suspended,ground)) {
            var probe=new DebugBlockPlacementOperation(face.point(),0,DebugPlacementRole.SUPPORT_LINING,face.source(),OptionalInt.of(face.y()),material,0);
            var key=FinalTerrainClearance.key(probe);var previous=operations.get(key);
            if(previous!=null) probe=new DebugBlockPlacementOperation(previous.point(),previous.verticalOffset(),previous.role(),
                    previous.sourceElementId(),previous.platformY(),material,previous.rotation(),previous.fillMaterial(),previous.providerData());
            else added++;
            operations.put(key,probe);
        }
        return new Result(new DebugPlacementPlan(List.copyOf(operations.values())),added);
    }
}
