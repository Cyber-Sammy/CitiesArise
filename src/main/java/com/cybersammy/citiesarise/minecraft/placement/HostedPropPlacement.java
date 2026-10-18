package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.content.ResolvedSettlementContent;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.*;

/** Hosted modules replace their prepared floor after surface templates and retain explicit access headroom. */
final class HostedPropPlacement {
    private HostedPropPlacement() { }
    static DebugPlacementPlan add(DebugPlacementPlan original,ResolvedSettlementContent content) {
        if(content.props().stream().noneMatch(p->p.zonePlacement().isPresent()))return original;
        var operations=new LinkedHashMap<DebugPlacementPosition,DebugBlockPlacementOperation>();original.operations().forEach(o->operations.put(key(o),o));
        for(var prop:content.props())if(prop.zonePlacement().isPresent()) {
            var zone=prop.zonePlacement().orElseThrow();
            for(var cell:prop.composition().cells()) {
                var point=prop.origin().add(cell.position());
                var operation=new DebugBlockPlacementOperation(new GridPoint(point.x(),point.z()),point.y(),DebugPlacementRole.CONTENT_BLOCK,
                        prop.source(),OptionalInt.of(prop.platformY()),resolve(cell.material(),prop.materials()),cell.rotation(),"",cell.providerData());
                operations.put(key(operation),operation);
            }
            for(var path:zone.path()) {
                var floor=new DebugBlockPlacementOperation(path.point(),0,DebugPlacementRole.BUILDING_ACCESS_SURFACE,prop.source(),
                        OptionalInt.of(path.y()),resolve(path.material(),prop.materials()),0);
                var existing=operations.get(key(floor));
                if(existing==null || !Set.of(DebugPlacementRole.BUILDING_ACCESS_SURFACE,DebugPlacementRole.BUILDING_ACCESS_STEP,
                        DebugPlacementRole.PARCEL_PATH,DebugPlacementRole.PARCEL_ALLEY,DebugPlacementRole.ROAD_SURFACE,DebugPlacementRole.WORN_ROAD_SURFACE).contains(existing.role()))
                    operations.put(key(floor),floor);
                for(int y=1;y<=2;y++) {
                    var air=new DebugBlockPlacementOperation(path.point(),y,DebugPlacementRole.CONTENT_BLOCK,prop.source(),OptionalInt.of(path.y()),zone.airMaterial(),0);
                    operations.put(key(air),air);
                }
            }
        }
        return new DebugPlacementPlan(List.copyOf(operations.values()));
    }
    private static DebugPlacementPosition key(DebugBlockPlacementOperation op) {return new DebugPlacementPosition(op.point(),Math.addExact(op.platformY().orElse(0),op.verticalOffset()));}
    private static String resolve(String material,Map<String,String> palette) {
        String result=material.contains(":")?material:palette.get(material);
        if(result==null)throw new IllegalArgumentException("Unknown hosted prop material: "+material);return result;
    }
}
