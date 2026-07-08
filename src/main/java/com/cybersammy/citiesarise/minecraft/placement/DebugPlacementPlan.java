package com.cybersammy.citiesarise.minecraft.placement;

import java.util.List;

public record DebugPlacementPlan(List<DebugBlockPlacementOperation> operations) {
    public DebugPlacementPlan {
        operations = List.copyOf(operations);
    }

    public int size() {
        return operations.size();
    }
}
