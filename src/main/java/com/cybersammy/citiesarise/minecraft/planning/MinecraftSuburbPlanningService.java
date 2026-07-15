package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.CitiesAriseMod;
import com.cybersammy.citiesarise.config.CitiesAriseConfig;
import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.config.DebugSuburbPlanningConfig;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanTransformService;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanner;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningRequest;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.transform.LightDecayTransform;
import com.cybersammy.citiesarise.core.transform.TransformPipeline;
import com.cybersammy.citiesarise.minecraft.profile.MinecraftSettlementProfileRepository;
import com.cybersammy.citiesarise.minecraft.profile.SettlementProfileSource;
import com.cybersammy.citiesarise.minecraft.cache.BoundedLruCache;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftTerrainSampler;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftWorldgenTerrainProvider;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class MinecraftSuburbPlanningService {
    private static final int MAX_CACHED_PROFILES = 32;

    private final SuburbPlanner planner;
    private final SuburbPlanTransformService transformService;
    private final SettlementProfileSource profileSource;
    private final RegionPlanCache planCache;
    private final BoundedLruCache<SettlementProfileId, DebugSettlementProfileSelection.SelectionResult> profileCache;
    private final Logger logger;

    public MinecraftSuburbPlanningService(SuburbPlanner planner, Logger logger) {
        this(planner, TransformPipeline.empty(), logger);
    }

    public MinecraftSuburbPlanningService(SuburbPlanner planner, TransformPipeline transformPipeline, Logger logger) {
        this(
                planner,
                new SuburbPlanTransformService(transformPipeline),
                new MinecraftSettlementProfileRepository(),
                new InMemoryRegionPlanCache(),
                logger
        );
    }

    public MinecraftSuburbPlanningService(
            SuburbPlanner planner,
            SuburbPlanTransformService transformService,
            Logger logger
    ) {
        this(planner, transformService, new MinecraftSettlementProfileRepository(), new InMemoryRegionPlanCache(), logger);
    }

    public MinecraftSuburbPlanningService(
            SuburbPlanner planner,
            SuburbPlanTransformService transformService,
            SettlementProfileSource profileSource,
            Logger logger
    ) {
        this(planner, transformService, profileSource, new InMemoryRegionPlanCache(), logger);
    }

    public MinecraftSuburbPlanningService(
            SuburbPlanner planner,
            SuburbPlanTransformService transformService,
            SettlementProfileSource profileSource,
            RegionPlanCache planCache,
            Logger logger
    ) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.transformService = Objects.requireNonNull(transformService, "transformService");
        this.profileSource = Objects.requireNonNull(profileSource, "profileSource");
        this.planCache = Objects.requireNonNull(planCache, "planCache");
        this.profileCache = new BoundedLruCache<>(MAX_CACHED_PROFILES);
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public static MinecraftSuburbPlanningService defaults(Logger logger) {
        return new MinecraftSuburbPlanningService(
                SuburbPlanner.defaults(),
                TransformPipeline.of(LightDecayTransform.defaults()),
                logger
        );
    }

    public SuburbDebugPlanResult planAt(ServerLevel level, Vec3 position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");

        SettlementProfileId profileId = new SettlementProfileId(CitiesAriseConfig.debugSettlementProfileId());
        return planAt(
                level,
                blockCoordinate(position.x()),
                blockCoordinate(position.z()),
                profileId,
                TerrainSurveySource.LOADED_WORLD,
                bounds -> new MinecraftTerrainSampler(level).sample(bounds)
        );
    }

    public Optional<SuburbDebugPlanResult> planForWorldgen(
            WorldGenLevel level,
            ChunkGenerator chunkGenerator,
            BlockPos position
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunkGenerator, "chunkGenerator");
        Objects.requireNonNull(position, "position");

        return prepareWorldgenContext(level.getLevel(), chunkGenerator)
                .map(context -> planForWorldgen(context, position));
    }

    public Optional<WorldgenPlanningContext> prepareWorldgenContext(
            ServerLevel level,
            ChunkGenerator chunkGenerator
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunkGenerator, "chunkGenerator");

        SettlementProfileId profileId = new SettlementProfileId(CitiesAriseWorldgenConfig.settlementProfileId());
        Optional<WorldgenSettlementProfileSelection> selection = WorldgenSettlementProfileSelection.from(
                activeProfile(level, profileId)
        );
        if (selection.isEmpty()) {
            return Optional.empty();
        }

        WorldgenSettlementProfileSelection worldgenProfile = selection.get();
        MinecraftWorldgenTerrainProvider terrainProvider = new MinecraftWorldgenTerrainProvider(
                chunkGenerator,
                level.getChunkSource().randomState(),
                level.getMinBuildHeight(),
                level.getHeight()
        );
        return Optional.of(new WorldgenPlanningContext(
                dimensionId(level),
                level.getSeed(),
                profileId,
                worldgenProfile.surveySize(),
                worldgenProfile.planningSettings(),
                terrainProvider::sample,
                CitiesAriseConfig.terrainLoggingEnabled(),
                CitiesAriseConfig.planningLoggingEnabled()
        ));
    }

    public Optional<WorldgenPlanningContext> prepareLocateContext(
            ServerLevel level,
            ChunkGenerator chunkGenerator
    ) {
        Objects.requireNonNull(level, "level");
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("locate context must be prepared on the server thread");
        }
        return prepareWorldgenContext(level, chunkGenerator);
    }

    public SuburbDebugPlanResult planForWorldgen(
            WorldgenPlanningContext context,
            BlockPos position
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(position, "position");

        return planAt(
                context.dimensionId(),
                context.worldSeed(),
                position.getX(),
                position.getZ(),
                context.profileId(),
                context.surveySize(),
                context.planningSettings(),
                TerrainSurveySource.WORLDGEN_BASE,
                context.terrainProvider()::sample,
                context.terrainLoggingEnabled(),
                context.planningLoggingEnabled()
        );
    }

    public SuburbDebugPlanResult planForStructureStart(
            StructurePlanningContext context,
            BlockPos position
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(position, "position");

        SettlementRegion region = SettlementRegion.fromBlockPosition(position.getX(), position.getZ());
        GridBounds bounds = region.surveyBounds(context.surveySize());
        PlanElementId settlementId = settlementId(region);
        long seed = SettlementSeed.forRegion(context.worldSeed(), region, settlementId);
        return createPlan(
                region,
                bounds,
                settlementId,
                seed,
                context.planningSettings(),
                context.terrainProvider()::sample,
                context.terrainLoggingEnabled(),
                context.planningLoggingEnabled()
        );
    }

    private SuburbDebugPlanResult planAt(
            ServerLevel level,
            int blockX,
            int blockZ,
            SettlementProfileId profileId,
            TerrainSurveySource terrainSurveySource,
            Function<GridBounds, TerrainSurvey> surveyFactory
    ) {
        DebugSuburbPlanningConfig config = CitiesAriseConfig.debugSuburbPlanningConfig();
        Optional<SettlementProfile> profile = activeProfile(level, profileId);
        GridSize surveySize = DebugSettlementProfileSelection.surveySize(config, profile);
        SuburbPlanningSettings planningSettings = DebugSettlementProfileSelection.suburbPlanningSettings(config, profile);
        return planAt(
                dimensionId(level),
                level.getSeed(),
                blockX,
                blockZ,
                profileId,
                surveySize,
                planningSettings,
                terrainSurveySource,
                surveyFactory,
                CitiesAriseConfig.terrainLoggingEnabled(),
                CitiesAriseConfig.planningLoggingEnabled()
        );
    }

    private SuburbDebugPlanResult planAt(
            String dimensionId,
            long worldSeed,
            int blockX,
            int blockZ,
            SettlementProfileId profileId,
            GridSize surveySize,
            SuburbPlanningSettings planningSettings,
            TerrainSurveySource terrainSurveySource,
            Function<GridBounds, TerrainSurvey> surveyFactory,
            boolean terrainLoggingEnabled,
            boolean planningLoggingEnabled
    ) {
        SettlementRegion region = SettlementRegion.fromBlockPosition(blockX, blockZ);
        GridBounds bounds = region.surveyBounds(surveySize);
        PlanElementId settlementId = settlementId(region);
        long seed = SettlementSeed.forRegion(worldSeed, region, settlementId);
        RegionPlanCacheKey cacheKey = new RegionPlanCacheKey(
                dimensionId,
                region,
                worldSeed,
                terrainSurveySource,
                profileId,
                surveySize,
                planningSettings
        );

        return planCache.getOrCreate(
                cacheKey,
                () -> createPlan(
                        region,
                        bounds,
                        settlementId,
                        seed,
                        planningSettings,
                        surveyFactory,
                        terrainLoggingEnabled,
                        planningLoggingEnabled
                )
        );
    }

    public void clearCache() {
        planCache.clear();
        profileCache.clear();
    }

    private SuburbDebugPlanResult createPlan(
            SettlementRegion region,
            GridBounds bounds,
            PlanElementId settlementId,
            long seed,
            SuburbPlanningSettings planningSettings,
            Function<GridBounds, TerrainSurvey> surveyFactory,
            boolean terrainLoggingEnabled,
            boolean planningLoggingEnabled
    ) {
        logTerrainStart(region, bounds, seed, settlementId, terrainLoggingEnabled);

        TerrainSurvey survey = surveyFactory.apply(bounds);
        SuburbPlanningRequest request = new SuburbPlanningRequest(
                settlementId,
                survey,
                seed,
                planningSettings
        );
        SuburbPlanningResult result = planner.plan(request);
        SuburbPlanningResult transformedResult = transformService.apply(result, seed);
        SuburbDebugPlanResult debugResult = SuburbDebugPlanResult.from(region, bounds, seed, transformedResult);

        logPlanningResult(debugResult, planningLoggingEnabled);
        return debugResult;
    }

    Optional<SettlementProfile> activeProfile(ServerLevel level, SettlementProfileId profileId) {
        DebugSettlementProfileSelection.SelectionResult result = profileCache.getOrCreate(
                profileId,
                () -> loadProfile(level, profileId)
        );
        return result.profile();
    }

    private DebugSettlementProfileSelection.SelectionResult loadProfile(
            ServerLevel level,
            SettlementProfileId profileId
    ) {
        DebugSettlementProfileSelection.SelectionResult result = DebugSettlementProfileSelection.load(
                () -> profileSource.find(level, profileId)
        );
        logProfileResult(profileId, result);
        return result;
    }

    private static PlanElementId settlementId(SettlementRegion region) {
        return new PlanElementId(CitiesAriseMod.MOD_ID + ":debug_suburb_" + region.x() + "_" + region.z());
    }

    private static int blockCoordinate(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private static String dimensionId(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private void logTerrainStart(
            SettlementRegion region,
            GridBounds bounds,
            long seed,
            PlanElementId settlementId,
            boolean loggingEnabled
    ) {
        if (!loggingEnabled) {
            return;
        }

        logger.info(
                "Sampling terrain for settlementId={}, region=({}, {}), bounds=({}, {}, {}x{}), seed={}.",
                settlementId.value(),
                region.x(),
                region.z(),
                bounds.minX(),
                bounds.minZ(),
                bounds.size().width(),
                bounds.size().depth(),
                seed
        );
    }

    private void logProfileResult(
            SettlementProfileId profileId,
            DebugSettlementProfileSelection.SelectionResult result
    ) {
        if (!CitiesAriseConfig.planningLoggingEnabled()) {
            return;
        }

        if (result.profile().isPresent()) {
            logger.info("Loaded settlement profile {}.", profileId.value());
            return;
        }

        if (result.failed()) {
            logger.warn(
                    "Failed to load settlement profile {}.",
                    profileId.value(),
                    result.error()
            );
            return;
        }

        logger.warn("Settlement profile {} was not found.", profileId.value());
    }

    private void logPlanningResult(SuburbDebugPlanResult result, boolean loggingEnabled) {
        if (!loggingEnabled) {
            return;
        }

        if (!result.successful()) {
            logger.info("Suburb planning rejected: {}.", result.summary());
            return;
        }

        SettlementPlan plan = result.plan();
        logger.info(
                "Suburb planning accepted: {}, roads={}, parcels={}, buildingSlots={}, wornRoads={}, decayedBuildingSlots={}.",
                result.summary(),
                plan.roadGraph().segments().size(),
                plan.parcels().size(),
                plan.buildingSlots().size(),
                result.wornRoadCount(),
                result.decayedBuildingSlotCount()
        );
    }
}
