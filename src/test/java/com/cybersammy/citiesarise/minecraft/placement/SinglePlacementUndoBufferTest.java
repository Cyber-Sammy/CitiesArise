package com.cybersammy.citiesarise.minecraft.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SinglePlacementUndoBufferTest {
    @Test
    void keepsOriginalValueForDuplicateKeys() {
        SinglePlacementUndoBuffer<String, String> buffer = new SinglePlacementUndoBuffer<>();

        buffer.capture("1,64,2", "grass");
        buffer.capture("1,64,2", "stone_bricks");

        assertEquals(1, buffer.size());
        assertEquals("grass", buffer.entries().getFirst().value());
    }

    @Test
    void rejectsNullKeysAndValues() {
        SinglePlacementUndoBuffer<String, String> buffer = new SinglePlacementUndoBuffer<>();

        assertThrows(NullPointerException.class, () -> buffer.capture(null, "grass"));
        assertThrows(NullPointerException.class, () -> buffer.capture("1,64,2", null));
    }
}
