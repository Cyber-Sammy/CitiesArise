package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementIndex;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementChunkProjector;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class WorldgenPlacementIndexCacheTest {
    @Test
    void reusesIndexForSamePlanInstance() {
        WorldgenPlacementIndexCache cache = new WorldgenPlacementIndexCache(2);
        SettlementPlan plan = plan();
        AtomicInteger calls = new AtomicInteger();

        DebugChunkPlacementIndex first = cache.getOrCreate(plan, () -> index(calls));
        DebugChunkPlacementIndex second = cache.getOrCreate(plan, () -> index(calls));

        assertSame(first, second);
        assertEquals(1, calls.get());
    }

    @Test
    void keepsEqualButDistinctPlanInstancesSeparate() {
        WorldgenPlacementIndexCache cache = new WorldgenPlacementIndexCache(2);
        AtomicInteger calls = new AtomicInteger();

        DebugChunkPlacementIndex first = cache.getOrCreate(plan(), () -> index(calls));
        DebugChunkPlacementIndex second = cache.getOrCreate(plan(), () -> index(calls));

        assertNotSame(first, second);
        assertEquals(2, calls.get());
        assertEquals(2, cache.size());
    }

    @Test
    void clearsIndices() {
        WorldgenPlacementIndexCache cache = new WorldgenPlacementIndexCache(1);
        SettlementPlan plan = plan();
        AtomicInteger calls = new AtomicInteger();

        DebugChunkPlacementIndex first = cache.getOrCreate(plan, () -> index(calls));
        cache.clear();
        DebugChunkPlacementIndex second = cache.getOrCreate(plan, () -> index(calls));

        assertNotSame(first, second);
        assertEquals(2, calls.get());
    }

    private static SettlementPlan plan() {
        return new SettlementPlan(
                new PlanElementId("cities_arise:test"),
                RoadGraph.empty(),
                List.of(),
                List.of(),
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static DebugChunkPlacementIndex index(AtomicInteger calls) {
        calls.incrementAndGet();
        return new DebugPlacementChunkProjector().partition(new DebugPlacementPlan(List.of()));
    }
}
