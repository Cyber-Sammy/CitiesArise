package com.cybersammy.citiesarise.minecraft.placement;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public final class DebugPlacementUndoStore {
    private static final int UPDATE_FLAGS = 3;

    private DebugPlacementSnapshot lastSnapshot;

    public void save(DebugPlacementSnapshot snapshot) {
        lastSnapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public void clear() {
        lastSnapshot = null;
    }

    public Optional<DebugPlacementSnapshot> lastSnapshot() {
        return Optional.ofNullable(lastSnapshot);
    }

    public int undoLast(ServerLevel level) {
        Objects.requireNonNull(level, "level");

        DebugPlacementSnapshot snapshot = lastSnapshot;
        clear();

        if (snapshot == null) {
            return 0;
        }

        List<DebugPlacementSnapshot.BlockChange> changes = snapshot.changes();

        for (int index = changes.size() - 1; index >= 0; index--) {
            DebugPlacementSnapshot.BlockChange change = changes.get(index);
            level.setBlock(change.position(), change.previousState(), UPDATE_FLAGS);
        }

        return changes.size();
    }
}
