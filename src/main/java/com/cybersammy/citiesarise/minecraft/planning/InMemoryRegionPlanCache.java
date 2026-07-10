package com.cybersammy.citiesarise.minecraft.planning;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class InMemoryRegionPlanCache implements RegionPlanCache {
    public static final int DEFAULT_MAX_ENTRIES = 256;

    private final Map<RegionPlanCacheKey, SuburbDebugPlanResult> plans;
    private final int maxEntries;

    public InMemoryRegionPlanCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryRegionPlanCache(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }

        this.maxEntries = maxEntries;
        this.plans = new LinkedHashMap<>(maxEntries, 0.75f, true);
    }

    @Override
    public synchronized SuburbDebugPlanResult getOrCreate(
            RegionPlanCacheKey key,
            Supplier<SuburbDebugPlanResult> planFactory
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(planFactory, "planFactory");

        SuburbDebugPlanResult cachedPlan = plans.get(key);
        if (cachedPlan != null) {
            return cachedPlan;
        }

        SuburbDebugPlanResult createdPlan = createPlan(planFactory);
        plans.put(key, createdPlan);
        evictEldestPlan();
        return createdPlan;
    }

    public synchronized int size() {
        return plans.size();
    }

    @Override
    public synchronized void clear() {
        plans.clear();
    }

    private void evictEldestPlan() {
        if (plans.size() <= maxEntries) {
            return;
        }

        RegionPlanCacheKey eldestKey = plans.keySet().iterator().next();
        plans.remove(eldestKey);
    }

    private static SuburbDebugPlanResult createPlan(Supplier<SuburbDebugPlanResult> planFactory) {
        SuburbDebugPlanResult result = planFactory.get();

        return Objects.requireNonNull(result, "planFactory result");
    }
}
