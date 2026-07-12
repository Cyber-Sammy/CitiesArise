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
        this.plans = new BoundedLruCache<>(maxEntries);
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
    public void clear() {
        plans.clear();
    }
}
