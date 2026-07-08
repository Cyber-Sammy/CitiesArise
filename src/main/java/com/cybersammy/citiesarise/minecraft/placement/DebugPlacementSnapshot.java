package com.cybersammy.citiesarise.minecraft.placement;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record DebugPlacementSnapshot(List<BlockChange> changes) {
    public DebugPlacementSnapshot {
        Objects.requireNonNull(changes, "changes");
        changes = List.copyOf(changes);
    }

    public int size() {
        return changes.size();
    }

    public record BlockChange(BlockPos position, BlockState previousState) {
        public BlockChange {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(previousState, "previousState");
            position = position.immutable();
        }
    }
}
