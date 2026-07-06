package com.cybersammy.citiesarise.core.terrain;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.HashMap;
import java.util.Map;

final class TerrainCellIndex {
    private final Map<GridPoint, TerrainCell> cellsByPoint = new HashMap<>();

    void add(TerrainCell cell) {
        TerrainCell previous = cellsByPoint.putIfAbsent(cell.point(), cell);

        if (previous != null) {
            throw new IllegalArgumentException("duplicate terrain cell point: " + cell.point());
        }
    }

    Map<GridPoint, TerrainCell> cellsByPoint() {
        return cellsByPoint;
    }
}
