package com.cybersammy.citiesarise.minecraft.placement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class DebugPlacementSnapshotBuilder {
    private final SinglePlacementUndoBuffer<BlockPos, DebugPlacementSnapshot.BlockChange> buffer = new SinglePlacementUndoBuffer<>();

    public void capture(BlockPos position, BlockState previousState) {
        buffer.capture(position.immutable(), new DebugPlacementSnapshot.BlockChange(position,previousState));
    }

    public void capture(net.minecraft.server.level.ServerLevel level, BlockPos position) {
        var entity=level.getBlockEntity(position);
        var data=entity==null ? null : entity.saveWithFullMetadata(level.registryAccess());
        buffer.capture(position.immutable(),new DebugPlacementSnapshot.BlockChange(position,level.getBlockState(position),data));
    }

    public DebugPlacementSnapshot build() {
        List<DebugPlacementSnapshot.BlockChange> changes = buffer.entries()
                .stream()
                .map(entry -> entry.value())
                .toList();

        return new DebugPlacementSnapshot(changes);
    }
}
