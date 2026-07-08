package com.cybersammy.citiesarise.minecraft.placement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class DebugPlacementSnapshotBuilder {
    private final SinglePlacementUndoBuffer<BlockPos, BlockState> buffer = new SinglePlacementUndoBuffer<>();

    public void capture(BlockPos position, BlockState previousState) {
        buffer.capture(position.immutable(), previousState);
    }

    public DebugPlacementSnapshot build() {
        List<DebugPlacementSnapshot.BlockChange> changes = buffer.entries()
                .stream()
                .map(entry -> new DebugPlacementSnapshot.BlockChange(entry.key(), entry.value()))
                .toList();

        return new DebugPlacementSnapshot(changes);
    }
}
