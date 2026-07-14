package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
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
                ).result()
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
                ).result()
                .orElseThrow();

        assertEquals(accepted, result.region());
        assertEquals(2, result.attemptedCandidates());
    }

    @Test
    void stopsAtCandidateAttemptLimit() {
        WorldgenRegionSearch.Outcome outcome = search.findNearest(
                0,
                0,
                4,
                1,
                region -> true,
                region -> false
        );

        assertTrue(outcome.result().isEmpty());
        assertEquals(1, outcome.attemptedCandidates());
    }

    @Test
    void schedulesSearchWithoutRunningItOnTheCallingThread() {
        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        var future = search.findNearestAsync(
                0,
                0,
                1,
                1,
                region -> true,
                region -> true,
                scheduledTask::set
        );

        assertFalse(future.isDone());
        scheduledTask.get().run();

        assertTrue(future.join().result().isPresent());
    }

    @Test
    void stopsWhenWorkerIsInterrupted() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(
                    CancellationException.class,
                    () -> search.findNearest(0, 0, 1, 1, region -> true, region -> true)
            );
        } finally {
            Thread.interrupted();
        }
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
