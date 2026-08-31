package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class WorldgenSettlementLocatorPlacementTest {
    @Test
    void scalesPositiveSettlementRegionToItsStructurePlacementGridCell() {
        assertEquals(48, WorldgenPlacementCoordinates.probeChunk(6, 8));
        assertEquals(56, WorldgenPlacementCoordinates.probeChunk(7, 8));
    }

    @Test
    void scalesNegativeSettlementRegionToItsStructurePlacementGridCell() {
        assertEquals(-48, WorldgenPlacementCoordinates.probeChunk(-6, 8));
        assertEquals(-56, WorldgenPlacementCoordinates.probeChunk(-7, 8));
    }
}
