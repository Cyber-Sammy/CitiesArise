package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.content.ResolvedSettlementContent;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.*;

/** Only paints frozen authored entrance geometry and its reserved, already prepared access. */
final class EntrancePlacement {
    private EntrancePlacement() { }
    static DebugPlacementPlan add(DebugPlacementPlan original,SettlementPlan city,ResolvedSettlementContent content) {
        if(content.buildings().values().stream().allMatch(b->b.resolvedEntrances().isEmpty()))return original;
        var operations=new LinkedHashMap<DebugPlacementPosition,DebugBlockPlacementOperation>();original.operations().forEach(o->operations.put(key(o),o));
        for(var slot:city.buildingSlots()) {
            var building=content.buildings().get(slot.id());if(building==null)continue;
            for(var entrance:building.resolvedEntrances()) {
                for(var cell:entrance.composition().cells()) {
                    String material=resolve(cell.material(),building.materials());
                    var op=new DebugBlockPlacementOperation(new GridPoint(slot.bounds().minX()+cell.position().x(),slot.bounds().minZ()+cell.position().z()),
                            cell.position().y(),DebugPlacementRole.CONTENT_BLOCK,slot.id(),OptionalInt.of(entrance.platformY()),material,cell.rotation(),"",cell.providerData());
                    operations.put(key(op),op);
                }
                for(var path:entrance.path()) {
                    var op=new DebugBlockPlacementOperation(path.point(),0,DebugPlacementRole.BUILDING_ACCESS_SURFACE,slot.id(),
                            OptionalInt.of(path.elevation()),resolve(path.material(),building.materials()),0);
                    var existing=operations.get(key(op));
                    if(existing==null || existing.role()!=DebugPlacementRole.BUILDING_ACCESS_STEP)operations.put(key(op),op);
                    for(int y=1;y<=2;y++) {
                        var air=new DebugBlockPlacementOperation(path.point(),y,DebugPlacementRole.CONTENT_BLOCK,slot.id(),OptionalInt.of(path.elevation()),
                                building.entrances().orElseThrow().airMaterial(),0);operations.put(key(air),air);
                    }
                }
            }
        }
        return new DebugPlacementPlan(List.copyOf(operations.values()));
    }
    private static DebugPlacementPosition key(DebugBlockPlacementOperation op) {
        return new DebugPlacementPosition(op.point(),Math.addExact(op.platformY().orElse(0),op.verticalOffset()));
    }
    private static String resolve(String material,Map<String,String> palette) {
        String value=material.contains(":")?material:palette.get(material);
        if(value==null)throw new IllegalArgumentException("Unknown entrance material: "+material);return value;
    }
}
