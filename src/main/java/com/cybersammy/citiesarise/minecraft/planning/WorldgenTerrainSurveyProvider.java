package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.Optional;
import java.util.Set;

/**
 * Supplies terrain from parallel-safe worldgen data and must never capture a live Minecraft level.
 */
@FunctionalInterface
public interface WorldgenTerrainSurveyProvider {
    TerrainSurvey sample(GridBounds bounds);

    /**
     * Returns a survey with exact binary water data for selected columns when the provider supports refinement.
     */
    default Optional<TerrainSurvey> sampleWithExactWaterMask(
            GridBounds bounds,
            Set<GridPoint> waterCheckPoints
    ) {
        return Optional.empty();
    }
}
