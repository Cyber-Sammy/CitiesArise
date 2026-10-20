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
        return findBestAsync(level,origin,java.util.Set.of());
    }

    public CompletableFuture<SearchResult> findFirstAsync(ServerLevel level, BlockPos origin, java.util.Set<SettlementRegion> excluded) {
        return findAsync(level,origin,excluded,false);
    }

    public CompletableFuture<SearchResult> findBestAsync(ServerLevel level, BlockPos origin, java.util.Set<SettlementRegion> excluded) {
        return findAsync(level,origin,excluded,true);
    }

    private CompletableFuture<SearchResult> findAsync(ServerLevel level, BlockPos origin, java.util.Set<SettlementRegion> excluded, boolean ranked) {
        var skipped=java.util.Set.copyOf(excluded);
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");

        if(!CitiesAriseWorldgenConfig.enabled())return CompletableFuture.completedFuture(
                new SearchResult(Optional.empty(),0,Map.of("WORLDGEN_DISABLED",1)));

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
        var nativeStarts=new NativeStructureStartVerifier(level,placement.placement());
        long worldSeed = context.worldSeed();
        int regionModulo = CitiesAriseWorldgenConfig.candidateRegionModulo();
        int seaLevel = level.getSeaLevel();
        Map<String, Integer> rejectionCounts = new LinkedHashMap<>();
        var rejectedRegions=new java.util.HashSet<SettlementRegion>();
        var completedPlans = planningService.cachedWorldgenPlans(context);
        return regionSearch.findBestAsync(
                origin.getX(),
                origin.getZ(),
                CitiesAriseWorldgenConfig.locateSearchRadiusRegions(),
                CitiesAriseWorldgenConfig.locateMaxCandidateAttempts(),
                ranked ? CitiesAriseWorldgenConfig.locateImprovementCandidateAttempts() : 0,
                region -> !skipped.contains(region) && com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.anchor(region,context.surveySize())
                        && candidateSelector.isCandidate(worldSeed, region, regionModulo,context.surveySize())
                        && placement.isStructureRegion(region)
                        && com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.reachable(region,context.surveySize(),
                                placement.potentialChunk(region).x,placement.potentialChunk(region).z),
                region -> {
                    var evaluation=evaluate(context,seaLevel,region,rejectionCounts,nativeStarts,completedPlans.get(region));
                    if(evaluation.isEmpty()) rejectedRegions.add(region);
                    return evaluation;
                },
                EarthworkSiteAssessment::compareTo,
                executor,
                java.util.concurrent.TimeUnit.SECONDS.toNanos(CitiesAriseWorldgenConfig.locateTimeLimitSeconds()),
                CitiesAriseWorldgenConfig.locateTerrainRanking() ? (region,mayContinue) ->
                        com.cybersammy.citiesarise.core.terrain.TerrainSiteEstimate.sample(
                                com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.bounds(region,context.surveySize()),
                                context.terrainProvider()::preliminarySample,mayContinue)
                                .map(estimate->java.util.OptionalDouble.of(estimate.score())).orElseGet(java.util.OptionalDouble::empty) : null,
                completedPlans.keySet()
        ).thenApply(outcome -> {
            if(outcome.timeLimitReached()) increment(rejectionCounts,"SEARCH_TIME_LIMIT");
            var raw=searchResult(outcome,rejectionCounts);
            var located=raw.settlement().map(found->{
                var center=com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.center(found.region(),context.surveySize());
                return new LocatedSettlement(found.region(),center.x(),center.z(),found.attemptedCandidates(),found.siteAssessment());
            });
            var result=new SearchResult(located,raw.attemptedCandidates(),raw.rejectionCounts());
            return new SearchResult(result.settlement(),result.attemptedCandidates(),result.rejectionCounts(),
                    context.profileId().value(),rejectedRegions,outcome.preliminaryCandidates());
        });
    }

    /** Seed/placement arithmetic only: never samples terrain or constructs a settlement plan. */
    public Optional<BlockPos> findPotential(ServerLevel level, BlockPos origin) {
        var planning=planningService.prepareLocateContext(level,level.getChunkSource().getGenerator());
        if(planning.isEmpty()) return Optional.empty();
        var size=planning.orElseThrow().surveySize();
        var placement = placementContext(level, level.getSeed());
        if (placement.isEmpty()) return Optional.empty();
        var context = placement.orElseThrow();
        return PotentialRegionSearch.find(SettlementRegion.fromBlockPosition(origin.getX(), origin.getZ()),
                CitiesAriseWorldgenConfig.locateSearchRadiusRegions(),
                region -> com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.anchor(region,size) && candidateSelector.isCandidate(level.getSeed(), region,
                        CitiesAriseWorldgenConfig.candidateRegionModulo(),size) && context.isStructureRegion(region)
                        && com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.reachable(region,size,
                                context.potentialChunk(region).x,context.potentialChunk(region).z))
                .map(context::locatePosition);
    }

    private Optional<EarthworkSiteAssessment> evaluate(
            WorldgenPlanningContext context,
            int seaLevel,
            SettlementRegion region,
            Map<String, Integer> rejectionCounts,NativeStructureStartVerifier nativeStarts,
            SuburbDebugPlanResult completedPlan
    ) {
        BlockPos center = centerPosition(seaLevel, region);
        SuburbDebugPlanResult result = completedPlan != null ? completedPlan : planningService.planForWorldgen(context, center);
        if (result.successful()) {
            var rejection=nativeStarts.rejection(result,context.profileId());
            if(rejection.isPresent()) {
                increment(rejectionCounts,rejection.orElseThrow());return Optional.empty();
            }
            return Optional.of(result.optionalSiteAssessment().orElseThrow(
                    () -> new IllegalStateException("successful worldgen plan is missing site assessment")
            ));
        }

        increment(rejectionCounts, rejectionReason(result));
        return Optional.empty();
    }

    private SearchResult searchResult(
            WorldgenRegionSearch.Outcome<EarthworkSiteAssessment> outcome,
            Map<String, Integer> rejectionCounts
    ) {
        Optional<LocatedSettlement> settlement = outcome.result().map(this::locatedSettlement);
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
            WorldgenRegionSearch.Result<EarthworkSiteAssessment> result
    ) {
        SettlementRegion region = result.region();
        var center = WorldgenPlacementCoordinates.diagnosticCenter(region);
        return new LocatedSettlement(
                region,
                center.x(),
                center.z(),
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

    public void cancel() {
        executor.cancel();
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
            Map<String, Integer> rejectionCounts,
            String profile,
            java.util.Set<SettlementRegion> rejectedRegions,
            int preliminaryCandidates
    ) {
        public SearchResult(Optional<LocatedSettlement> settlement,int attemptedCandidates,Map<String,Integer> rejectionCounts,String profile,
                java.util.Set<SettlementRegion> rejectedRegions) { this(settlement,attemptedCandidates,rejectionCounts,profile,rejectedRegions,0); }
        public SearchResult(Optional<LocatedSettlement> settlement,int attemptedCandidates,Map<String,Integer> rejectionCounts) {
            this(settlement,attemptedCandidates,rejectionCounts,"",java.util.Set.of());
        }
        public SearchResult {
            Objects.requireNonNull(profile);
            rejectedRegions=java.util.Set.copyOf(rejectedRegions);
            Objects.requireNonNull(settlement, "settlement");
            Objects.requireNonNull(rejectionCounts, "rejectionCounts");
            if (attemptedCandidates < 0 || preliminaryCandidates<0) {
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
