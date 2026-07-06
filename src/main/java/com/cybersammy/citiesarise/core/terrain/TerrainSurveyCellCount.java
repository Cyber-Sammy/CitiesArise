package com.cybersammy.citiesarise.core.terrain;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import java.util.Objects;

final class TerrainSurveyCellCount {
    private TerrainSurveyCellCount() {
    }

    static int expectedCellCount(GridBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");

        try {
            return Math.multiplyExact(bounds.size().width(), bounds.size().depth());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("terrain survey bounds contain too many cells", exception);
        }
    }
}
