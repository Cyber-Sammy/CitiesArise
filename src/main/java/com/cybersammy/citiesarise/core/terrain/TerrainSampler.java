package com.cybersammy.citiesarise.core.terrain;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.Optional;

@FunctionalInterface
public interface TerrainSampler {
    Optional<TerrainCell> sample(GridPoint point);
}
