package com.cybersammy.citiesarise.minecraft.placement;

import java.util.List;
import java.util.Objects;

public record DebugPlacementPlan(List<DebugBlockPlacementOperation> operations) {
    public DebugPlacementPlan {
        Objects.requireNonNull(operations, "operations");
        operations = List.copyOf(operations);
    }

    public int size() {
        return operations.size();
    }
}
