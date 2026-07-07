package com.cybersammy.citiesarise.minecraft.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class MinecraftSlopeNormalizerTest {
    @Test
    void mapsSingleBlockDeltaToDefaultCoreSlopeLimit() {
        assertEquals(0.25, MinecraftSlopeNormalizer.fromHeightDelta(1));
    }

    @Test
    void mapsFlatTerrainToZeroSlope() {
        assertEquals(0.0, MinecraftSlopeNormalizer.fromHeightDelta(0));
    }

    @Test
    void rejectsNegativeHeightDelta() {
        assertThrows(IllegalArgumentException.class, () -> MinecraftSlopeNormalizer.fromHeightDelta(-1));
    }
}
