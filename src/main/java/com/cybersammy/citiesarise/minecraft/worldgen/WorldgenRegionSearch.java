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
            int improvementCandidateAttempts,
            Predicate<SettlementRegion> candidatePredicate,
            Function<SettlementRegion, Optional<T>> evaluator,
            Comparator<T> comparator,
            Executor executor
    ) {
        return findBestAsync(originX,originZ,searchRadius,maxCandidateAttempts,improvementCandidateAttempts,
                candidatePredicate,evaluator,comparator,executor,Long.MAX_VALUE);
    }

    <T> CompletableFuture<Outcome<T>> findBestAsync(int originX,int originZ,int searchRadius,int maxCandidateAttempts,
            int improvementCandidateAttempts,Predicate<SettlementRegion> candidatePredicate,
            Function<SettlementRegion,Optional<T>> evaluator,Comparator<T> comparator,Executor executor,long timeLimitNanos) {
        return findBestAsync(originX,originZ,searchRadius,maxCandidateAttempts,improvementCandidateAttempts,candidatePredicate,evaluator,
                comparator,executor,timeLimitNanos,null);
    }
    <T> CompletableFuture<Outcome<T>> findBestAsync(int originX,int originZ,int searchRadius,int maxCandidateAttempts,
            int improvementCandidateAttempts,Predicate<SettlementRegion> candidatePredicate,
            Function<SettlementRegion,Optional<T>> evaluator,Comparator<T> comparator,Executor executor,long timeLimitNanos,
            WorldgenRegionPreselection.Estimator estimator) {
        return findBestAsync(originX, originZ, searchRadius, maxCandidateAttempts, improvementCandidateAttempts,
                candidatePredicate, evaluator, comparator, executor, timeLimitNanos, estimator, java.util.Set.of());
    }

    <T> CompletableFuture<Outcome<T>> findBestAsync(int originX,int originZ,int searchRadius,int maxCandidateAttempts,
            int improvementCandidateAttempts,Predicate<SettlementRegion> candidatePredicate,
            Function<SettlementRegion,Optional<T>> evaluator,Comparator<T> comparator,Executor executor,long timeLimitNanos,
            WorldgenRegionPreselection.Estimator estimator, java.util.Set<SettlementRegion> preferred) {
        Objects.requireNonNull(executor, "executor");
        return CompletableFuture.supplyAsync(
                () -> findBest(
                        originX,
                        originZ,
                        searchRadius,
                        maxCandidateAttempts,
                        improvementCandidateAttempts,
                        candidatePredicate,
                        evaluator,
                        comparator,
                        timeLimitNanos,
                        System::nanoTime,estimator,preferred
                ),
                executor
        );
    }

    <T> Outcome<T> findBest(
            int originX,
            int originZ,
            int searchRadius,
            int maxCandidateAttempts,
            int improvementCandidateAttempts,
            Predicate<SettlementRegion> candidatePredicate,
            Function<SettlementRegion, Optional<T>> evaluator,
            Comparator<T> comparator
    ) {
        return findBest(originX,originZ,searchRadius,maxCandidateAttempts,improvementCandidateAttempts,
                candidatePredicate,evaluator,comparator,Long.MAX_VALUE,System::nanoTime);
    }

    <T> Outcome<T> findBest(int originX,int originZ,int searchRadius,int maxCandidateAttempts,
            int improvementCandidateAttempts,Predicate<SettlementRegion> candidatePredicate,
            Function<SettlementRegion,Optional<T>> evaluator,Comparator<T> comparator,long timeLimitNanos,
            java.util.function.LongSupplier clock) {
        return findBest(originX,originZ,searchRadius,maxCandidateAttempts,improvementCandidateAttempts,candidatePredicate,evaluator,
                comparator,timeLimitNanos,clock,null);
    }
    <T> Outcome<T> findBest(int originX,int originZ,int searchRadius,int maxCandidateAttempts,
            int improvementCandidateAttempts,Predicate<SettlementRegion> candidatePredicate,
            Function<SettlementRegion,Optional<T>> evaluator,Comparator<T> comparator,long timeLimitNanos,
            java.util.function.LongSupplier clock,WorldgenRegionPreselection.Estimator estimator) {
        return findBest(originX, originZ, searchRadius, maxCandidateAttempts, improvementCandidateAttempts,
                candidatePredicate, evaluator, comparator, timeLimitNanos, clock, estimator, java.util.Set.of());
    }

    <T> Outcome<T> findBest(int originX,int originZ,int searchRadius,int maxCandidateAttempts,
            int improvementCandidateAttempts,Predicate<SettlementRegion> candidatePredicate,
            Function<SettlementRegion,Optional<T>> evaluator,Comparator<T> comparator,long timeLimitNanos,
            java.util.function.LongSupplier clock,WorldgenRegionPreselection.Estimator estimator,
            java.util.Set<SettlementRegion> preferred) {
        if(timeLimitNanos<=0) throw new IllegalArgumentException("timeLimitNanos must be positive");
        long started=clock.getAsLong();
        requirePositive(searchRadius, "searchRadius");
        requirePositive(maxCandidateAttempts, "maxCandidateAttempts");
        requireNonNegative(improvementCandidateAttempts, "improvementCandidateAttempts");
        Objects.requireNonNull(candidatePredicate, "candidatePredicate");
        Objects.requireNonNull(evaluator, "evaluator");
        Objects.requireNonNull(comparator, "comparator");

        rejectInterruptedSearch();
        List<SettlementRegion> regions = orderedRegions(originX, originZ, searchRadius);
        // Keep geographic order within completed hints; exclusions, radius and eligibility still apply.
        regions = new ArrayList<>(regions);
        regions.sort(Comparator.comparingInt(region -> preferred.contains(region) ? 0 : 1));
        int preferredCount = 0;
        while (preferredCount < regions.size() && preferred.contains(regions.get(preferredCount))) preferredCount++;
        int preliminaryCandidates=0;
        Optional<Candidate<T>> bestCandidate = Optional.empty();
        int attempts = 0;
        int firstAcceptedAttempt = -1;
        boolean timedOut=false;
        for (int index = 0; index < regions.size(); index++) {
            rejectInterruptedSearch();
            if(clock.getAsLong()-started>=timeLimitNanos) { timedOut=true; break; }
            // Defer generator screening until completed plans fail (or best mode requests improvement).
            if (index == preferredCount && estimator != null) {
                long screeningStarted = clock.getAsLong();
                long allowance = Math.min(java.util.concurrent.TimeUnit.SECONDS.toNanos(8), timeLimitNanos / 5);
                var selection = WorldgenRegionPreselection.order(regions.subList(index, regions.size()),
                        candidatePredicate, estimator, () -> clock.getAsLong() - started < timeLimitNanos
                                && clock.getAsLong() - screeningStarted < allowance);
                var reordered = new ArrayList<>(regions.subList(0, index));
                reordered.addAll(selection.regions());
                regions = reordered;
                preliminaryCandidates = selection.sampledSites();
                System.getLogger(WorldgenRegionSearch.class.getName()).log(System.Logger.Level.INFO,
                        "Diagnostic terrain preselection: screened=" + preliminaryCandidates
                                + ", elapsedMs=" + (clock.getAsLong() - screeningStarted) / 1_000_000.0);
                if (clock.getAsLong() - started >= timeLimitNanos) { timedOut = true; break; }
            }
            SettlementRegion region = regions.get(index);
            if (!candidatePredicate.test(region)) {
                continue;
            }
            attempts++;
            Optional<T> evaluation = Objects.requireNonNull(evaluator.apply(region), "evaluation");
            rejectInterruptedSearch();
            if (evaluation.isPresent()) {
                Candidate<T> candidate = new Candidate<>(region, evaluation.orElseThrow());
                bestCandidate = selectBetter(bestCandidate, candidate, comparator,originX,originZ);
                if (firstAcceptedAttempt < 0) {
                    firstAcceptedAttempt = attempts;
                }
            }
            if (searchComplete(
                    attempts,
                    maxCandidateAttempts,
                    firstAcceptedAttempt,
                    improvementCandidateAttempts
            )) {
                break;
            }
        }
        var result=outcome(bestCandidate, attempts);
        return new Outcome<>(result.result(),attempts,timedOut,preliminaryCandidates);
    }

    private static <T> Optional<Candidate<T>> selectBetter(
            Optional<Candidate<T>> current,
            Candidate<T> candidate,
            Comparator<T> comparator,int originX,int originZ
    ) {
        if (current.isEmpty()) {
            return Optional.of(candidate);
        }
        Candidate<T> currentCandidate = current.orElseThrow();
        int compared=comparator.compare(candidate.evaluation(),currentCandidate.evaluation());
        var geographic=Comparator.comparingLong((SettlementRegion r)->distanceSquared(originX,originZ,r))
                .thenComparingInt(SettlementRegion::x).thenComparingInt(SettlementRegion::z);
        if (compared<0 || compared==0 && geographic.compare(candidate.region(),currentCandidate.region())<0) {
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
            rejectInterruptedSearch();
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

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static boolean searchComplete(
            int attempts,
            int maxCandidateAttempts,
            int firstAcceptedAttempt,
            int improvementCandidateAttempts
    ) {
        if (attempts >= maxCandidateAttempts) {
            return true;
        }
        if (firstAcceptedAttempt < 0) {
            return false;
        }
        return attempts - firstAcceptedAttempt >= improvementCandidateAttempts;
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

    record Outcome<T>(Optional<Result<T>> result, int attemptedCandidates,boolean timeLimitReached,int preliminaryCandidates) {
        Outcome(Optional<Result<T>> result,int attemptedCandidates) { this(result,attemptedCandidates,false,0); }
        Outcome(Optional<Result<T>> result,int attemptedCandidates,boolean timeLimitReached) { this(result,attemptedCandidates,timeLimitReached,0); }
        Outcome {
            Objects.requireNonNull(result, "result");
            if (attemptedCandidates < 0 || preliminaryCandidates<0) {
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
