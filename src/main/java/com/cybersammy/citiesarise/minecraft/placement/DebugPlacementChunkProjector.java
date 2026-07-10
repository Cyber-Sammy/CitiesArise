package com.cybersammy.citiesarise.minecraft.placement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DebugPlacementChunkProjector {
    public DebugChunkPlacementPlan project(DebugPlacementPlan plan, PlacementChunk chunk) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(chunk, "chunk");

        List<DebugBlockPlacementOperation> chunkOperations = new ArrayList<>();
        for (DebugBlockPlacementOperation operation : plan.operations()) {
            if (chunk.contains(operation.point())) {
                chunkOperations.add(operation);
            }
        }

        return new DebugChunkPlacementPlan(chunk, chunkOperations);
    }
}
