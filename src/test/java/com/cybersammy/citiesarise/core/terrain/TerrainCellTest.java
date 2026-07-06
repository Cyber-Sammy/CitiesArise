package com.cybersammy.citiesarise.core.terrain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

final class TerrainCellTest {
    @Test
    void rejectsNegativeSlope() {
        assertThrows(IllegalArgumentException.class, () -> cell(-0.1));
    }

    @Test
    void rejectsNonFiniteSlope() {
        assertThrows(IllegalArgumentException.class, () -> cell(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> cell(Double.POSITIVE_INFINITY));
    }

    private static TerrainCell cell(double slope) {
        return new TerrainCell(
                new GridPoint(0, 0),
                64,
                false,
                slope,
                BiomeCategory.PLAINS,
                TerrainCategory.BUILDABLE
        );
    }
}
