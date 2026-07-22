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
import java.util.function.Function;
import java.util.function.Predicate;

final class WorldgenRegionSearch {
    <T> CompletableFuture<Outcome<T>> findBestAsync(
            int originX,
            int originZ,
            int searchRadius,
            int maxCandidateAttempts,
            Predicate<SettlementRegion> candidatePredicate,
            Function<SettlementRegion, Optional<T>> evaluator,
            Comparator<T> comparator,
            Executor executor
    ) {
        Objects.requireNonNull(executor, "executor");
        return CompletableFuture.supplyAsync(
                () -> findBest(
                        originX,
                        originZ,
                        searchRadius,
                        maxCandidateAttempts,
                        candidatePredicate,
                        evaluator,
                        comparator
                ),
                executor
        );
    }

    <T> Outcome<T> findBest(
            int originX,
            int originZ,
            int searchRadius,
            int maxCandidateAttempts,
            Predicate<SettlementRegion> candidatePredicate,
            Function<SettlementRegion, Optional<T>> evaluator,
            Comparator<T> comparator
    ) {
        requirePositive(searchRadius, "searchRadius");
        requirePositive(maxCandidateAttempts, "maxCandidateAttempts");
        Objects.requireNonNull(candidatePredicate, "candidatePredicate");
        Objects.requireNonNull(evaluator, "evaluator");
        Objects.requireNonNull(comparator, "comparator");

        List<SettlementRegion> regions = orderedRegions(originX, originZ, searchRadius);
        Optional<Candidate<T>> bestCandidate = Optional.empty();
        int attempts = 0;
        for (SettlementRegion region : regions) {
            rejectInterruptedSearch();
            if (!candidatePredicate.test(region)) {
                continue;
            }
            attempts++;
            Optional<T> evaluation = Objects.requireNonNull(evaluator.apply(region), "evaluation");
            if (evaluation.isPresent()) {
                Candidate<T> candidate = new Candidate<>(region, evaluation.orElseThrow());
                bestCandidate = selectBetter(bestCandidate, candidate, comparator);
            }
            if (attempts >= maxCandidateAttempts) {
                break;
            }
        }
        return outcome(bestCandidate, attempts);
    }

    private static <T> Optional<Candidate<T>> selectBetter(
            Optional<Candidate<T>> current,
            Candidate<T> candidate,
            Comparator<T> comparator
    ) {
        if (current.isEmpty()) {
            return Optional.of(candidate);
        }
        Candidate<T> currentCandidate = current.orElseThrow();
        if (comparator.compare(candidate.evaluation(), currentCandidate.evaluation()) < 0) {
            return Optional.of(candidate);
        }
        return current;
    }

    private static <T> Outcome<T> outcome(Optional<Candidate<T>> candidate, int attempts) {
        return candidate
                .map(value -> Outcome.found(value.region(), value.evaluation(), attempts))
                .orElseGet(() -> Outcome.notFound(attempts));
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

    private static void rejectInterruptedSearch() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("settlement search was interrupted");
        }
    }

    private record Candidate<T>(SettlementRegion region, T evaluation) {
        private Candidate {
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(evaluation, "evaluation");
        }
    }

    record Result<T>(SettlementRegion region, T evaluation, int attemptedCandidates) {
        Result {
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(evaluation, "evaluation");
        }
    }

    record Outcome<T>(Optional<Result<T>> result, int attemptedCandidates) {
        Outcome {
            Objects.requireNonNull(result, "result");
            if (attemptedCandidates < 0) {
                throw new IllegalArgumentException("attemptedCandidates must not be negative");
            }
        }

        private static <T> Outcome<T> found(
                SettlementRegion region,
                T evaluation,
                int attemptedCandidates
        ) {
            return new Outcome<>(
                    Optional.of(new Result<>(region, evaluation, attemptedCandidates)),
                    attemptedCandidates
            );
        }

        private static <T> Outcome<T> notFound(int attemptedCandidates) {
            return new Outcome<>(Optional.empty(), attemptedCandidates);
        }
    }
}
