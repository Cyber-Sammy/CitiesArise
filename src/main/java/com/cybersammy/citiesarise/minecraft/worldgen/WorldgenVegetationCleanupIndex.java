package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class WorldgenVegetationCleanupIndex {
    private final Map<PlacementChunk, List<DebugBlockPlacementOperation>> operationsByAffectedChunk;

    WorldgenVegetationCleanupIndex(DebugPlacementPlan plan) {
        Objects.requireNonNull(plan, "plan");
        Map<PlacementChunk, List<DebugBlockPlacementOperation>> indexed = new LinkedHashMap<>();
        for (DebugBlockPlacementOperation operation : plan.operations()) {
            addAffectedChunks(indexed, operation);
        }
        Map<PlacementChunk, List<DebugBlockPlacementOperation>> copied = new LinkedHashMap<>();
        indexed.forEach((chunk, operations) -> copied.put(chunk, List.copyOf(operations)));
        this.operationsByAffectedChunk = Map.copyOf(copied);
    }

    WorldgenVegetationCleanupPlan slice(PlacementChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        return new WorldgenVegetationCleanupPlan(
                chunk,
                operationsByAffectedChunk.getOrDefault(chunk, List.of())
        );
    }

    private static void addAffectedChunks(
            Map<PlacementChunk, List<DebugBlockPlacementOperation>> indexed,
            DebugBlockPlacementOperation operation
    ) {
        int radius = WorldgenPlacementPolicy.FINAL_VEGETATION_CLEARANCE_RADIUS;
        PlacementChunk minimum = PlacementChunk.containing(
                operation.point().x() - radius,
                operation.point().z() - radius
        );
        PlacementChunk maximum = PlacementChunk.containing(
                operation.point().x() + radius,
                operation.point().z() + radius
        );
        for (int chunkZ = minimum.z(); chunkZ <= maximum.z(); chunkZ++) {
            for (int chunkX = minimum.x(); chunkX <= maximum.x(); chunkX++) {
                PlacementChunk chunk = new PlacementChunk(chunkX, chunkZ);
                indexed.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(operation);
            }
        }
    }
}
