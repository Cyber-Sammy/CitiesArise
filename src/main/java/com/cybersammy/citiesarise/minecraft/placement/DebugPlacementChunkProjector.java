package com.cybersammy.citiesarise.minecraft.placement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DebugPlacementChunkProjector {
    public DebugChunkPlacementIndex partition(DebugPlacementPlan plan) {
        Objects.requireNonNull(plan, "plan");

        Map<PlacementChunk, List<DebugBlockPlacementOperation>> operationsByChunk = new LinkedHashMap<>();
        for (DebugBlockPlacementOperation operation : plan.operations()) {
            PlacementChunk chunk = PlacementChunk.containing(operation.point());
            operationsByChunk.computeIfAbsent(chunk, ignoredChunk -> new ArrayList<>()).add(operation);
        }

        return new DebugChunkPlacementIndex(operationsByChunk);
    }
}
