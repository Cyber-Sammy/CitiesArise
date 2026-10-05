package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import com.cybersammy.citiesarise.core.registry.SettlementIndex;
import org.junit.jupiter.api.Test;

class SettlementRegistryTest {
    private static SettlementIndex.Metadata metadata(String dimension, int x, String profile) {
        return new SettlementIndex.Metadata(dimension, profile, "test:plan", x, -20,
                x - 5, -25, x + 20, -5, Set.of(1L, 2L));
    }

    @Test void identityDoesNotDependOnContentButDoesIncludeDimension() {
        assertEquals(metadata("test:a", 0, "test:old").id(), metadata("test:a", 0, "test:new").id());
        assertNotEquals(metadata("test:a", 0, "test:old").id(), metadata("test:b", 0, "test:old").id());
    }

    @Test void progressRequiresEveryDistinctPlacementChunk() {
        var registry = new SettlementIndex();
        var data = metadata("test:a", 0, "test:profile");
        registry.observe(data);
        assertEquals("START_KNOWN", registry.nearest(0, 0).orElseThrow().state());
        registry.placed(data, 2, 10);
        registry.placed(data, 2, 500);
        assertEquals("PARTIALLY_PLACED", registry.nearest(0, 0).orElseThrow().state());
        assertEquals(10, registry.nearest(0, 0).orElseThrow().placementNanos());
        registry.placed(data, 1, 20);
        registry.observe(data);
        assertEquals("PLACEMENT_COMPLETE", registry.nearest(0, 0).orElseThrow().state());
        assertEquals(30, registry.nearest(0, 0).orElseThrow().placementNanos());
        assertEquals(1, registry.size());
        assertThrows(IllegalArgumentException.class, () -> registry.placed(data, 3, 1));
        assertThrows(IllegalArgumentException.class, () -> registry.placed(metadata("test:a", 0, "test:changed"), 1, 1));
    }

    @Test void lookupHandlesNegativeAndExtremeCoordinates() {
        var registry = new SettlementIndex();
        assertTrue(registry.nearest(0, 0).isEmpty());
        var west = metadata("test:a", -29_000_000, "test:profile");
        var east = metadata("test:a", 29_000_000, "test:profile");
        registry.observe(east); registry.observe(west);
        assertEquals(west, registry.nearest(Integer.MIN_VALUE, -20).orElseThrow().metadata());
        assertEquals(east, registry.nearest(Integer.MAX_VALUE, -20).orElseThrow().metadata());
    }
}
