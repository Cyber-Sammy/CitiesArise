package com.cybersammy.citiesarise.minecraft.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class WorldgenRegionSearchTest {
    private final WorldgenRegionSearch search = new WorldgenRegionSearch();

    @Test
    void nextSkipsSavedAndRejectedRegionsWithoutSpendingTheAttemptBudget() {
        var visited=new java.util.HashSet<SettlementRegion>();
        var accepted=new java.util.ArrayList<SettlementRegion>();
        for(int pass=0;pass<3;pass++) {
            var snapshot=java.util.Set.copyOf(visited);
            var outcome=search.findBest(0,0,2,2,0,region->!snapshot.contains(region),region->{
                assertTrue(visited.add(region),"Repeated checked region");
                return visited.size()%2==0?Optional.of(1):Optional.empty();
            },Integer::compare);
            assertEquals(2,outcome.attemptedCandidates());
            accepted.add(outcome.result().orElseThrow().region());
        }
        assertEquals(3,accepted.stream().distinct().count());
        var exhausted=search.findBest(0,0,1,2,0,region->false,region->{throw new AssertionError("Excluded region evaluated");},Integer::compare);
        assertEquals(0,exhausted.attemptedCandidates());assertTrue(exhausted.result().isEmpty());
    }

    @Test
    void cancellationDuringLastCandidateDoesNotPublishSuccess() {
        try {
            assertThrows(CancellationException.class, () -> search.findBest(0, 0, 1, 1, 0,
                    region -> true, region -> {
                        Thread.currentThread().interrupt();
                        return Optional.of(1);
                    }, Integer::compare));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void returnsBestAcceptedCandidateWithinAttemptLimit() {
        SettlementRegion nearest = new SettlementRegion(0, 0);
        SettlementRegion farther = new SettlementRegion(2, 0);

        WorldgenRegionSearch.Result<Integer> result = search.findBest(
                        0,
                        0,
                        4,
                        10,
                        10,
                        region -> List.of(nearest, farther).contains(region),
                        region -> Optional.of(region.equals(nearest) ? 10 : 1),
                        Integer::compare
                ).result()
                .orElseThrow();

        assertEquals(farther, result.region());
        assertEquals(2, result.attemptedCandidates());
    }

    @Test
    void keepsNearestCandidateWhenEvaluationsAreEqual() {
        SettlementRegion nearest = new SettlementRegion(0, 0);
        SettlementRegion farther = new SettlementRegion(2, 0);

        WorldgenRegionSearch.Result<Integer> result = search.findBest(
                        0,
                        0,
                        4,
                        10,
                        10,
                        region -> List.of(nearest, farther).contains(region),
                        region -> Optional.of(1),
                        Integer::compare
                ).result()
                .orElseThrow();

        assertEquals(nearest, result.region());
        assertEquals(2, result.attemptedCandidates());
    }

    @Test
    void skipsRejectedCandidates() {
        SettlementRegion rejected = new SettlementRegion(0, 0);
        SettlementRegion accepted = new SettlementRegion(1, 0);

        WorldgenRegionSearch.Result<Integer> result = search.findBest(
                        0,
                        0,
                        4,
                        10,
                        10,
                        region -> region.equals(rejected) || region.equals(accepted),
                        region -> region.equals(accepted) ? Optional.of(1) : Optional.empty(),
                        Integer::compare
                ).result()
                .orElseThrow();

        assertEquals(accepted, result.region());
        assertEquals(2, result.attemptedCandidates());
    }

    @Test
    void stopsAtCandidateAttemptLimit() {
        WorldgenRegionSearch.Outcome<Integer> outcome = search.findBest(
                0,
                0,
                4,
                1,
                1,
                region -> true,
                region -> Optional.empty(),
                Integer::compare
        );

        assertTrue(outcome.result().isEmpty());
        assertEquals(1, outcome.attemptedCandidates());
    }

    @Test
    void schedulesSearchWithoutRunningItOnTheCallingThread() {
        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        var future = search.findBestAsync(
                0,
                0,
                1,
                1,
                1,
                region -> true,
                region -> Optional.of(1),
                Integer::compare,
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
                    () -> search.findBest(
                            0,
                            0,
                            1,
                            1,
                            1,
                            region -> true,
                            region -> Optional.of(1),
                            Integer::compare
                    )
            );
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsInvalidLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> search.findBest(
                        0, 0, 0, 1, 1, region -> true, region -> Optional.of(1), Integer::compare
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> search.findBest(
                        0, 0, 1, 0, 1, region -> true, region -> Optional.of(1), Integer::compare
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> search.findBest(
                        0, 0, 1, 1, -1, region -> true, region -> Optional.of(1), Integer::compare
                )
        );
    }

    @Test
    void stopsAfterDeterministicImprovementWindow() {
        WorldgenRegionSearch.Outcome<Integer> outcome = search.findBest(
                0,
                0,
                4,
                20,
                2,
                region -> true,
                region -> Optional.of(1),
                Integer::compare
        );

        assertTrue(outcome.result().isPresent());
        assertEquals(3, outcome.attemptedCandidates());
    }

    @Test
    void improvementWindowCanReplaceFirstAcceptedCandidate() {
        SettlementRegion first = new SettlementRegion(0, 0);
        SettlementRegion better = new SettlementRegion(1, 0);

        WorldgenRegionSearch.Result<Integer> result = search.findBest(
                        64,
                        64,
                        4,
                        20,
                        4,
                        region -> region.equals(first) || region.equals(better),
                        region -> Optional.of(region.equals(first) ? 10 : 1),
                        Integer::compare
                ).result()
                .orElseThrow();

        assertEquals(better, result.region());
        assertEquals(2, result.attemptedCandidates());
    }
}
