package com.cybersammy.citiesarise.minecraft.placement;

import net.minecraft.world.level.block.state.BlockState;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftVegetationClassifier;

public interface DebugBlockMaterialProvider {
    BlockState blockState(DebugPlacementRole role);
    default BlockState blockState(DebugBlockPlacementOperation operation) {
        var state=operation.material().isEmpty() ? blockState(operation.role())
                : MinecraftContentMaterials.resolve(operation.material(),operation.rotation());
        return operation.role()==DebugPlacementRole.PARCEL_FENCE?MinecraftContentMaterials.fenceConnections(state,operation.providerData().get("cities_arise:fence_connections")):state;
    }

    default boolean needsVegetationProtection(DebugBlockPlacementOperation operation) {
        return MinecraftVegetationClassifier.isClearable(blockState(operation));
    }

    default boolean needsVegetationProtection(DebugPlacementRole role) {
        BlockState state = blockState(role);
        return MinecraftVegetationClassifier.isClearable(state);
    }
}
