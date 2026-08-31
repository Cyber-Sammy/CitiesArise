package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation;
import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import java.util.List;
import java.util.Objects;

record WorldgenVegetationCleanupPlan(
        PlacementChunk chunk,
        List<DebugBlockPlacementOperation> influencingOperations
) {
    WorldgenVegetationCleanupPlan {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(influencingOperations, "influencingOperations");
        influencingOperations = List.copyOf(influencingOperations);
    }
}
