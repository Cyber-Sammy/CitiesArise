package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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
import java.time.Duration;
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
        assertEquals(1, footprint.excludedArea());
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
        assertEquals(20, footprint.excludedArea());
        assertTrue(footprint.contains(new GridPoint(0, 0)));
        assertFalse(footprint.contains(new GridPoint(4, 0)));
        assertEquals(0, footprint.developableRegionId());
    }

    @Test
    void ranksThousandsOfSlidingCandidatesWithinTheCheapSearchBudget() {
        GridBounds surveyBounds = bounds(0, 0, 120, 72);
        TerrainTopology topology = topology(
                surveyBounds,
                point -> point.x() % 29 != 0 && (point.z() % 23 != 0 || point.x() % 7 != 0)
        );
        DistrictFootprint.RegionMap regionMap = DistrictFootprint.RegionMap.from(topology);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            int selections = 0;
            for (int z = 0; z <= 42; z++) {
                for (int x = 0; x <= 80; x++) {
                    DistrictFootprint.ComponentSelection selection = DistrictFootprint.selectionFromRegionMap(
                            bounds(x, z, 40, 30),
                            regionMap
                    ).orElseThrow();
                    assertTrue(selection.area() > 0);
                    selections++;
                }
            }
            assertEquals(3_483, selections);
        });
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
