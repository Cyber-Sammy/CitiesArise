package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.minecraft.cache.BoundedLruCache;
import java.util.function.Supplier;

public final class InMemoryRegionPlanCache implements RegionPlanCache {
    public static final int DEFAULT_MAX_ENTRIES = 256;

    private final BoundedLruCache<RegionPlanCacheKey, SuburbDebugPlanResult> plans;

    public InMemoryRegionPlanCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryRegionPlanCache(int maxEntries) {
        // Keep two neighboring large plans alongside a bounded set of rejected candidates.
        // Otherwise a single small rejection evicts one of those expensive plans and
        // custom structure managers can repeatedly alternate between rebuilding them.
        this.plans = new BoundedLruCache<>(maxEntries,(long)maxEntries+Math.max(1,maxEntries/8),
                result->result.successful() && CityPlanningArea.partitioned(result.surveyBounds().size())?Math.max(1,maxEntries/2):1);
    }

    @Override
    public SuburbDebugPlanResult getOrCreate(
            RegionPlanCacheKey key,
            Supplier<SuburbDebugPlanResult> planFactory
    ) {
        return plans.getOrCreate(key, planFactory);
    }

    public int size() {
        return plans.size();
    }

    @Override
    public java.util.Map<RegionPlanCacheKey, SuburbDebugPlanResult> snapshot() {
        return plans.snapshot();
    }

    @Override
    public void clear() {
        plans.clear();
    }
}
