package com.cybersammy.citiesarise.core.terrain.policy;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TerrainAdaptationPlan {
    private final TerrainResponsePolicy policy;
    private final List<TerrainFeatureRegion> features;
    private final Map<GridPoint, TerrainFeatureRegion> featuresByPoint;

    public TerrainAdaptationPlan(
            TerrainResponsePolicy policy,
            List<TerrainFeatureRegion> features
    ) {
        this.policy = Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(features, "features");
        this.features = List.copyOf(features);
        this.featuresByPoint = indexFeatures(this.features);
    }

    public static TerrainAdaptationPlan empty(TerrainResponsePolicy policy) {
        return new TerrainAdaptationPlan(policy, List.of());
    }

    public List<TerrainFeatureRegion> features() {
        return features;
    }

    public TerrainPlanningAction actionAt(GridPoint point, TerrainFeatureType type) {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(type, "type");
        TerrainFeatureRegion feature = featuresByPoint.get(point);
        if (feature != null && feature.type() == type) {
            return feature.action();
        }
        return policy.actionFor(type);
    }

    public boolean permitsCurrentPlacement(GridPoint point, TerrainFeatureType type) {
        return actionAt(point, type).permitsCurrentPlacement();
    }

    public TerrainAdaptationPlan withPolicy(TerrainResponsePolicy replacement) {
        Objects.requireNonNull(replacement, "replacement");
        List<TerrainFeatureRegion> updated = features.stream()
                .map(feature -> new TerrainFeatureRegion(
                        feature.id(),
                        feature.type(),
                        feature.points(),
                        feature.bounds(),
                        feature.metrics(),
                        TerrainAdaptationPlanner.resolveAction(
                                replacement,
                                feature.type(),
                                feature.metrics()
                        )
                ))
                .toList();
        return new TerrainAdaptationPlan(replacement, updated);
    }

    private static Map<GridPoint, TerrainFeatureRegion> indexFeatures(
            List<TerrainFeatureRegion> features
    ) {
        Map<GridPoint, TerrainFeatureRegion> index = new HashMap<>();
        for (TerrainFeatureRegion feature : features) {
            for (GridPoint point : feature.points()) {
                TerrainFeatureRegion previous = index.put(point, feature);
                if (previous != null) {
                    throw new IllegalArgumentException("terrain features must not overlap at " + point);
                }
            }
        }
        return Map.copyOf(index);
    }
}
