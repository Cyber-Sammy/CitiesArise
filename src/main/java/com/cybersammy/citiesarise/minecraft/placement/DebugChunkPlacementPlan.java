package com.cybersammy.citiesarise.minecraft.placement;

import java.util.List;
import java.util.Objects;

public record DebugChunkPlacementPlan(
        PlacementChunk chunk,
        List<DebugBlockPlacementOperation> operations
) {
    public DebugChunkPlacementPlan {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(operations, "operations");
        validateOperations(chunk, operations);
        operations = List.copyOf(operations);
    }

    public int size() {
        return operations.size();
    }

    private static void validateOperations(
            PlacementChunk chunk,
            List<DebugBlockPlacementOperation> operations
    ) {
        for (DebugBlockPlacementOperation operation : operations) {
            if (operation == null) {
                throw new NullPointerException("operations contains null");
            }

            if (!chunk.contains(operation.point())) {
                throw new IllegalArgumentException("operation is outside target chunk: " + operation.point());
            }
        }
    }
}
