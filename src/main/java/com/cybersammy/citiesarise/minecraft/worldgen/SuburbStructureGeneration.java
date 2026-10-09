package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.config.CitiesAriseConfig;
import com.cybersammy.citiesarise.config.CitiesAriseWorldgenConfig;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlanConverter;
import com.cybersammy.citiesarise.minecraft.planning.MinecraftSuburbPlanningService;
import com.cybersammy.citiesarise.minecraft.planning.SettlementRegion;
import com.cybersammy.citiesarise.minecraft.planning.SuburbDebugPlanResult;
import com.cybersammy.citiesarise.minecraft.planning.StructurePlanningContext;
import com.cybersammy.citiesarise.minecraft.profile.ReloadableSettlementProfileStore;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftWorldgenTerrainProvider;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

final class SuburbStructureGeneration {
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
        if(!com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.reachable(region,profile.surveySize(),context.chunkPos().x,context.chunkPos().z)) return Optional.empty();
        var cityCenter=com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.center(region,profile.surveySize());
        center=new BlockPos(cityCenter.x(),center.getY(),cityCenter.z());
        MinecraftWorldgenTerrainProvider terrainProvider = new MinecraftWorldgenTerrainProvider(
                context.chunkGenerator(),
                context.randomState(),
                context.heightAccessor().getMinBuildHeight(),
                context.heightAccessor().getHeight()
        );
        StructurePlanningContext planningContext = new StructurePlanningContext(
                context.seed(),
                profile.id(),
                profile.surveySize(),
                profile.suburbPlanningSettings(),
                profile.terrainResponsePolicy(),
                terrainProvider,
                CitiesAriseConfig.terrainLoggingEnabled(),
                CitiesAriseConfig.planningLoggingEnabled()
        );
        long planningStarted = System.nanoTime();
        SuburbDebugPlanResult result = planningService.planForStructureStart(planningContext, center);
        if (CitiesAriseConfig.planningLoggingEnabled()) logger.info("Structure planning at {}: success={}, elapsedMs={}",
                center, result.successful(), (System.nanoTime() - planningStarted) / 1_000_000.0);
        if (!result.successful()) {
            return Optional.empty();
        }

        DebugPlacementPlan placementPlan = result.optionalTerrainPreparationPlan()
                .map(preparationPlan -> planConverter.convert(result.plan(), preparationPlan))
                .orElseGet(() -> planConverter.convert(result.plan()));
        SuburbStructurePlacementSnapshot snapshot = SuburbStructurePlacementSnapshot.from(placementPlan);
        if (placementPlan.operations().stream().anyMatch(op -> op.platformY().isPresent()
                && (op.platformY().getAsInt() + op.verticalOffset() < context.heightAccessor().getMinBuildHeight()
                || op.platformY().getAsInt() + op.verticalOffset() >= context.heightAccessor().getMaxBuildHeight()))) {
            return Optional.empty();
        }
        BoundingBox boundingBox = boundingBox(
                snapshot,
                profile,
                context
        );
        // A custom structure-set offset must never leave remote chunks outside vanilla reference discovery.
        int startX=context.chunkPos().x,startZ=context.chunkPos().z;
        if(Math.floorDiv(boundingBox.minX(),16)<startX-8 || Math.floorDiv(boundingBox.maxX(),16)>startX+8
                || Math.floorDiv(boundingBox.minZ(),16)<startZ-8 || Math.floorDiv(boundingBox.maxZ(),16)>startZ+8) return Optional.empty();
        return Optional.of(new Generation(center, new CitiesAriseSuburbPiece(boundingBox, snapshot,
                profile.id().value(), result.plan().id().value(), center.getX(), center.getZ())));
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

    private static BoundingBox boundingBox(
            SuburbStructurePlacementSnapshot snapshot,
            SettlementProfile profile,
            Structure.GenerationContext context
    ) {
        int minY = minimumStructureY(snapshot, profile, context);
        int maxY = maximumStructureY(snapshot, profile, context);
        return new BoundingBox(
                snapshot.minimumX() - WorldgenPlacementPolicy.FINAL_VEGETATION_CLEARANCE_RADIUS,
                minY,
                snapshot.minimumZ() - WorldgenPlacementPolicy.FINAL_VEGETATION_CLEARANCE_RADIUS,
                snapshot.maximumX() + WorldgenPlacementPolicy.FINAL_VEGETATION_CLEARANCE_RADIUS,
                maxY,
                snapshot.maximumZ() + WorldgenPlacementPolicy.FINAL_VEGETATION_CLEARANCE_RADIUS
        );
    }

    private static int minimumStructureY(
            SuburbStructurePlacementSnapshot snapshot,
            SettlementProfile profile,
            Structure.GenerationContext context
    ) {
        int fillDepth = profile.suburbPlanningSettings().maxFillDepth();
        int minimumY = snapshot.minimumPlatformY() + Math.min(-fillDepth - 1, snapshot.minimumVerticalOffset());
        return Math.max(context.heightAccessor().getMinBuildHeight(), minimumY);
    }

    private static int maximumStructureY(
            SuburbStructurePlacementSnapshot snapshot,
            SettlementProfile profile,
            Structure.GenerationContext context
    ) {
        int cutDepth = profile.suburbPlanningSettings().maxCutDepth();
        int operationTop = Math.max(snapshot.maximumVerticalOffset(), 0);
        int clearanceTop = cutDepth + WorldgenPlacementPolicy.VEGETATION_CLEARANCE;
        int maximumY = snapshot.maximumPlatformY() + Math.max(operationTop, clearanceTop);
        return Math.min(context.heightAccessor().getMaxBuildHeight() - 1, maximumY);
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
