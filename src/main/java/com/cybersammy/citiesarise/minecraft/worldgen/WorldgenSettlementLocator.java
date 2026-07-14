package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import com.cybersammy.citiesarise.minecraft.planning.SuburbDebugPlanResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

public final class WorldgenSettlementLocator {
    private final MinecraftSuburbPlanningService planningService;
    private final WorldgenRegionCandidateSelector candidateSelector;
    private final WorldgenRegionSearch regionSearch;
    private final Executor executor;

    public WorldgenSettlementLocator(MinecraftSuburbPlanningService planningService) {
        this(planningService, Util.backgroundExecutor());
    }

    WorldgenSettlementLocator(MinecraftSuburbPlanningService planningService, Executor executor) {
        this.planningService = Objects.requireNonNull(planningService, "planningService");
        this.candidateSelector = new WorldgenRegionCandidateSelector();
        this.regionSearch = new WorldgenRegionSearch();
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public CompletableFuture<SearchResult> findNearestAsync(ServerLevel level, BlockPos origin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");

        long worldSeed = level.getSeed();
        int regionModulo = CitiesAriseWorldgenConfig.candidateRegionModulo();
        ChunkGenerator chunkGenerator = level.getChunkSource().getGenerator();
        Map<String, Integer> rejectionCounts = new LinkedHashMap<>();
        return regionSearch.findNearestAsync(
                origin.getX(),
                origin.getZ(),
                CitiesAriseWorldgenConfig.locateSearchRadiusRegions(),
                CitiesAriseWorldgenConfig.locateMaxCandidateAttempts(),
                region -> candidateSelector.isCandidate(worldSeed, region, regionModulo),
                region -> isAccepted(level, chunkGenerator, region, rejectionCounts),
                executor
        ).thenApply(outcome -> searchResult(outcome, rejectionCounts));
    }

    private boolean isAccepted(
            ServerLevel level,
            ChunkGenerator chunkGenerator,
            SettlementRegion region,
            Map<String, Integer> rejectionCounts
    ) {
        BlockPos center = centerPosition(level, region);
        Optional<SuburbDebugPlanResult> planningResult = planningService.planForWorldgen(level, chunkGenerator, center);
        if (planningResult.isEmpty()) {
            increment(rejectionCounts, "PROFILE_UNAVAILABLE");
            return false;
        }

        SuburbDebugPlanResult result = planningResult.orElseThrow();
        if (result.successful()) {
            return true;
        }

        increment(rejectionCounts, rejectionReason(result));
        return false;
    }

    private SearchResult searchResult(
            WorldgenRegionSearch.Outcome outcome,
            Map<String, Integer> rejectionCounts
    ) {
        Optional<LocatedSettlement> settlement = outcome.result().map(this::locatedSettlement);
        return new SearchResult(settlement, outcome.attemptedCandidates(), rejectionCounts);
    }

    private static String rejectionReason(SuburbDebugPlanResult result) {
        return result.optionalTerrainDiagnostic()
                .flatMap(diagnostic -> diagnostic.primaryRejectionReason())
                .map(Enum::name)
                .orElseGet(() -> result.optionalFailureReason().map(Enum::name).orElse("UNKNOWN"));
    }

    private static void increment(Map<String, Integer> counts, String reason) {
        counts.merge(reason, 1, Integer::sum);
    }

    private LocatedSettlement locatedSettlement(WorldgenRegionSearch.Result result) {
        SettlementRegion region = result.region();
        return new LocatedSettlement(
                region,
                WorldgenRegionSearch.centerCoordinate(region.x()),
                WorldgenRegionSearch.centerCoordinate(region.z()),
                result.attemptedCandidates()
        );
    }

    private static BlockPos centerPosition(ServerLevel level, SettlementRegion region) {
        return new BlockPos(
                WorldgenRegionSearch.centerCoordinate(region.x()),
                level.getSeaLevel(),
                WorldgenRegionSearch.centerCoordinate(region.z())
        );
    }

    public record LocatedSettlement(
            SettlementRegion region,
            int blockX,
            int blockZ,
            int attemptedCandidates
    ) {
    }

    public record SearchResult(
            Optional<LocatedSettlement> settlement,
            int attemptedCandidates,
            Map<String, Integer> rejectionCounts
    ) {
        public SearchResult {
            Objects.requireNonNull(settlement, "settlement");
            Objects.requireNonNull(rejectionCounts, "rejectionCounts");
            if (attemptedCandidates < 0) {
                throw new IllegalArgumentException("attemptedCandidates must not be negative");
            }

            rejectionCounts = Collections.unmodifiableMap(
                    new LinkedHashMap<>(new TreeMap<>(rejectionCounts))
            );
        }

        public String rejectionSummary() {
            if (rejectionCounts.isEmpty()) {
                return "none";
            }

            return rejectionCounts.entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining(", "));
        }
    }
}
