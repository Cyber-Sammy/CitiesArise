package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation;
import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import java.util.List;
import java.util.Objects;

record WorldgenVegetationCleanupPlan(
        PlacementChunk chunk,
        List<DebugBlockPlacementOperation> influencingOperations,
        List<DebugBlockPlacementOperation> protectedOperations
) {
    WorldgenVegetationCleanupPlan(PlacementChunk chunk,List<DebugBlockPlacementOperation> operations) {
        this(chunk,operations,operations);
    }
    WorldgenVegetationCleanupPlan {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(influencingOperations, "influencingOperations");
        influencingOperations = List.copyOf(influencingOperations);
        protectedOperations = List.copyOf(protectedOperations);
    }
}
