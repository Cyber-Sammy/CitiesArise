package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;

/**
 * Supplies terrain from parallel-safe worldgen data and must never capture a live Minecraft level.
 */
@FunctionalInterface
public interface WorldgenTerrainSurveyProvider {
    TerrainSurvey sample(GridBounds bounds);
}
