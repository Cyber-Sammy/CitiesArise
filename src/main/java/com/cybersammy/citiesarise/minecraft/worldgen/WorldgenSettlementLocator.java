package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

public final class WorldgenSettlementLocator {
    private static final int SEARCH_RADIUS_REGIONS = 64;
    private static final int MAX_CANDIDATE_ATTEMPTS = 64;

    private final MinecraftSuburbPlanningService planningService;
    private final WorldgenRegionCandidateSelector candidateSelector;
    private final WorldgenRegionSearch regionSearch;

    public WorldgenSettlementLocator(MinecraftSuburbPlanningService planningService) {
        this.planningService = Objects.requireNonNull(planningService, "planningService");
        this.candidateSelector = new WorldgenRegionCandidateSelector();
        this.regionSearch = new WorldgenRegionSearch();
    }

    public Optional<LocatedSettlement> findNearest(ServerLevel level, BlockPos origin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");

        long worldSeed = level.getSeed();
        int regionModulo = CitiesAriseWorldgenConfig.candidateRegionModulo();
        ChunkGenerator chunkGenerator = level.getChunkSource().getGenerator();
        Optional<WorldgenRegionSearch.Result> result = regionSearch.findNearest(
                origin.getX(),
                origin.getZ(),
                SEARCH_RADIUS_REGIONS,
                MAX_CANDIDATE_ATTEMPTS,
                region -> candidateSelector.isCandidate(worldSeed, region, regionModulo),
                region -> isAccepted(level, chunkGenerator, region)
        );
        return result.map(this::locatedSettlement);
    }

    private boolean isAccepted(ServerLevel level, ChunkGenerator chunkGenerator, SettlementRegion region) {
        BlockPos center = centerPosition(level, region);
        return planningService.planForWorldgen(level, chunkGenerator, center)
                .filter(result -> result.successful())
                .isPresent();
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
}
