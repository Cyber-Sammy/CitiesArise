package com.cybersammy.citiesarise.minecraft.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoiseTerrainCacheBudgetTest {
    @Test void tallWorldMasksAreChargedByHeightWithinOneProcessWideBudget() {
        assertEquals(2_096L*256/64,NoiseTerrainCacheBudget.maskWords(2096));
        assertEquals(NoiseTerrainCacheBudget.HEIGHT_ENTRY_BYTES+2096L*256/8,NoiseTerrainCacheBudget.cellBytes(2096));
        assertTrue(NoiseTerrainCacheBudget.cellBytes(2096)>NoiseTerrainCacheBudget.cellBytes(512));
        long cityTiles=36L*36; // Worst alignment including the survey's slope halo.
        assertTrue(cityTiles*NoiseTerrainCacheBudget.cellBytes(2096)<NoiseTerrainCacheBudget.CELL_BYTES,
                "A 540x540 city must not evict its own JJThunder masks between stages");
        assertEquals(128L*1024*1024,NoiseTerrainCacheBudget.CELL_BYTES+NoiseTerrainCacheBudget.HEIGHT_BYTES+NoiseTerrainCacheBudget.PRELIMINARY_BYTES);
        assertTrue(NoiseTerrainCacheBudget.fits(2096));
        assertFalse(NoiseTerrainCacheBudget.fits(Integer.MAX_VALUE));
        assertFalse(NoiseTerrainCacheBudget.fits(0));
        assertFalse(NoiseTerrainCacheBudget.fits(4_000_000));
    }
    @Test void budgetArithmeticRejectsInvalidOrOverflowingRequests() {
        assertThrows(IllegalArgumentException.class,()->NoiseTerrainCacheBudget.maskWords(0));
        assertThrows(IllegalArgumentException.class,()->NoiseTerrainCacheBudget.maskWords(-1));
        assertThrows(ArithmeticException.class,()->NoiseTerrainCacheBudget.maskWords(Integer.MAX_VALUE));
    }
}
