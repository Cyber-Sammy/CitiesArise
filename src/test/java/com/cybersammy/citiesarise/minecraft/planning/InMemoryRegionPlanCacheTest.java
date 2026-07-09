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
