package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PotentialRegionSearchTest {
    @Test void scansBoundedSquareOnceIncludingNegativeCoordinates() {
        var visited = new HashSet<SettlementRegion>();
        var origin = new SettlementRegion(-5, -9);
        assertTrue(PotentialRegionSearch.find(origin, 3, region -> {
            assertTrue(visited.add(region));
            assertTrue(Math.abs(region.x() - origin.x()) <= 3);
            assertTrue(Math.abs(region.z() - origin.z()) <= 3);
            return false;
        }).isEmpty());
        assertEquals(49, visited.size());
    }

    @Test void stopsWithoutEvaluatingFurtherRegions() {
        var calls = new AtomicInteger();
        var origin = new SettlementRegion(0, 0);
        assertEquals(origin, PotentialRegionSearch.find(origin, 512, region -> {
            calls.incrementAndGet(); return true;
        }).orElseThrow());
        assertEquals(1, calls.get());
        var target = new SettlementRegion(2, -1);
        assertEquals(target, PotentialRegionSearch.find(origin, 2, target::equals).orElseThrow());
        assertTrue(PotentialRegionSearch.find(origin, 1, target::equals).isEmpty());
    }
}
