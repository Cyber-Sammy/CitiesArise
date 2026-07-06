package com.cybersammy.citiesarise.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class PlanElementIdTest {
    @Test
    void trimsStableIdentifierValue() {
        PlanElementId id = new PlanElementId(" settlement/main ");

        assertEquals("settlement/main", id.value());
    }

    @Test
    void createsDeterministicChildId() {
        PlanElementId parent = new PlanElementId("settlement/main");

        assertEquals(new PlanElementId("settlement/main/road-1"), parent.child("road-1"));
    }

    @Test
    void rejectsBlankOrWhitespaceIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> new PlanElementId(""));
        assertThrows(IllegalArgumentException.class, () -> new PlanElementId("   "));
        assertThrows(IllegalArgumentException.class, () -> new PlanElementId("road 1"));
    }

    @Test
    void rejectsChildNameContainingPathSeparator() {
        PlanElementId parent = new PlanElementId("settlement/main");

        assertThrows(IllegalArgumentException.class, () -> parent.child("road/1"));
    }
}
