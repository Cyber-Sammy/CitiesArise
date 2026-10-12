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

    public record BlockChange(BlockPos position, BlockState previousState, net.minecraft.nbt.CompoundTag previousEntityData) {
        public BlockChange(BlockPos position, BlockState previousState) { this(position,previousState,null); }
        public BlockChange {
            previousEntityData=previousEntityData==null?null:previousEntityData.copy();
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(previousState, "previousState");
            position = position.immutable();
        }
        @Override public net.minecraft.nbt.CompoundTag previousEntityData() { return previousEntityData==null?null:previousEntityData.copy(); }
    }
}
