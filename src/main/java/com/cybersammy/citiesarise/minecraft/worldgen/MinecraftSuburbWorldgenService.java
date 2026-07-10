package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseConfig;
import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementIndex;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementChunkProjector;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlanConverter;
import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import com.cybersammy.citiesarise.minecraft.planning.SuburbDebugPlanResult;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.slf4j.Logger;

public final class MinecraftSuburbWorldgenService {
    private static final int MAX_CACHED_PLACEMENT_INDICES = 256;

    private final MinecraftSuburbPlanningService planningService;
    private final DebugPlacementPlanConverter planConverter;
    private final DebugPlacementChunkProjector chunkProjector;
    private final WorldgenPlacementApplier placementApplier;
    private final WorldgenPlacementIndexCache placementIndexCache;
    private final Logger logger;

    public MinecraftSuburbWorldgenService(MinecraftSuburbPlanningService planningService, Logger logger) {
        this(
                planningService,
                new DebugPlacementPlanConverter(),
                new DebugPlacementChunkProjector(),
                new WorldgenPlacementApplier(),
                new WorldgenPlacementIndexCache(MAX_CACHED_PLACEMENT_INDICES),
                logger
        );
    }

    MinecraftSuburbWorldgenService(
            MinecraftSuburbPlanningService planningService,
            DebugPlacementPlanConverter planConverter,
            DebugPlacementChunkProjector chunkProjector,
            WorldgenPlacementApplier placementApplier,
            WorldgenPlacementIndexCache placementIndexCache,
            Logger logger
    ) {
        this.planningService = Objects.requireNonNull(planningService, "planningService");
        this.planConverter = Objects.requireNonNull(planConverter, "planConverter");
        this.chunkProjector = Objects.requireNonNull(chunkProjector, "chunkProjector");
        this.placementApplier = Objects.requireNonNull(placementApplier, "placementApplier");
        this.placementIndexCache = Objects.requireNonNull(placementIndexCache, "placementIndexCache");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean placeChunk(WorldGenLevel level, ChunkGenerator chunkGenerator, BlockPos origin) {
        if (!CitiesAriseWorldgenConfig.enabled()) {
            return false;
        }

        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunkGenerator, "chunkGenerator");
        Objects.requireNonNull(origin, "origin");

        SuburbDebugPlanResult planningResult = planningService.planForWorldgen(level, chunkGenerator, origin);
        if (!planningResult.successful()) {
            return false;
        }

        DebugChunkPlacementIndex placementIndex = placementIndexCache.getOrCreate(
                planningResult.plan(),
                () -> createPlacementIndex(planningResult)
        );
        PlacementChunk chunk = PlacementChunk.containing(origin.getX(), origin.getZ());
        DebugChunkPlacementPlan chunkPlan = placementIndex.slice(chunk);

        if (chunkPlan.operations().isEmpty()) {
            return false;
        }

        int placedBlocks = placementApplier.apply(level, chunkPlan);
        logPlacedChunk(planningResult, chunkPlan, placedBlocks);
        return placedBlocks > 0;
    }

    public void clearCache() {
        placementIndexCache.clear();
    }

    private DebugChunkPlacementIndex createPlacementIndex(SuburbDebugPlanResult planningResult) {
        DebugPlacementPlan placementPlan = planConverter.convert(planningResult.plan());
        return chunkProjector.partition(placementPlan);
    }

    private void logPlacedChunk(
            SuburbDebugPlanResult planningResult,
            DebugChunkPlacementPlan chunkPlan,
            int placedBlocks
    ) {
        if (!CitiesAriseConfig.placementLoggingEnabled()) {
            return;
        }

        logger.info(
                "Worldgen suburb chunk placed: region=({}, {}), chunk=({}, {}), operations={}, placedBlocks={}.",
                planningResult.region().x(),
                planningResult.region().z(),
                chunkPlan.chunk().x(),
                chunkPlan.chunk().z(),
                chunkPlan.size(),
                placedBlocks
        );
    }
}
