package com.cybersammy.citiesarise.core.earthwork;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class TerrainTransitionSettingsTest {
    @Test
    void legacyDefaultsDoNotOptIntoRetainingWalls() {
        assertFalse(TerrainTransitionSettings.defaults().retainingWalls());
    }

    @Test
    void rejectsUnboundedTransitionGeometry() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainTransitionSettings(0, 2, 2, 3, 3, 3, 3, false, 2)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainTransitionSettings(1, 9, 2, 3, 3, 3, 3, false, 2)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainTransitionSettings(1, 2, 4, 3, 3, 3, 3, false, 2)
        );
    }
}
