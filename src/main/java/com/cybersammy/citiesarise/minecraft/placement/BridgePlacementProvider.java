package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.model.BridgePlan;
import java.util.List;

@FunctionalInterface
public interface BridgePlacementProvider {
    List<DebugBlockPlacementOperation> create(BridgePlan bridge);
}
