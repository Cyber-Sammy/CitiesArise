package com.cybersammy.citiesarise.minecraft.planning;

import java.util.function.Supplier;

public interface RegionPlanCache {
    SuburbDebugPlanResult getOrCreate(RegionPlanCacheKey key, Supplier<SuburbDebugPlanResult> planFactory);

    default java.util.Map<RegionPlanCacheKey, SuburbDebugPlanResult> snapshot() {
        return java.util.Map.of();
    }

    default java.util.Map<SettlementRegion, SuburbDebugPlanResult> completedWorldgenPlans(WorldgenPlanningContext context) {
        var matches = new java.util.HashMap<SettlementRegion, SuburbDebugPlanResult>();
        snapshot().forEach((key, result) -> {
            var expected = new RegionPlanCacheKey(context.dimensionId(), key.region(), context.worldSeed(),
                    TerrainSurveySource.WORLDGEN_BASE, context.profileId(), context.surveySize(),
                    context.planningSettings(), context.terrainResponsePolicy());
            if (key.equals(expected) && result.successful()) matches.put(key.region(), result);
        });
        return java.util.Map.copyOf(matches);
    }

    void clear();
}
