package com.cybersammy.citiesarise.minecraft.planning;

import java.util.function.Supplier;

public interface RegionPlanCache {
    SuburbDebugPlanResult getOrCreate(RegionPlanCacheKey key, Supplier<SuburbDebugPlanResult> planFactory);
}
