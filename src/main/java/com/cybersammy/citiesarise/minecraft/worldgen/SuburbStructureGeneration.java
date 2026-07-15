package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseConfig;
import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlanConverter;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import com.cybersammy.citiesarise.minecraft.planning.SuburbDebugPlanResult;
import com.cybersammy.citiesarise.minecraft.planning.WorldgenPlanningContext;
import com.cybersammy.citiesarise.minecraft.profile.ReloadableSettlementProfileStore;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftWorldgenTerrainProvider;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

final class SuburbStructureGeneration {
    private static final String OVERWORLD_DIMENSION_ID = "minecraft:overworld";

    private final MinecraftSuburbPlanningService planningService;
    private final ReloadableSettlementProfileStore profileStore;
    private final DebugPlacementPlanConverter planConverter;
    private final WorldgenRegionCandidateSelector candidateSelector;
    private final Logger logger;

    SuburbStructureGeneration(
            MinecraftSuburbPlanningService planningService,
            ReloadableSettlementProfileStore profileStore,
            Logger logger
    ) {
        this.planningService = Objects.requireNonNull(planningService, "planningService");
        this.profileStore = Objects.requireNonNull(profileStore, "profileStore");
        this.planConverter = new DebugPlacementPlanConverter();
        this.candidateSelector = new WorldgenRegionCandidateSelector();
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    Optional<Generation> create(Structure.GenerationContext context) {
        Objects.requireNonNull(context, "context");
        if (!CitiesAriseWorldgenConfig.enabled()) {
            return Optional.empty();
        }

        BlockPos center = regionCenter(context);
        SettlementRegion region = SettlementRegion.fromBlockPosition(center.getX(), center.getZ());
        if (!candidateSelector.isCandidate(
                context.seed(),
                region,
                CitiesAriseWorldgenConfig.candidateRegionModulo()
        )) {
            return Optional.empty();
        }

        SettlementProfileId profileId = new SettlementProfileId(CitiesAriseWorldgenConfig.settlementProfileId());
        Optional<SettlementProfile> optionalProfile = profileStore.find(profileId);
        if (optionalProfile.isEmpty()) {
            logMissingProfile(profileId);
            return Optional.empty();
        }

        SettlementProfile profile = optionalProfile.get();
        MinecraftWorldgenTerrainProvider terrainProvider = new MinecraftWorldgenTerrainProvider(
                context.chunkGenerator(),
                context.randomState(),
                context.heightAccessor().getMinBuildHeight(),
                context.heightAccessor().getHeight()
        );
        WorldgenPlanningContext planningContext = new WorldgenPlanningContext(
                OVERWORLD_DIMENSION_ID,
                context.seed(),
                profile.id(),
                profile.surveySize(),
                profile.suburbPlanningSettings(),
                terrainProvider::sample,
                CitiesAriseConfig.terrainLoggingEnabled(),
                CitiesAriseConfig.planningLoggingEnabled()
        );
        SuburbDebugPlanResult result = planningService.planForStructureStart(planningContext, center);
        if (!result.successful()) {
            return Optional.empty();
        }

        DebugPlacementPlan placementPlan = result.optionalTerrainPreparationPlan()
                .map(preparationPlan -> planConverter.convert(result.plan(), preparationPlan))
                .orElseGet(() -> planConverter.convert(result.plan()));
        SuburbStructurePlacementSnapshot snapshot = SuburbStructurePlacementSnapshot.from(placementPlan);
        BoundingBox boundingBox = boundingBox(result.surveyBounds(), context);
        return Optional.of(new Generation(center, new CitiesAriseSuburbPiece(boundingBox, snapshot)));
    }

    private static BlockPos regionCenter(Structure.GenerationContext context) {
        int blockX = context.chunkPos().getMiddleBlockX();
        int blockZ = context.chunkPos().getMiddleBlockZ();
        SettlementRegion region = SettlementRegion.fromBlockPosition(blockX, blockZ);
        int centerX = regionCenterCoordinate(region.x());
        int centerZ = regionCenterCoordinate(region.z());
        int centerY = context.chunkGenerator().getSeaLevel();
        return new BlockPos(centerX, centerY, centerZ);
    }

    private static int regionCenterCoordinate(int regionCoordinate) {
        int regionStart = Math.multiplyExact(regionCoordinate, SettlementRegion.REGION_BLOCKS);
        return Math.addExact(regionStart, SettlementRegion.REGION_BLOCKS / 2);
    }

    private static BoundingBox boundingBox(GridBounds bounds, Structure.GenerationContext context) {
        return new BoundingBox(
                bounds.minX(),
                context.heightAccessor().getMinBuildHeight(),
                bounds.minZ(),
                bounds.maxXExclusive() - 1,
                context.heightAccessor().getMaxBuildHeight() - 1,
                bounds.maxZExclusive() - 1
        );
    }

    private void logMissingProfile(SettlementProfileId profileId) {
        if (!CitiesAriseConfig.planningLoggingEnabled()) {
            return;
        }
        logger.warn("Settlement profile {} is unavailable for structure generation.", profileId.value());
    }

    record Generation(BlockPos position, CitiesAriseSuburbPiece piece) {
        Generation {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(piece, "piece");
        }
    }
}
