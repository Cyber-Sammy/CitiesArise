package com.cybersammy.citiesarise.core.terrain.scoring;

import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;

final class TerrainCategorySuitabilityRule implements TerrainSuitabilityRule {
    @Override
    public String name() {
        return "terrain_category";
    }

    @Override
    public TerrainSuitabilityContribution evaluate(TerrainCell cell, TerrainSuitabilityContext context) {
        if (cell.terrainCategory() == TerrainCategory.BLOCKED) {
            return TerrainSuitabilityContribution.rejection(TerrainRejectionReason.BLOCKED_TERRAIN);
        }

        if (cell.terrainCategory() == TerrainCategory.ROUGH) {
            return TerrainSuitabilityContribution.multiplier(0.5);
        }

        return TerrainSuitabilityContribution.multiplier(1.0);
    }
}
