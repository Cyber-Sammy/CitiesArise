package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TiledHeightCacheTest {
    @Test void retainsAFullCityAcrossRepeatedSurveysIncludingNegativeCoordinates() {
        var calls = new AtomicInteger();
        var cache = new TiledHeightCache(p -> {
            calls.incrementAndGet();
            return new TiledHeightCache.Heights(p.x(), p.z());
        });
        for (int pass = 0; pass < 2; pass++) {
            for (int z = -270; z < 270; z++) for (int x = -270; x < 270; x++) {
                var heights = cache.get(new GridPoint(x, z));
                assertEquals(x, heights.surface());
                assertEquals(z, heights.support());
            }
        }
        assertEquals(540 * 540, calls.get());
    }

    @Test void evictsWholeTilesAndDoesNotShareAnswersBetweenProviders() {
        var calls = new AtomicInteger();
        var cache = new TiledHeightCache(p -> new TiledHeightCache.Heights(calls.incrementAndGet(), 7), 2);
        assertEquals(1, cache.get(new GridPoint(-1, -1)).surface());
        assertEquals(2, cache.get(new GridPoint(0, 0)).surface());
        assertEquals(3, cache.get(new GridPoint(16, 0)).surface());
        assertEquals(2, cache.get(new GridPoint(0, 0)).surface());
        assertEquals(4, cache.get(new GridPoint(-1, -1)).surface());
        var other = new TiledHeightCache(p -> new TiledHeightCache.Heights(123, -6));
        assertEquals(123, other.get(new GridPoint(-1, -1)).surface());
    }

    @Test void failedSamplingCanBeRetriedWithoutCachingPartialAnswers() {
        var calls = new AtomicInteger();
        var cache = new TiledHeightCache(p -> {
            if (calls.incrementAndGet() == 1) throw new IllegalArgumentException("test failure");
            return new TiledHeightCache.Heights(-64, -64);
        });
        var point = new GridPoint(-16, 16);
        assertThrows(IllegalArgumentException.class, () -> cache.get(point));
        assertEquals(new TiledHeightCache.Heights(-64, -64), cache.get(point));
        assertEquals(2, calls.get());
    }
}
