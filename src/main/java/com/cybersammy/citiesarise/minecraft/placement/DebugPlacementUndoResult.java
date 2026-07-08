package com.cybersammy.citiesarise.minecraft.placement;

import java.util.Objects;

public record DebugPlacementUndoResult(DebugPlacementUndoStatus status, int restoredBlocks) {
    public DebugPlacementUndoResult {
        Objects.requireNonNull(status, "status");

        if (restoredBlocks < 0) {
            throw new IllegalArgumentException("restoredBlocks must not be negative");
        }

        if (status != DebugPlacementUndoStatus.RESTORED && restoredBlocks != 0) {
            throw new IllegalArgumentException("only restored undo results can report restored blocks");
        }
    }

    public static DebugPlacementUndoResult restored(int restoredBlocks) {
        return new DebugPlacementUndoResult(DebugPlacementUndoStatus.RESTORED, restoredBlocks);
    }

    public static DebugPlacementUndoResult empty() {
        return new DebugPlacementUndoResult(DebugPlacementUndoStatus.EMPTY, 0);
    }

    public static DebugPlacementUndoResult wrongDimension() {
        return new DebugPlacementUndoResult(DebugPlacementUndoStatus.WRONG_DIMENSION, 0);
    }
}
