package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.content.ResolvedSettlementContent;
import java.util.*;

/** Exact optional paths overlay yard surfaces without changing substrate preparation. */
final class ParcelOutdoorPlacement {
    private ParcelOutdoorPlacement() { }
    static DebugPlacementPlan add(DebugPlacementPlan original, ResolvedSettlementContent content) {
        if(content.parcels().values().stream().allMatch(p -> p.outdoor().paths().isEmpty())) return original;
        Map<DebugPlacementPosition,DebugBlockPlacementOperation> result=new LinkedHashMap<>();
        original.operations().forEach(op -> result.put(op.position(),op));
        content.parcels().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().value())).forEach(entry -> {
            for(var cell:entry.getValue().outdoor().paths()) {
                for(int dy=0;dy<=2;dy++) {
                    var op=new DebugBlockPlacementOperation(cell.point(),dy,dy==0?DebugPlacementRole.PARCEL_PATH:DebugPlacementRole.PARCEL_PATH_CLEARANCE,
                            entry.getKey(),OptionalInt.of(cell.y()),dy==0?cell.material():"minecraft:air",0,"",Map.of());
                    var previous=result.get(op.position());
                    if(previous==null || previous.role().priority()<op.role().priority()) result.put(op.position(),op);
                }
            }
        });
        return new DebugPlacementPlan(List.copyOf(result.values()));
    }
}
