package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.CitiesAriseMod;
import com.cybersammy.citiesarise.config.CitiesAriseConfig;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanner;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningRequest;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningResult;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftTerrainSampler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class MinecraftSuburbPlanningService {
    private static final GridSize DEBUG_SURVEY_SIZE = new GridSize(40, 30);

    private final SuburbPlanner planner;
    private final Logger logger;

    public MinecraftSuburbPlanningService(SuburbPlanner planner, Logger logger) {
        this.planner = planner;
        this.logger = logger;
    }

    public static MinecraftSuburbPlanningService defaults(Logger logger) {
        return new MinecraftSuburbPlanningService(SuburbPlanner.defaults(), logger);
    }

    public SuburbDebugPlanResult planAt(ServerLevel level, Vec3 position) {
        SettlementRegion region = SettlementRegion.fromBlockPosition(blockCoordinate(position.x()), blockCoordinate(position.z()));
        PlanElementId settlementId = settlementId(region);
        GridBounds bounds = region.surveyBounds(DEBUG_SURVEY_SIZE);
        long seed = SettlementSeed.forRegion(level.getSeed(), region, settlementId);

        logTerrainStart(region, bounds, seed, settlementId);

        TerrainSurvey survey = new MinecraftTerrainSampler(level).sample(bounds);
        SuburbPlanningRequest request = new SuburbPlanningRequest(
                settlementId,
                survey,
                seed,
                SuburbPlanningSettings.defaults()
        );
        SuburbPlanningResult result = planner.plan(request);
        SuburbDebugPlanResult debugResult = SuburbDebugPlanResult.from(region, bounds, seed, result);

        logPlanningResult(debugResult);
        return debugResult;
    }

    private static PlanElementId settlementId(SettlementRegion region) {
        return new PlanElementId(CitiesAriseMod.MOD_ID + ":debug_suburb_" + region.x() + "_" + region.z());
    }

    private static int blockCoordinate(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private void logTerrainStart(SettlementRegion region, GridBounds bounds, long seed, PlanElementId settlementId) {
        if (!CitiesAriseConfig.terrainLoggingEnabled()) {
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

    private void logPlanningResult(SuburbDebugPlanResult result) {
        if (!CitiesAriseConfig.planningLoggingEnabled()) {
            return;
        }

        if (!result.successful()) {
            logger.info("Suburb planning rejected: {}.", result.summary());
            return;
        }

        SettlementPlan plan = result.plan();
        logger.info(
                "Suburb planning accepted: {}, roads={}, parcels={}, buildingSlots={}.",
                result.summary(),
                plan.roadGraph().segments().size(),
                plan.parcels().size(),
                plan.buildingSlots().size()
        );
    }
}
