package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.*;

/** Merge by actual XYZ after ground earthwork, street, entrance and prop materialization. */
final class CityOverpassPlacement {
    private CityOverpassPlacement() { }
    static DebugPlacementPlan add(DebugPlacementPlan ground,SettlementPlan plan) {
        if(plan.overpasses().isEmpty())return ground;
        var operations=new LinkedHashMap<String,DebugBlockPlacementOperation>();ground.operations().forEach(op->operations.put(key(op),op));
        for(var module:plan.overpasses()) {
            var landings=new HashSet<GridPoint>();module.topology().columns().stream().filter(module::landing).forEach(c->landings.add(module.world(c.point())));
            for(var op:LayeredRoadPlacement.materialize(module).operations()) {
                var old=operations.get(key(op));
                if(old!=null && !old.material().equals("minecraft:air") && !old.material().equals(op.material())) {
                    if(landings.contains(op.point()) && (op.role()==DebugPlacementRole.BRIDGE_DECK || op.role()==DebugPlacementRole.BRIDGE_CLEARANCE))continue;
                    throw new IllegalArgumentException("City overpass overlaps occupied content at "+key(op));
                }
                operations.put(key(op),op);
            }
        }
        return new DebugPlacementPlan(List.copyOf(operations.values()));
    }
    private static String key(DebugBlockPlacementOperation op) {
        return op.point().x()+":"+(op.platformY().orElseThrow()+op.verticalOffset())+":"+op.point().z();
    }
}
