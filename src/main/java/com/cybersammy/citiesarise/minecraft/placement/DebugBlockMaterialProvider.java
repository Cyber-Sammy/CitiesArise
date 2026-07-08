package com.cybersammy.citiesarise.minecraft.placement;

import net.minecraft.world.level.block.state.BlockState;

public interface DebugBlockMaterialProvider {
    BlockState blockState(DebugPlacementRole role);
}
