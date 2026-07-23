package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class DevelopmentCapacityTest {
    @Test
    void acceptsOrderedCapacityRange() {
        DevelopmentCapacity capacity = new DevelopmentCapacity(4, 6, 8);

        assertEquals(4, capacity.minimum());
        assertEquals(6, capacity.target());
        assertEquals(8, capacity.maximum());
    }

    @Test
    void createsFixedCompatibilityCapacity() {
        assertEquals(new DevelopmentCapacity(6, 6, 6), DevelopmentCapacity.fixed(6));
    }

    @Test
    void rejectsNonPositiveAndUnorderedValues() {
        assertThrows(IllegalArgumentException.class, () -> new DevelopmentCapacity(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new DevelopmentCapacity(2, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new DevelopmentCapacity(1, 3, 2));
    }
}
