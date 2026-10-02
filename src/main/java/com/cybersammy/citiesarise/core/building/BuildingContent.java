package com.cybersammy.citiesarise.core.building;

import java.util.*;
import com.cybersammy.citiesarise.core.content.*;

public record BuildingContent(BuildingAsset asset, Map<String,String> materials,
        Optional<CompositionSettings> composition, Optional<ResolvedComposition> resolved) {
    public BuildingContent {
        Objects.requireNonNull(asset); materials=Map.copyOf(materials); Objects.requireNonNull(composition); Objects.requireNonNull(resolved);
    }
    public BuildingContent resolved(ResolvedComposition plan) { return new BuildingContent(asset,materials,composition,Optional.of(plan)); }
}
