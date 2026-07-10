package com.cybersammy.citiesarise.minecraft.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

final class PlacementChunkTest {
    @Test
    void mapsPositiveBlockCoordinatesAtChunkBorders() {
        assertEquals(new PlacementChunk(0, 0), PlacementChunk.containing(point(0, 0)));
        assertEquals(new PlacementChunk(0, 0), PlacementChunk.containing(point(15, 15)));
        assertEquals(new PlacementChunk(1, 1), PlacementChunk.containing(point(16, 16)));
    }

    @Test
    void mapsNegativeBlockCoordinatesUsingFloorDivision() {
        assertEquals(new PlacementChunk(-1, -1), PlacementChunk.containing(point(-1, -1)));
        assertEquals(new PlacementChunk(-1, -1), PlacementChunk.containing(point(-16, -16)));
        assertEquals(new PlacementChunk(-2, -2), PlacementChunk.containing(point(-17, -17)));
    }

    @Test
    void reportsWhetherPointBelongsToChunk() {
        PlacementChunk chunk = new PlacementChunk(-1, 2);

        assertTrue(chunk.contains(point(-16, 32)));
        assertTrue(chunk.contains(point(-1, 47)));
        assertFalse(chunk.contains(point(0, 47)));
        assertFalse(chunk.contains(point(-1, 48)));
    }

    @Test
    void rejectsNullPoint() {
        assertThrows(NullPointerException.class, () -> PlacementChunk.containing(null));
        assertThrows(NullPointerException.class, () -> new PlacementChunk(0, 0).contains(null));
    }

    private static GridPoint point(int x, int z) {
        return new GridPoint(x, z);
    }
}
