package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumn;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import java.util.Optional;
import java.util.Set;

/**
 * Supplies terrain from parallel-safe worldgen data and must never capture a live Minecraft level.
 */
@FunctionalInterface
public interface WorldgenTerrainSurveyProvider {
    TerrainSurvey sample(GridBounds bounds);

    /** Checks the final cut/fill contact, before accepting any placement or structure start. */
    default Optional<TerrainPreparationColumn> unsupportedColumn(TerrainPreparationPlan plan) {
        return Optional.empty();
    }

    /**
     * Refines selected columns when supported. The Minecraft provider returns exact heights as well as water data.
     */
    default Optional<TerrainSurvey> sampleWithExactWaterMask(
            GridBounds bounds,
            Set<GridPoint> waterCheckPoints
    ) {
        return Optional.empty();
    }
}
