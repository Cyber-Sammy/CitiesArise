package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopology;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopologyAnalyzer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class AdaptiveSuburbLayoutSelectorTest {
    @Test
    void doesNotApplyBuildingShoulderRadiusToWholeParcel() {
        GridBounds surveyBounds = bounds(0, 0, 20, 20);
        TerrainSurvey survey = TerrainSurvey.sample(
                surveyBounds,
                point -> Optional.of(cell(point, point.equals(new GridPoint(4, 4))))
        );
        TerrainTopology topology = new TerrainTopologyAnalyzer().analyze(
                survey,
                cell -> cell.terrainCategory() != TerrainCategory.BLOCKED
        );
        GridBounds parcelBounds = bounds(5, 5, 10, 10);
        SuburbLayout preferredLayout = new SuburbLayout(
                surveyBounds,
                10,
                List.of(),
                List.of(parcelBounds),
                List.of(parcelBounds),
                List.of(
                        new PotentialTerrainPreparationFootprint(parcelBounds, 0),
                        new PotentialTerrainPreparationFootprint(bounds(8, 8, 4, 4), 3)
                )
        );

        SuburbLayout selected = new AdaptiveSuburbLayoutSelector().select(
                surveyBounds,
                1,
                new GridSize(10, 10),
                topology,
                preferredLayout,
                ignored -> preferredLayout
        );

        assertSame(preferredLayout, selected);
    }

    private static TerrainCell cell(GridPoint point, boolean blocked) {
        return new TerrainCell(
                point,
                64,
                false,
                0.0,
                BiomeCategory.PLAINS,
                blocked ? TerrainCategory.BLOCKED : TerrainCategory.BUILDABLE
        );
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(new GridPoint(x, z), new GridSize(width, depth));
    }
}
