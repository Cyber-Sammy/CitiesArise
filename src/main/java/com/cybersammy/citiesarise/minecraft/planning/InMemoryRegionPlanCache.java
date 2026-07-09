package com.cybersammy.citiesarise.minecraft.planning;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class InMemoryRegionPlanCache implements RegionPlanCache {
    private final Map<RegionPlanCacheKey, SuburbDebugPlanResult> plans = new ConcurrentHashMap<>();

    @Override
    public SuburbDebugPlanResult getOrCreate(
            RegionPlanCacheKey key,
            Supplier<SuburbDebugPlanResult> planFactory
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(planFactory, "planFactory");

        return plans.computeIfAbsent(key, ignoredKey -> createPlan(planFactory));
    }

    public int size() {
        return plans.size();
    }

    public void clear() {
        plans.clear();
    }

    private static SuburbDebugPlanResult createPlan(Supplier<SuburbDebugPlanResult> planFactory) {
        SuburbDebugPlanResult result = planFactory.get();

        return Objects.requireNonNull(result, "planFactory result");
    }
}
