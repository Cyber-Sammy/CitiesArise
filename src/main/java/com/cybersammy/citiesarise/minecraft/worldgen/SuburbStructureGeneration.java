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
    private SuburbDebugPlanResult partitionedSource;
    private PartitionedCityPlacement partitionedPlacement;
    synchronized void clearCache(){partitionedSource=null;partitionedPlacement=null;}

    boolean reservesSpawn(long seed,int x,int z) {
        if(!CitiesAriseWorldgenConfig.enabled())return false;
        var profile=profileStore.find(new SettlementProfileId(CitiesAriseWorldgenConfig.settlementProfileId()));
        if(profile.isEmpty())return false;
        return WorldgenSpawnReservation.contains(seed,x,z,profile.orElseThrow().surveySize(),CitiesAriseWorldgenConfig.candidateRegionModulo());
    }

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
        SettlementProfileId profileId = new SettlementProfileId(CitiesAriseWorldgenConfig.settlementProfileId());
        Optional<SettlementProfile> optionalProfile = profileStore.find(profileId);
        if (optionalProfile.isEmpty()) {
            logMissingProfile(profileId);
            return Optional.empty();
        }

        SettlementProfile profile = optionalProfile.get();
        long worldSeed=StructureWorldSeeds.resolve(context.chunkGenerator(),context.seed());
        var localRegion=region;
        region=com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.parent(region,profile.surveySize());
        if(!candidateSelector.isCandidate(worldSeed,region,CitiesAriseWorldgenConfig.candidateRegionModulo(),profile.surveySize()))return Optional.empty();
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
                worldSeed,
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

        if(com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.partitioned(profile.surveySize())) {
            var prepared=partitioned(result,profile,center);
            var part=prepared.part(localRegion);if(part.isEmpty())return Optional.empty();
            var local=part.orElseThrow();var box=boundingBox(local.snapshot(),profile,context);
            // Adjacent owners belong to one city. A read-only clearance halo must not make
            // their structure pieces overlap in generators which resolve structure collisions.
            var owner=com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.owner(localRegion);
            box=new BoundingBox(Math.max(box.minX(),owner.minX()),box.minY(),Math.max(box.minZ(),owner.minZ()),
                    Math.min(box.maxX(),owner.maxXExclusive()-1),box.maxY(),Math.min(box.maxZ(),owner.maxZExclusive()-1));
            int startX=context.chunkPos().x,startZ=context.chunkPos().z;
            if(Math.floorDiv(box.minX(),16)<startX-8 || Math.floorDiv(box.maxX(),16)>startX+8
                    || Math.floorDiv(box.minZ(),16)<startZ-8 || Math.floorDiv(box.maxZ(),16)>startZ+8)return Optional.empty();
            if(local.snapshot().operations().stream().anyMatch(op->op.platformY().isPresent()
                    && (op.platformY().getAsInt()+op.verticalOffset()<context.heightAccessor().getMinBuildHeight()
                    || op.platformY().getAsInt()+op.verticalOffset()>=context.heightAccessor().getMaxBuildHeight())))return Optional.empty();
            return Optional.of(new Generation(biomePosition(local.snapshot(),context.chunkPos()),new CitiesAriseSuburbPiece(box,local.snapshot(),profile.id().value(),
                    result.plan().id().child("owner-"+localRegion.x()+"-"+localRegion.z()).value(),center.getX(),center.getZ(),local.blueprint(),prepared.metadata)));
        }

        DebugPlacementPlan placementPlan = result.optionalTerrainPreparationPlan()
                .map(preparationPlan -> planConverter.convert(result.plan(), preparationPlan,result.content()))
                .orElseGet(() -> planConverter.convert(result.plan(),result.content()));
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
        return Optional.of(new Generation(biomePosition(snapshot,context.chunkPos()), new CitiesAriseSuburbPiece(boundingBox, snapshot,
                profile.id().value(), result.plan().id().value(), center.getX(), center.getZ(),
                profile.structuralSupports().enabled() && result.optionalTerrainPreparationPlan().isPresent()
                        ? StructuralSupportBlueprint.from(result.plan(),result.optionalTerrainPreparationPlan().orElseThrow(),profile.structuralSupports(),result.content().placementMaterials())
                                .withRecipe(new FinalTerrainPlanningRecipe(profile,result.surveyBounds(),result.seed(),result.plan().id().value()))
                                .withinConstructionBudget(profile.suburbPlanningSettings().maxEarthworkVolume()-result.optionalTerrainPreparationPlan().orElseThrow().constructionVolume()):null)));
    }

    private synchronized PartitionedCityPlacement partitioned(SuburbDebugPlanResult result,SettlementProfile profile,BlockPos center) {
        if(partitionedSource==result)return partitionedPlacement;
        var placement=result.optionalTerrainPreparationPlan().map(p->planConverter.convert(result.plan(),p,result.content()))
                .orElseGet(()->planConverter.convert(result.plan(),result.content()));
        var blueprint=profile.structuralSupports().enabled() && result.optionalTerrainPreparationPlan().isPresent()
                ?StructuralSupportBlueprint.from(result.plan(),result.optionalTerrainPreparationPlan().orElseThrow(),profile.structuralSupports(),result.content().placementMaterials())
                    .withRecipe(new FinalTerrainPlanningRecipe(profile,result.surveyBounds(),result.seed(),result.plan().id().value()))
                    .withinConstructionBudget(profile.suburbPlanningSettings().maxEarthworkVolume()-result.optionalTerrainPreparationPlan().orElseThrow().constructionVolume()):null;
        partitionedSource=result;
        partitionedPlacement=new PartitionedCityPlacement(placement,blueprint,profile.id().value(),result.plan().id().value(),center.getX(),center.getZ());
        return partitionedPlacement;
    }

    java.util.Set<SettlementRegion> requiredStartRegions(SuburbDebugPlanResult result,SettlementProfileId profileId) {
        var profile=profileStore.find(profileId).orElseThrow(()->new IllegalStateException("Structure profile unavailable"));
        if(!com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.partitioned(profile.surveySize()))return java.util.Set.of(result.region());
        var bounds=result.surveyBounds();
        var center=new BlockPos(bounds.minX()+bounds.size().width()/2,0,bounds.minZ()+bounds.size().depth()/2);
        return java.util.Set.copyOf(partitioned(result,profile,center).owners());
    }

    /** Biome eligibility belongs to the occupied local surface, not the parent's sea-level centre. */
    static BlockPos biomePosition(SuburbStructurePlacementSnapshot snapshot,net.minecraft.world.level.ChunkPos chunk) {
        var surface=snapshot.operations().stream().filter(op->op.platformY().isPresent() && switch(op.role()) {
            case PARCEL_YARD,PARCEL_BOUNDARY,BUILDING_FLOOR,ROAD_SURFACE,WORN_ROAD_SURFACE,
                    ROAD_TRANSITION_STEP,BUILDING_ACCESS_SURFACE,BUILDING_ACCESS_STEP,BRIDGE_DECK,BRIDGE_STEP,
                    TERRAIN_SURFACE,TERRAIN_CUT_SURFACE,PARCEL_ALLEY,PARCEL_PATH,ROAD_END_CURB -> true;
            default -> false;
        }).min(java.util.Comparator.comparingLong((SuburbStructurePlacementSnapshot.Operation op)->{
            long dx=(long)op.point().x()-chunk.getMiddleBlockX(),dz=(long)op.point().z()-chunk.getMiddleBlockZ();
            return dx*dx+dz*dz;
        }).thenComparingInt(op->op.point().x()).thenComparingInt(op->op.point().z())
                .thenComparingInt(op->op.platformY().orElseThrow()+op.verticalOffset())).orElseThrow();
        return new BlockPos(surface.point().x(),surface.platformY().orElseThrow()+surface.verticalOffset()+1,surface.point().z());
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
        var reservation=com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.bounds(
                com.cybersammy.citiesarise.minecraft.planning.CityPlanningArea.parent(
                    SettlementRegion.fromBlockPosition(regionCenter(context).getX(),regionCenter(context).getZ()),profile.surveySize()),profile.surveySize());
        boolean full=profile.ravineRoads().enabled() && profile.ravineRoads().mode()==com.cybersammy.citiesarise.core.road.RavineRoadSettings.Mode.FOLLOW_EDGE;
        return new BoundingBox(
                full?reservation.minX():snapshot.minimumX() - WorldgenPlacementPolicy.FINAL_VEGETATION_CLEARANCE_RADIUS,
                full?context.heightAccessor().getMinBuildHeight():minY,
                full?reservation.minZ():snapshot.minimumZ() - WorldgenPlacementPolicy.FINAL_VEGETATION_CLEARANCE_RADIUS,
                full?reservation.maxXExclusive()-1:snapshot.maximumX() + WorldgenPlacementPolicy.FINAL_VEGETATION_CLEARANCE_RADIUS,
                full?context.heightAccessor().getMaxBuildHeight()-1:maxY,
                full?reservation.maxZExclusive()-1:snapshot.maximumZ() + WorldgenPlacementPolicy.FINAL_VEGETATION_CLEARANCE_RADIUS
        );
    }

    private static int minimumStructureY(
            SuburbStructurePlacementSnapshot snapshot,
            SettlementProfile profile,
            Structure.GenerationContext context
    ) {
        int fillDepth = profile.suburbPlanningSettings().maxFillDepth();
        int minimumY = snapshot.minimumPlatformY() + Math.min(-fillDepth - 1, snapshot.minimumVerticalOffset());
        if(profile.structuralSupports().enabled()) minimumY=Math.min(minimumY,
                snapshot.minimumPlatformY()-profile.structuralSupports().maximumDepth());
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
