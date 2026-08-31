package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.core.earthwork.EarthworkSiteAssessment;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

public final class WorldgenSettlementLocator {
    private static final ResourceLocation SUBURB_STRUCTURE_SET =
            ResourceLocation.fromNamespaceAndPath("cities_arise", "suburb");

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

    public CompletableFuture<SearchResult> findBestAsync(ServerLevel level, BlockPos origin) {
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
        Optional<PlacementContext> optionalPlacement = placementContext(level, context.worldSeed());
        if (optionalPlacement.isEmpty()) {
            return CompletableFuture.completedFuture(placementUnavailableResult());
        }
        PlacementContext placement = optionalPlacement.orElseThrow();
        long worldSeed = context.worldSeed();
        int regionModulo = CitiesAriseWorldgenConfig.candidateRegionModulo();
        int seaLevel = level.getSeaLevel();
        Map<String, Integer> rejectionCounts = new LinkedHashMap<>();
        return regionSearch.findBestAsync(
                origin.getX(),
                origin.getZ(),
                CitiesAriseWorldgenConfig.locateSearchRadiusRegions(),
                CitiesAriseWorldgenConfig.locateMaxCandidateAttempts(),
                CitiesAriseWorldgenConfig.locateImprovementCandidateAttempts(),
                region -> candidateSelector.isCandidate(worldSeed, region, regionModulo)
                        && placement.isStructureRegion(region),
                region -> evaluate(context, seaLevel, region, rejectionCounts),
                EarthworkSiteAssessment::compareTo,
                executor
        ).thenApply(outcome -> searchResult(outcome, rejectionCounts, placement));
    }

    private Optional<EarthworkSiteAssessment> evaluate(
            WorldgenPlanningContext context,
            int seaLevel,
            SettlementRegion region,
            Map<String, Integer> rejectionCounts
    ) {
        BlockPos center = centerPosition(seaLevel, region);
        SuburbDebugPlanResult result = planningService.planForWorldgen(context, center);
        if (result.successful()) {
            return Optional.of(result.optionalSiteAssessment().orElseThrow(
                    () -> new IllegalStateException("successful worldgen plan is missing site assessment")
            ));
        }

        increment(rejectionCounts, rejectionReason(result));
        return Optional.empty();
    }

    private SearchResult searchResult(
            WorldgenRegionSearch.Outcome<EarthworkSiteAssessment> outcome,
            Map<String, Integer> rejectionCounts,
            PlacementContext placement
    ) {
        Optional<LocatedSettlement> settlement = outcome.result().map(result -> locatedSettlement(result, placement));
        return new SearchResult(settlement, outcome.attemptedCandidates(), rejectionCounts);
    }

    private static SearchResult profileUnavailableResult() {
        return new SearchResult(Optional.empty(), 0, Map.of("PROFILE_UNAVAILABLE", 1));
    }

    private static SearchResult placementUnavailableResult() {
        return new SearchResult(Optional.empty(), 0, Map.of("STRUCTURE_PLACEMENT_UNAVAILABLE", 1));
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

    private LocatedSettlement locatedSettlement(
            WorldgenRegionSearch.Result<EarthworkSiteAssessment> result,
            PlacementContext placement
    ) {
        SettlementRegion region = result.region();
        BlockPos locatePosition = placement.locatePosition(region);
        return new LocatedSettlement(
                region,
                locatePosition.getX(),
                locatePosition.getZ(),
                result.attemptedCandidates(),
                result.evaluation()
        );
    }

    private static BlockPos centerPosition(int seaLevel, SettlementRegion region) {
        return new BlockPos(
                WorldgenRegionSearch.centerCoordinate(region.x()),
                seaLevel,
                WorldgenRegionSearch.centerCoordinate(region.z())
        );
    }

    private static Optional<PlacementContext> placementContext(ServerLevel level, long worldSeed) {
        StructureSet structureSet = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE_SET)
                .get(SUBURB_STRUCTURE_SET);
        if (structureSet == null
                || !(structureSet.placement() instanceof RandomSpreadStructurePlacement placement)
                || placement.spacing() != SettlementRegion.REGION_CHUNKS) {
            return Optional.empty();
        }
        return Optional.of(new PlacementContext(
                placement,
                level.getChunkSource().getGeneratorState(),
                worldSeed
        ));
    }

    public void stop() {
        executor.stop();
    }

    private record PlacementContext(
            RandomSpreadStructurePlacement placement,
            ChunkGeneratorStructureState generatorState,
            long worldSeed
    ) {
        private PlacementContext {
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(generatorState, "generatorState");
        }

        private boolean isStructureRegion(SettlementRegion region) {
            ChunkPos chunk = potentialChunk(region);
            return placement.isStructureChunk(generatorState, chunk.x, chunk.z);
        }

        private BlockPos locatePosition(SettlementRegion region) {
            return placement.getLocatePos(potentialChunk(region));
        }

        private ChunkPos potentialChunk(SettlementRegion region) {
            return potentialChunkForRegion(placement, worldSeed, region);
        }
    }

    static ChunkPos potentialChunkForRegion(
            RandomSpreadStructurePlacement placement,
            long worldSeed,
            SettlementRegion region
    ) {
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(region, "region");
        int probeChunkX = WorldgenPlacementCoordinates.probeChunk(region.x(), placement.spacing());
        int probeChunkZ = WorldgenPlacementCoordinates.probeChunk(region.z(), placement.spacing());
        return placement.getPotentialStructureChunk(worldSeed, probeChunkX, probeChunkZ);
    }

    public record LocatedSettlement(
            SettlementRegion region,
            int blockX,
            int blockZ,
            int attemptedCandidates,
            EarthworkSiteAssessment siteAssessment
    ) {
        public LocatedSettlement {
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(siteAssessment, "siteAssessment");
        }
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
