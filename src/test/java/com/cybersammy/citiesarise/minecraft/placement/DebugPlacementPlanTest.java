package com.cybersammy.citiesarise.minecraft.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class DebugPlacementPlanTest {
    @Test
    void rejectsNullOperations() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new DebugPlacementPlan(null)
        );

        assertEquals("operations", exception.getMessage());
    }
}
