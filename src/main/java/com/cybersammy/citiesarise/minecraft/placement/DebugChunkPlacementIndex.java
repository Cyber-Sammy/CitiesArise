package com.cybersammy.citiesarise.minecraft.placement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DebugChunkPlacementIndex {
    private final Map<PlacementChunk, List<DebugBlockPlacementOperation>> operationsByChunk;
    private final int operationCount;

    DebugChunkPlacementIndex(Map<PlacementChunk, List<DebugBlockPlacementOperation>> operationsByChunk) {
        Objects.requireNonNull(operationsByChunk, "operationsByChunk");

        Map<PlacementChunk, List<DebugBlockPlacementOperation>> copiedOperations = new LinkedHashMap<>();
        int copiedOperationCount = 0;
        for (Map.Entry<PlacementChunk, List<DebugBlockPlacementOperation>> entry : operationsByChunk.entrySet()) {
            PlacementChunk chunk = Objects.requireNonNull(entry.getKey(), "operationsByChunk key");
            List<DebugBlockPlacementOperation> operations = List.copyOf(
                    Objects.requireNonNull(entry.getValue(), "operationsByChunk value")
            );
            copiedOperations.put(chunk, operations);
            copiedOperationCount = Math.addExact(copiedOperationCount, operations.size());
        }

        this.operationsByChunk = Map.copyOf(copiedOperations);
        this.operationCount = copiedOperationCount;
    }

    public DebugChunkPlacementPlan slice(PlacementChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        List<DebugBlockPlacementOperation> operations = operationsByChunk.getOrDefault(chunk, List.of());
        return new DebugChunkPlacementPlan(chunk, operations);
    }

    public int chunkCount() {
        return operationsByChunk.size();
    }

    public int operationCount() {
        return operationCount;
    }
}
