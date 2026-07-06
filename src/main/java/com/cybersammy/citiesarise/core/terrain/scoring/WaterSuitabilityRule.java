package com.cybersammy.citiesarise.core.terrain.scoring;

import com.cybersammy.citiesarise.core.terrain.TerrainCell;

final class WaterSuitabilityRule implements TerrainSuitabilityRule {
    @Override
    public String name() {
        return "water";
    }

    @Override
    public TerrainSuitabilityContribution evaluate(TerrainCell cell, TerrainSuitabilityContext context) {
        if (cell.water()) {
            return TerrainSuitabilityContribution.rejection(TerrainRejectionReason.WATER);
        }

        return TerrainSuitabilityContribution.multiplier(1.0);
    }
}
