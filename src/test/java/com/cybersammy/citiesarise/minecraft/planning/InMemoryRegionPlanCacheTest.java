package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningFailureReason;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class InMemoryRegionPlanCacheTest {
    @Test void speculativeAndNativeRequestsShareOneCompletedOutcome() throws Exception {
        var cache=new InMemoryRegionPlanCache();var calls=new AtomicInteger();
        var key=key("minecraft:overworld","cities_arise:suburb",SuburbPlanningSettings.defaults());
        var began=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var prefetched=new java.util.concurrent.CompletableFuture<SuburbDebugPlanResult>();
        try(var queue=new PlanningAheadQueue<RegionPlanCacheKey>(1);var nativeWorker=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            queue.offer(key,()->prefetched.complete(cache.getOrCreate(key,()->{
                began.countDown();try{release.await();}catch(InterruptedException e){throw new AssertionError(e);}return result(88,calls);
            })));
            org.junit.jupiter.api.Assertions.assertTrue(began.await(2,java.util.concurrent.TimeUnit.SECONDS));
            var nativeResult=nativeWorker.submit(()->cache.getOrCreate(key,()->result(99,calls)));
            release.countDown();assertSame(prefetched.get(2,java.util.concurrent.TimeUnit.SECONDS),nativeResult.get(2,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,calls.get());assertEquals(88,nativeResult.get().seed());
        }finally{release.countDown();}
    }
    @Test
    void locateHintsMatchEveryWorldgenInputAndDisappearOnClear() {
        var cache = new InMemoryRegionPlanCache();
        var settings = SuburbPlanningSettings.defaults();
        var profile = new SettlementProfileId("cities_arise:suburb");
        var size = new GridSize(540,540);
        var policy = com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy.defaults();
        var context = new WorldgenPlanningContext("minecraft:overworld",1234,profile,size,settings,policy,
                bounds->{throw new AssertionError("Cached hints must not query terrain");},false,false);
        var region = new SettlementRegion(3,3);
        var valid = new RegionPlanCacheKey(context.dimensionId(),region,context.worldSeed(),TerrainSurveySource.WORLDGEN_BASE,
                profile,size,settings,policy);
        var calls = new AtomicInteger();
        var accepted = cache.getOrCreate(valid,()->largeResult(1,calls));
        assertSame(accepted,cache.completedWorldgenPlans(context).get(region));
        var snapshot = cache.snapshot();
        assertThrows(UnsupportedOperationException.class,snapshot::clear);
        cache.clear();
        assertEquals(1,snapshot.size());
        assertEquals(java.util.Map.of(),cache.completedWorldgenPlans(context));
        var wrongKeys = java.util.List.of(
                new RegionPlanCacheKey("minecraft:the_nether",region,1234,TerrainSurveySource.WORLDGEN_BASE,profile,size,settings,policy),
                new RegionPlanCacheKey(context.dimensionId(),region,4321,TerrainSurveySource.WORLDGEN_BASE,profile,size,settings,policy),
                new RegionPlanCacheKey(context.dimensionId(),region,1234,TerrainSurveySource.LOADED_WORLD,profile,size,settings,policy),
                new RegionPlanCacheKey(context.dimensionId(),region,1234,TerrainSurveySource.WORLDGEN_BASE,new SettlementProfileId("test:other"),size,settings,policy),
                new RegionPlanCacheKey(context.dimensionId(),region,1234,TerrainSurveySource.WORLDGEN_BASE,profile,new GridSize(120,72),settings,policy),
                new RegionPlanCacheKey(context.dimensionId(),region,1234,TerrainSurveySource.WORLDGEN_BASE,profile,size,new SuburbPlanningSettings(5,1.0,8,18,20,4),policy),
                new RegionPlanCacheKey(context.dimensionId(),region,1234,TerrainSurveySource.WORLDGEN_BASE,profile,size,settings,
                        new com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy(policy.responses(),policy.capabilities(),
                                com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationSettings.defaults())));
        for (var wrong : wrongKeys) {
            cache.clear();cache.getOrCreate(wrong,()->largeResult(1,calls));
            assertEquals(java.util.Map.of(),cache.completedWorldgenPlans(context),wrong.toString());
        }
        cache.clear();cache.getOrCreate(valid,()->rejectedResult(calls));
        assertEquals(java.util.Map.of(),cache.completedWorldgenPlans(context));
    }

    @Test
    void retainsTwoNeighboringLargePlansAlongsideRejectedCandidates() {
        var cache=new InMemoryRegionPlanCache();var calls=new AtomicInteger();
        var settings=SuburbPlanningSettings.defaults();
        var first=key("minecraft:overworld","cities_arise:first",settings);
        var second=key("minecraft:overworld","cities_arise:second",settings);
        var a=cache.getOrCreate(first,()->largeResult(1,calls));
        for(int i=0;i<32;i++) {
            var rejected=key("minecraft:overworld","cities_arise:rejected_"+i,settings);
            cache.getOrCreate(rejected,()->rejectedResult(calls));
        }
        var b=cache.getOrCreate(second,()->largeResult(2,calls));
        assertSame(a,cache.getOrCreate(first,()->largeResult(3,calls)));
        assertSame(b,cache.getOrCreate(second,()->largeResult(4,calls)));
        assertEquals(34,calls.get(),"Local rejection checks evicted a neighboring large city");
        var third=key("minecraft:overworld","cities_arise:third",settings);
        cache.getOrCreate(third,()->largeResult(5,calls));
        cache.getOrCreate(first,()->largeResult(6,calls));
        assertEquals(36,calls.get(),"The large-plan memory budget became unbounded");
    }

    private static SuburbDebugPlanResult largeResult(long seed,AtomicInteger calls) {
        calls.incrementAndGet();
        var plan=new com.cybersammy.citiesarise.core.model.SettlementPlan(
                new com.cybersammy.citiesarise.core.model.PlanElementId("test:cache"),
                new com.cybersammy.citiesarise.core.model.RoadGraph(java.util.List.of(),java.util.List.of(),java.util.List.of()),
                java.util.List.of(),java.util.List.of(),java.util.Set.of(),com.cybersammy.citiesarise.core.model.PlanProperties.empty());
        return new SuburbDebugPlanResult(new SettlementRegion(3,3),
                new GridBounds(new GridPoint(122,122),new GridSize(540,540)),seed,true,plan,null,null);
    }

    @Test
    void reusesPlanForSameKey() {
        InMemoryRegionPlanCache cache = new InMemoryRegionPlanCache();
        AtomicInteger calls = new AtomicInteger();
        RegionPlanCacheKey key = key("minecraft:overworld", "cities_arise:suburb", SuburbPlanningSettings.defaults());

        SuburbDebugPlanResult first = cache.getOrCreate(key, () -> result(1L, calls));
        SuburbDebugPlanResult second = cache.getOrCreate(key, () -> result(2L, calls));

        assertSame(first, second);
        assertEquals(1, calls.get());
        assertEquals(1, cache.size());
    }

    @Test
    void keepsDifferentProfilesSeparate() {
        InMemoryRegionPlanCache cache = new InMemoryRegionPlanCache();
        AtomicInteger calls = new AtomicInteger();
        SuburbPlanningSettings settings = SuburbPlanningSettings.defaults();

        SuburbDebugPlanResult first = cache.getOrCreate(
                key("minecraft:overworld", "cities_arise:suburb", settings),
                () -> result(1L, calls)
        );
        SuburbDebugPlanResult second = cache.getOrCreate(
                key("minecraft:overworld", "cities_arise:large_suburb", settings),
                () -> result(2L, calls)
        );

        assertEquals(1L, first.seed());
        assertEquals(2L, second.seed());
        assertEquals(2, calls.get());
        assertEquals(2, cache.size());
    }

    @Test
    void keepsDifferentPlanningSettingsSeparate() {
        InMemoryRegionPlanCache cache = new InMemoryRegionPlanCache();
        AtomicInteger calls = new AtomicInteger();

        SuburbDebugPlanResult first = cache.getOrCreate(
                key("minecraft:overworld", "cities_arise:suburb", SuburbPlanningSettings.defaults()),
                () -> result(1L, calls)
        );
        SuburbDebugPlanResult second = cache.getOrCreate(
                key("minecraft:overworld", "cities_arise:suburb", new SuburbPlanningSettings(5, 1.0, 8, 18, 20, 4)),
                () -> result(2L, calls)
        );

        assertEquals(1L, first.seed());
        assertEquals(2L, second.seed());
        assertEquals(2, calls.get());
        assertEquals(2, cache.size());
    }

    @Test
    void cachesRejectedResults() {
        InMemoryRegionPlanCache cache = new InMemoryRegionPlanCache();
        AtomicInteger calls = new AtomicInteger();
        RegionPlanCacheKey key = key("minecraft:overworld", "cities_arise:suburb", SuburbPlanningSettings.defaults());

        SuburbDebugPlanResult first = cache.getOrCreate(key, () -> rejectedResult(calls));
        SuburbDebugPlanResult second = cache.getOrCreate(key, () -> result(2L, calls));

        assertSame(first, second);
        assertEquals(SuburbPlanningFailureReason.SURVEY_TOO_SMALL, second.failureReason());
        assertEquals(1, calls.get());
    }

    @Test
    void evictsLeastRecentlyUsedPlanAtCapacity() {
        InMemoryRegionPlanCache cache = new InMemoryRegionPlanCache(2);
        AtomicInteger calls = new AtomicInteger();
        RegionPlanCacheKey firstKey = key("minecraft:overworld", "cities_arise:first", SuburbPlanningSettings.defaults());
        RegionPlanCacheKey secondKey = key("minecraft:overworld", "cities_arise:second", SuburbPlanningSettings.defaults());
        RegionPlanCacheKey thirdKey = key("minecraft:overworld", "cities_arise:third", SuburbPlanningSettings.defaults());

        SuburbDebugPlanResult first = cache.getOrCreate(firstKey, () -> result(1L, calls));
        cache.getOrCreate(secondKey, () -> result(2L, calls));
        assertSame(first, cache.getOrCreate(firstKey, () -> result(3L, calls)));

        cache.getOrCreate(thirdKey, () -> result(4L, calls));
        SuburbDebugPlanResult recreatedSecond = cache.getOrCreate(secondKey, () -> result(5L, calls));

        assertEquals(5L, recreatedSecond.seed());
        assertEquals(4, calls.get());
        assertEquals(2, cache.size());
    }

    @Test
    void clearsCachedPlans() {
        InMemoryRegionPlanCache cache = new InMemoryRegionPlanCache();
        AtomicInteger calls = new AtomicInteger();
        RegionPlanCacheKey key = key("minecraft:overworld", "cities_arise:suburb", SuburbPlanningSettings.defaults());

        cache.getOrCreate(key, () -> result(1L, calls));
        cache.clear();
        SuburbDebugPlanResult recreated = cache.getOrCreate(key, () -> result(2L, calls));

        assertEquals(2L, recreated.seed());
        assertEquals(2, calls.get());
        assertEquals(1, cache.size());
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new InMemoryRegionPlanCache(0));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryRegionPlanCache(-1));
    }

    @Test
    void rejectsNullKey() {
        InMemoryRegionPlanCache cache = new InMemoryRegionPlanCache();

        assertThrows(NullPointerException.class, () -> cache.getOrCreate(null, () -> result(1L, new AtomicInteger())));
    }

    @Test
    void rejectsNullFactory() {
        InMemoryRegionPlanCache cache = new InMemoryRegionPlanCache();
        RegionPlanCacheKey key = key("minecraft:overworld", "cities_arise:suburb", SuburbPlanningSettings.defaults());

        assertThrows(NullPointerException.class, () -> cache.getOrCreate(key, null));
    }

    @Test
    void rejectsNullFactoryResult() {
        InMemoryRegionPlanCache cache = new InMemoryRegionPlanCache();
        RegionPlanCacheKey key = key("minecraft:overworld", "cities_arise:suburb", SuburbPlanningSettings.defaults());

        assertThrows(NullPointerException.class, () -> cache.getOrCreate(key, () -> null));
    }

    private static RegionPlanCacheKey key(
            String dimensionId,
            String profileId,
            SuburbPlanningSettings settings
    ) {
        return new RegionPlanCacheKey(
                dimensionId,
                new SettlementRegion(2, -3),
                1234L,
                TerrainSurveySource.LOADED_WORLD,
                new SettlementProfileId(profileId),
                new GridSize(120, 72),
                settings
        );
    }

    private static SuburbDebugPlanResult result(long seed, AtomicInteger calls) {
        calls.incrementAndGet();
        return new SuburbDebugPlanResult(
                new SettlementRegion(2, -3),
                new GridBounds(new GridPoint(10, 20), new GridSize(120, 72)),
                seed,
                false,
                null,
                SuburbPlanningFailureReason.NOT_ENOUGH_PARCEL_SPACE,
                null
        );
    }

    private static SuburbDebugPlanResult rejectedResult(AtomicInteger calls) {
        calls.incrementAndGet();
        return new SuburbDebugPlanResult(
                new SettlementRegion(2, -3),
                new GridBounds(new GridPoint(10, 20), new GridSize(120, 72)),
                1L,
                false,
                null,
                SuburbPlanningFailureReason.SURVEY_TOO_SMALL,
                null
        );
    }
}
