package com.cybersammy.citiesarise.minecraft.placement;

import net.minecraft.world.level.block.state.BlockState;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftVegetationClassifier;

public interface DebugBlockMaterialProvider {
    BlockState blockState(DebugPlacementRole role);
    default boolean needsVegetationProtection(DebugPlacementRole role) {
        BlockState state = blockState(role);
        return MinecraftVegetationClassifier.isClearable(state);
    }
}
