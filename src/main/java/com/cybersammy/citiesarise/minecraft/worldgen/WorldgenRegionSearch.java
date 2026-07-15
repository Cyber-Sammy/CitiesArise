package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

final class WorldgenRegionSearch {
    CompletableFuture<Outcome> findNearestAsync(
            int originX,
            int originZ,
            int searchRadius,
            int maxCandidateAttempts,
            Predicate<SettlementRegion> candidatePredicate,
            Predicate<SettlementRegion> acceptedPredicate,
            Executor executor
    ) {
        Objects.requireNonNull(executor, "executor");
        return CompletableFuture.supplyAsync(
                () -> findNearest(
                        originX,
                        originZ,
                        searchRadius,
                        maxCandidateAttempts,
                        candidatePredicate,
                        acceptedPredicate
                ),
                executor
        );
    }

    Outcome findNearest(
            int originX,
            int originZ,
            int searchRadius,
            int maxCandidateAttempts,
            Predicate<SettlementRegion> candidatePredicate,
            Predicate<SettlementRegion> acceptedPredicate
    ) {
        requirePositive(searchRadius, "searchRadius");
        requirePositive(maxCandidateAttempts, "maxCandidateAttempts");

        List<SettlementRegion> regions = orderedRegions(originX, originZ, searchRadius);
        int attempts = 0;
        for (SettlementRegion region : regions) {
            rejectInterruptedSearch();
            if (!candidatePredicate.test(region)) {
                continue;
            }
            attempts++;
            if (acceptedPredicate.test(region)) {
                return Outcome.found(region, attempts);
            }
            if (attempts >= maxCandidateAttempts) {
                return Outcome.notFound(attempts);
            }
        }
        return Outcome.notFound(attempts);
    }

    private static List<SettlementRegion> orderedRegions(int originX, int originZ, int radius) {
        SettlementRegion originRegion = SettlementRegion.fromBlockPosition(originX, originZ);
        List<SettlementRegion> regions = new ArrayList<>();
        for (int zOffset = -radius; zOffset <= radius; zOffset++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                regions.add(new SettlementRegion(originRegion.x() + xOffset, originRegion.z() + zOffset));
            }
        }
        regions.sort(Comparator
                .comparingLong((SettlementRegion region) -> distanceSquared(originX, originZ, region))
                .thenComparingInt(SettlementRegion::x)
                .thenComparingInt(SettlementRegion::z));
        return List.copyOf(regions);
    }

    private static long distanceSquared(int originX, int originZ, SettlementRegion region) {
        long xDistance = (long) centerCoordinate(region.x()) - originX;
        long zDistance = (long) centerCoordinate(region.z()) - originZ;
        return (xDistance * xDistance) + (zDistance * zDistance);
    }

    static int centerCoordinate(int regionCoordinate) {
        return Math.addExact(
                Math.multiplyExact(regionCoordinate, SettlementRegion.REGION_BLOCKS),
                SettlementRegion.REGION_BLOCKS / 2
        );
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    record Result(SettlementRegion region, int attemptedCandidates) {
    }

    private static void rejectInterruptedSearch() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("settlement search was interrupted");
        }
    }

    record Outcome(Optional<Result> result, int attemptedCandidates) {
        Outcome {
            Objects.requireNonNull(result, "result");
            if (attemptedCandidates < 0) {
                throw new IllegalArgumentException("attemptedCandidates must not be negative");
            }
        }

        private static Outcome found(SettlementRegion region, int attemptedCandidates) {
            return new Outcome(Optional.of(new Result(region, attemptedCandidates)), attemptedCandidates);
        }

        private static Outcome notFound(int attemptedCandidates) {
            return new Outcome(Optional.empty(), attemptedCandidates);
        }
    }
}
