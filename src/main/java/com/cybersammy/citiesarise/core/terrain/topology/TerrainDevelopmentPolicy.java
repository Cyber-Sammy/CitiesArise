package com.cybersammy.citiesarise.core.terrain.topology;

import com.cybersammy.citiesarise.core.terrain.TerrainCell;

@FunctionalInterface
public interface TerrainDevelopmentPolicy {
    boolean isDevelopable(TerrainCell cell);
}
