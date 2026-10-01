package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import java.util.List;

/** Produces bounded operations for an already selected asset and its prepared entrance. */
public interface BuildingPlacementProvider {
    List<DebugBlockPlacementOperation> create(BuildingSlot slot, GridPoint entrance);
}
