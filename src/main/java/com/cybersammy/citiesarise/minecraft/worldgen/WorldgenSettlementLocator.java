package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import com.cybersammy.citiesarise.minecraft.planning.SuburbDebugPlanResult;
import com.cybersammy.citiesarise.minecraft.planning.WorldgenPlanningContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class WorldgenSettlementLocator {
    private final MinecraftSuburbPlanningService planningService;
    private final WorldgenRegionCandidateSelector candidateSelector;
    private final WorldgenRegionSearch regionSearch;
    private final LocateSearchExecutor executor;

    public WorldgenSettlementLocator(MinecraftSuburbPlanningService planningService) {
        this.planningService = Objects.requireNonNull(planningService, "planningService");
        this.candidateSelector = new WorldgenRegionCandidateSelector();
        this.regionSearch = new WorldgenRegionSearch();
        this.executor = new LocateSearchExecutor();
    }

    public CompletableFuture<SearchResult> findNearestAsync(ServerLevel level, BlockPos origin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");

        Optional<WorldgenPlanningContext> optionalContext = planningService.prepareLocateContext(
                level,
                level.getChunkSource().getGenerator()
        );
        if (optionalContext.isEmpty()) {
            return CompletableFuture.completedFuture(profileUnavailableResult());
        }

        WorldgenPlanningContext context = optionalContext.orElseThrow();
        long worldSeed = context.worldSeed();
        int regionModulo = CitiesAriseWorldgenConfig.candidateRegionModulo();
        int seaLevel = level.getSeaLevel();
        Map<String, Integer> rejectionCounts = new LinkedHashMap<>();
        return regionSearch.findNearestAsync(
                origin.getX(),
                origin.getZ(),
                CitiesAriseWorldgenConfig.locateSearchRadiusRegions(),
                CitiesAriseWorldgenConfig.locateMaxCandidateAttempts(),
                region -> candidateSelector.isCandidate(worldSeed, region, regionModulo),
                region -> isAccepted(context, seaLevel, region, rejectionCounts),
                executor
        ).thenApply(outcome -> searchResult(outcome, rejectionCounts));
    }

    private boolean isAccepted(
            WorldgenPlanningContext context,
            int seaLevel,
            SettlementRegion region,
            Map<String, Integer> rejectionCounts
    ) {
        BlockPos center = centerPosition(seaLevel, region);
        SuburbDebugPlanResult result = planningService.planForWorldgen(context, center);
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

    private static SearchResult profileUnavailableResult() {
        return new SearchResult(Optional.empty(), 0, Map.of("PROFILE_UNAVAILABLE", 1));
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

    private static BlockPos centerPosition(int seaLevel, SettlementRegion region) {
        return new BlockPos(
                WorldgenRegionSearch.centerCoordinate(region.x()),
                seaLevel,
                WorldgenRegionSearch.centerCoordinate(region.z())
        );
    }

    public void stop() {
        executor.stop();
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
