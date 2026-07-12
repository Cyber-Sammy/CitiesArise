package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WorldgenRegionSearchTest {
    private final WorldgenRegionSearch search = new WorldgenRegionSearch();

    @Test
    void returnsNearestAcceptedCandidate() {
        SettlementRegion nearest = new SettlementRegion(0, 0);
        SettlementRegion farther = new SettlementRegion(2, 0);

        WorldgenRegionSearch.Result result = search.findNearest(
                        0,
                        0,
                        4,
                        10,
                        region -> List.of(nearest, farther).contains(region),
                        region -> true
                )
                .orElseThrow();

        assertEquals(nearest, result.region());
        assertEquals(1, result.attemptedCandidates());
    }

    @Test
    void skipsRejectedCandidates() {
        SettlementRegion rejected = new SettlementRegion(0, 0);
        SettlementRegion accepted = new SettlementRegion(1, 0);

        WorldgenRegionSearch.Result result = search.findNearest(
                        0,
                        0,
                        4,
                        10,
                        region -> region.equals(rejected) || region.equals(accepted),
                        region -> region.equals(accepted)
                )
                .orElseThrow();

        assertEquals(accepted, result.region());
        assertEquals(2, result.attemptedCandidates());
    }

    @Test
    void stopsAtCandidateAttemptLimit() {
        assertTrue(search.findNearest(0, 0, 4, 1, region -> true, region -> false).isEmpty());
    }

    @Test
    void rejectsInvalidLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> search.findNearest(0, 0, 0, 1, region -> true, region -> true)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> search.findNearest(0, 0, 1, 0, region -> true, region -> true)
        );
    }
}
