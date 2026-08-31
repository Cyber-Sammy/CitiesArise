package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopology;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopologyAnalyzer;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DistrictFootprintTest {
    @Test
    void preservesAnInternalGapInsideOneConnectedRegion() {
        GridBounds bounds = bounds(0, 0, 7, 7);
        GridPoint preserved = new GridPoint(3, 3);
        DistrictFootprint footprint = DistrictFootprint.fromTopology(
                bounds,
                topology(bounds, point -> !point.equals(preserved))
        ).orElseThrow();

        assertEquals(48, footprint.area());
        assertEquals(1, footprint.preservedGapArea());
        assertFalse(footprint.contains(preserved));
        assertTrue(footprint.contains(bounds(0, 0, 3, 3)));
        assertFalse(footprint.contains(bounds(2, 2, 3, 3)));
    }

    @Test
    void selectsOneDeterministicComponentWhenBarrierSplitsEnvelope() {
        GridBounds bounds = bounds(0, 0, 7, 5);
        DistrictFootprint footprint = DistrictFootprint.fromTopology(
                bounds,
                topology(bounds, point -> point.x() != 3)
        ).orElseThrow();

        assertEquals(15, footprint.area());
        assertEquals(20, footprint.preservedGapArea());
        assertTrue(footprint.contains(new GridPoint(0, 0)));
        assertFalse(footprint.contains(new GridPoint(4, 0)));
        assertEquals(0, footprint.developableRegionId());
    }

    private static TerrainTopology topology(
            GridBounds bounds,
            java.util.function.Predicate<GridPoint> developable
    ) {
        TerrainSurvey survey = TerrainSurvey.sample(
                bounds,
                point -> Optional.of(new TerrainCell(
                        point,
                        64,
                        false,
                        0.0,
                        BiomeCategory.PLAINS,
                        developable.test(point) ? TerrainCategory.BUILDABLE : TerrainCategory.BLOCKED
                ))
        );
        return new TerrainTopologyAnalyzer().analyze(
                survey,
                cell -> cell.terrainCategory() == TerrainCategory.BUILDABLE
        );
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(new GridPoint(x, z), new GridSize(width, depth));
    }
}
