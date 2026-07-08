package com.cybersammy.citiesarise.minecraft.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ScopedSingleSnapshotStoreTest {
    @Test
    void returnsWrongScopeWithoutConsumingSnapshot() {
        ScopedSingleSnapshotStore<String, String> store = new ScopedSingleSnapshotStore<>();

        store.save("overworld", "snapshot");

        assertEquals(
                ScopedSingleSnapshotStore.SnapshotLookupStatus.WRONG_SCOPE,
                store.lookup("nether").status()
        );
        assertEquals(
                ScopedSingleSnapshotStore.SnapshotLookupStatus.FOUND,
                store.lookup("overworld").status()
        );
    }

    @Test
    void replacesPreviousSnapshot() {
        ScopedSingleSnapshotStore<String, String> store = new ScopedSingleSnapshotStore<>();

        store.save("overworld", "first");
        store.save("overworld", "second");

        ScopedSingleSnapshotStore.SnapshotLookup<String> lookup = store.lookup("overworld");

        assertEquals(ScopedSingleSnapshotStore.SnapshotLookupStatus.FOUND, lookup.status());
        assertEquals("second", lookup.snapshot());
    }
}
