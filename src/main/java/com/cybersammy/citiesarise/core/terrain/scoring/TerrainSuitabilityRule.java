package com.cybersammy.citiesarise.core.terrain.scoring;

import com.cybersammy.citiesarise.core.terrain.TerrainCell;

public interface TerrainSuitabilityRule {
    String name();

    TerrainSuitabilityContribution evaluate(TerrainCell cell, TerrainSuitabilityContext context);
}
