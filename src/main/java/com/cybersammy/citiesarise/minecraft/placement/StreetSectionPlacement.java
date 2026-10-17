package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.road.StreetSectionGeometry;
import java.util.*;

/** Material adapter over frozen core street surfaces; no random choices during chunk placement. */
final class StreetSectionPlacement {
    private StreetSectionPlacement() { }
    static DebugPlacementPlan apply(DebugPlacementPlan placement,SettlementPlan plan) {return apply(placement,plan,null);}
    static DebugPlacementPlan apply(DebugPlacementPlan placement,SettlementPlan plan,com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan preparation) {
        var surfaces=StreetSectionGeometry.surfaces(plan.roadGraph());if(surfaces.isEmpty())return placement;
        var stairs=new HashMap<com.cybersammy.citiesarise.core.geometry.GridPoint,String>();
        if(preparation!=null && preparation.transitionSettings().streets().stairAccess()) {
            var zones=new HashMap<com.cybersammy.citiesarise.core.model.PlanElementId,com.cybersammy.citiesarise.core.earthwork.ElevationZone>();
            preparation.elevationPlan().zones().forEach(z->zones.put(z.sourceElementId(),z));
            String material=preparation.transitionSettings().streets().stairMaterial();
            for(var t:preparation.elevationPlan().transitions())if(t.concentratedStairs() && t.elevationDelta()>0) {
                var points=com.cybersammy.citiesarise.core.earthwork.ElevationTransitionPolicy.materialize(t,zones.get(t.sourceZoneId()),zones.get(t.targetZoneId()));
                for(int i=0;i<points.size();i++) {
                    var point=points.get(i);if(!point.step())continue;
                    var next=i+1<points.size()?points.get(i+1).point():t.anchor();
                    int dx=Integer.signum(next.x()-point.point().x()),dz=Integer.signum(next.z()-point.point().z());
                    if(t.targetElevation()<t.sourceElevation()){dx=-dx;dz=-dz;}
                    stairs.put(point.point(),NativeStreetStairMaterial.orient(material,dx,dz));
                }
            }
        }
        var result=new ArrayList<DebugBlockPlacementOperation>();
        for(var op:placement.operations()) {
            if(op.role()==DebugPlacementRole.ROAD_END_CURB && surfaces.containsKey(op.point()))continue;
            boolean surface=op.role()==DebugPlacementRole.ROAD_SURFACE || op.role()==DebugPlacementRole.WORN_ROAD_SURFACE
                    || op.role()==DebugPlacementRole.BRIDGE_DECK && op.verticalOffset()==0;
            var cell=surface?surfaces.get(op.point()):null;
            if(op.role()==DebugPlacementRole.BUILDING_ACCESS_STEP && stairs.containsKey(op.point()))
                op=new DebugBlockPlacementOperation(op.point(),op.verticalOffset(),op.role(),op.sourceElementId(),op.platformY(),stairs.get(op.point()),op.rotation(),op.fillMaterial(),op.providerData());
            if(cell!=null && !cell.material().isEmpty())op=new DebugBlockPlacementOperation(op.point(),op.verticalOffset(),op.role(),
                    op.sourceElementId(),op.platformY(),cell.material(),op.rotation(),op.fillMaterial(),op.providerData());
            result.add(op);
        }
        return new DebugPlacementPlan(result);
    }
}
