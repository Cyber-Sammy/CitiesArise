package com.cybersammy.citiesarise.minecraft.placement;

import java.util.Objects;

final class ScopedSingleSnapshotStore<S, T> {
    private S scope;
    private T snapshot;

    void save(S scope, T snapshot) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    void clear() {
        scope = null;
        snapshot = null;
    }

    SnapshotLookup<T> lookup(S currentScope) {
        Objects.requireNonNull(currentScope, "currentScope");

        if (snapshot == null) {
            return SnapshotLookup.empty();
        }

        if (!scope.equals(currentScope)) {
            return SnapshotLookup.wrongScope();
        }

        return SnapshotLookup.found(snapshot);
    }

    record SnapshotLookup<T>(SnapshotLookupStatus status, T snapshot) {
        SnapshotLookup {
            Objects.requireNonNull(status, "status");
        }

        static <T> SnapshotLookup<T> found(T snapshot) {
            return new SnapshotLookup<>(SnapshotLookupStatus.FOUND, Objects.requireNonNull(snapshot, "snapshot"));
        }

        static <T> SnapshotLookup<T> empty() {
            return new SnapshotLookup<>(SnapshotLookupStatus.EMPTY, null);
        }

        static <T> SnapshotLookup<T> wrongScope() {
            return new SnapshotLookup<>(SnapshotLookupStatus.WRONG_SCOPE, null);
        }
    }

    enum SnapshotLookupStatus {
        FOUND,
        EMPTY,
        WRONG_SCOPE
    }
}
