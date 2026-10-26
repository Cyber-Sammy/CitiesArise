package com.cybersammy.citiesarise.minecraft.worldgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlacementQueueBudgetTest {
    @Test void expandedCityStillHasFiniteOwnerOperationAndEstimatedMemoryAdmission() {
        assertTrue(PlacementQueueBudget.fits(49,10_000_000));
        assertTrue(PlacementQueueBudget.fits(64,12_000_000));
        assertFalse(PlacementQueueBudget.fits(65,1));
        assertFalse(PlacementQueueBudget.fits(1,12_000_001));
        assertFalse(PlacementQueueBudget.fits(1,Long.MAX_VALUE));
        assertFalse(PlacementQueueBudget.fits(-1,1));
        assertTrue(PlacementQueueBudget.estimatedBytes(64,12_000_000)<=PlacementQueueBudget.MAX_ESTIMATED_BYTES);
    }
}
