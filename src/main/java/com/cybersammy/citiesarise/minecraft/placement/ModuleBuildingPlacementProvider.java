package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import java.util.*;

public final class ModuleBuildingPlacementProvider implements BuildingPlacementProvider {
    @Override
    public List<DebugBlockPlacementOperation> create(BuildingSlot slot,GridPoint entrance) {
        var content=slot.content().orElseThrow();
        var composition=content.resolved().orElseThrow(() -> new IllegalArgumentException("Composition must be resolved before placement"));
        List<DebugBlockPlacementOperation> operations=new ArrayList<>();
        for(var cell:composition.cells()) {
            String material=cell.material().contains(":")?cell.material():content.materials().get(cell.material());
            if(material==null) throw new IllegalArgumentException("Unknown material token: "+cell.material());
            operations.add(new DebugBlockPlacementOperation(new GridPoint(slot.bounds().minX()+cell.position().x(),slot.bounds().minZ()+cell.position().z()),
                    cell.position().y(),DebugPlacementRole.CONTENT_BLOCK,slot.id(),OptionalInt.empty(),material,cell.rotation()));
        }
        return List.copyOf(operations);
    }
}
