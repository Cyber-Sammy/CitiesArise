package com.cybersammy.citiesarise.core.building;

import java.util.*;
import com.cybersammy.citiesarise.core.content.*;

public record BuildingContent(BuildingAsset asset, Map<String,String> materials,
        Optional<CompositionSettings> composition, Optional<ResolvedComposition> resolved,
        Optional<EntranceSettings> entrances,List<ResolvedEntrance> resolvedEntrances) {
    public BuildingContent(BuildingAsset asset,Map<String,String> materials,Optional<CompositionSettings> composition,Optional<ResolvedComposition> resolved) {
        this(asset,materials,composition,resolved,Optional.empty(),List.of());
    }
    public BuildingContent {
        Objects.requireNonNull(asset); materials=Map.copyOf(materials); Objects.requireNonNull(composition); Objects.requireNonNull(resolved);
        entrances=entrances==null?Optional.empty():entrances;resolvedEntrances=resolvedEntrances==null?List.of():List.copyOf(resolvedEntrances);
    }
    public BuildingContent resolved(ResolvedComposition plan) { return new BuildingContent(asset,materials,composition,Optional.of(plan),entrances,resolvedEntrances); }
    public BuildingContent withEntrances(EntranceSettings settings) {return new BuildingContent(asset,materials,composition,resolved,Optional.ofNullable(settings),resolvedEntrances);}
    public BuildingContent withResolvedEntrances(ResolvedComposition building,List<ResolvedEntrance> entries) {
        return new BuildingContent(asset,materials,composition,Optional.of(building),entrances,entries);
    }
}
