package com.cybersammy.citiesarise.minecraft.placement;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class DebugPlacementUndoStore {
    private static final int UPDATE_FLAGS = 3;

    private final ScopedSingleSnapshotStore<ResourceKey<Level>, DebugPlacementSnapshot> snapshots = new ScopedSingleSnapshotStore<>();

    public void save(ServerLevel level, DebugPlacementSnapshot snapshot) {
        Objects.requireNonNull(level, "level");
        snapshots.save(level.dimension(), snapshot);
    }

    public void clear() {
        snapshots.clear();
    }

    public DebugPlacementUndoResult undoLast(ServerLevel level) {
        Objects.requireNonNull(level, "level");

        ScopedSingleSnapshotStore.SnapshotLookup<DebugPlacementSnapshot> lookup = snapshots.lookup(level.dimension());

        if (lookup.status() == ScopedSingleSnapshotStore.SnapshotLookupStatus.EMPTY) {
            return DebugPlacementUndoResult.empty();
        }

        if (lookup.status() == ScopedSingleSnapshotStore.SnapshotLookupStatus.WRONG_SCOPE) {
            return DebugPlacementUndoResult.wrongDimension();
        }

        clear();
        List<DebugPlacementSnapshot.BlockChange> changes = lookup.snapshot().changes();

        for (int index = changes.size() - 1; index >= 0; index--) {
            DebugPlacementSnapshot.BlockChange change = changes.get(index);
            level.setBlock(change.position(), change.previousState(), UPDATE_FLAGS);
        }

        return DebugPlacementUndoResult.restored(changes.size());
    }
}
