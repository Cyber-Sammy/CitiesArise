package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.registry.SettlementIndex.Metadata;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementIndex;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementChunkProjector;
import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class CitiesAriseSuburbPiece extends StructurePiece {
    private final SuburbStructurePlacementSnapshot snapshot;
    private final DebugChunkPlacementIndex placementIndex;
    private final WorldgenVegetationCleanupIndex vegetationCleanupIndex;
    private final String profileId;
    private final String planId;
    private final int centerX;
    private final int centerZ;
    private final Metadata registryTemplate;
    private final Metadata sharedParent;
    private final StructuralSupportBlueprint structuralBlueprint;

    CitiesAriseSuburbPiece(BoundingBox boundingBox, SuburbStructurePlacementSnapshot snapshot) {
        this(boundingBox, snapshot, "cities_arise:legacy_unknown", "cities_arise:legacy",
                boundingBox.getCenter().getX(), boundingBox.getCenter().getZ());
    }

    CitiesAriseSuburbPiece(BoundingBox boundingBox, SuburbStructurePlacementSnapshot snapshot,
                          String profileId, String planId, int centerX, int centerZ) {
        this(boundingBox,snapshot,profileId,planId,centerX,centerZ,null);
    }

    CitiesAriseSuburbPiece(BoundingBox boundingBox,SuburbStructurePlacementSnapshot snapshot,
            String profileId,String planId,int centerX,int centerZ,StructuralSupportBlueprint structuralBlueprint) {
        this(boundingBox,snapshot,profileId,planId,centerX,centerZ,structuralBlueprint,null);
    }
    CitiesAriseSuburbPiece(BoundingBox boundingBox,SuburbStructurePlacementSnapshot snapshot,
            String profileId,String planId,int centerX,int centerZ,StructuralSupportBlueprint structuralBlueprint,Metadata parent) {
        super(CitiesAriseWorldgen.SUBURB_PIECE_TYPE.get(), 0, Objects.requireNonNull(boundingBox, "boundingBox"));
        this.structuralBlueprint=structuralBlueprint;
        this.profileId = Objects.requireNonNull(profileId);
        this.planId = Objects.requireNonNull(planId);
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.sharedParent=parent;
        this.registryTemplate = parent==null?createRegistryTemplate():parent;
        this.placementIndex = createPlacementIndex(snapshot);
        this.vegetationCleanupIndex = createVegetationCleanupIndex(snapshot);
    }

    CitiesAriseSuburbPiece(CompoundTag tag) {
        super(CitiesAriseWorldgen.SUBURB_PIECE_TYPE.get(), Objects.requireNonNull(tag, "tag"));
        this.snapshot = SuburbStructurePlacementSnapshot.load(tag);
        this.structuralBlueprint=tag.contains("StructuralBlueprint")?StructuralSupportBlueprint.load(tag.getCompound("StructuralBlueprint")):null;
        this.profileId = tag.contains("SettlementProfile") ? tag.getString("SettlementProfile") : "cities_arise:legacy_unknown";
        this.planId = tag.contains("SettlementPlan") ? tag.getString("SettlementPlan") : "cities_arise:legacy";
        this.centerX = tag.contains("SettlementCenterX") ? tag.getInt("SettlementCenterX") : boundingBox.getCenter().getX();
        this.centerZ = tag.contains("SettlementCenterZ") ? tag.getInt("SettlementCenterZ") : boundingBox.getCenter().getZ();
        this.sharedParent=tag.contains("ParentCity")?SettlementRegistry.readMetadata(tag.getCompound("ParentCity")):null;
        this.registryTemplate = sharedParent==null?createRegistryTemplate():sharedParent;
        this.placementIndex = createPlacementIndex(snapshot);
        this.vegetationCleanupIndex = createVegetationCleanupIndex(snapshot);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        snapshot.save(tag);
        if(sharedParent!=null)tag.put("ParentCity",SettlementRegistry.writeMetadata(sharedParent));
        if(structuralBlueprint!=null) tag.put("StructuralBlueprint",structuralBlueprint.save());
        tag.putString("SettlementProfile", profileId);
        tag.putString("SettlementPlan", planId);
        tag.putInt("SettlementCenterX", centerX);
        tag.putInt("SettlementCenterZ", centerZ);
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            RandomSource random,
            BoundingBox generationBox,
            ChunkPos chunkPos,
            BlockPos pivot
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunkPos, "chunkPos");
        if(structuralBlueprint!=null && structuralBlueprint.settings().enabled()) {
            var server=level.getLevel();
            ServerThreadPublication.publish(server.getServer(),()->PendingStructuralPlacement.get(server)
                    .add(profileId,planId,centerX,centerZ,snapshot,structuralBlueprint,sharedParent));
            return;
        }
        PlacementChunk chunk = new PlacementChunk(chunkPos.x, chunkPos.z);
        DebugChunkPlacementPlan chunkPlan = placementIndex.slice(chunk);
        if (!chunkPlan.operations().isEmpty()) {
            long started = System.nanoTime();
            new WorldgenPlacementApplier().apply(level, chunkPlan,snapshot.toPlacementPlan().operations());
            long elapsed = System.nanoTime() - started;
            var serverLevel = level.getLevel();
            var metadata = registryMetadata(serverLevel.dimension().location().toString());
            // Worldgen workers never access SavedData. Publish only after successful placement.
            ServerThreadPublication.publish(serverLevel.getServer(),
                    () -> SettlementRegistry.get(serverLevel).placed(metadata, chunkPos.toLong(), elapsed));
        }
        WorldgenVegetationCleanupPlan cleanupPlan = vegetationCleanupIndex.slice(chunk);
        if (!cleanupPlan.influencingOperations().isEmpty()) {
            WorldgenVegetationCleanup.enqueue(level.getLevel().dimension(), cleanupPlan);
        }
    }

    Metadata registryMetadata(String dimension) {
        return new Metadata(dimension, registryTemplate.profile(), registryTemplate.planId(), centerX, centerZ,
                registryTemplate.minX(), registryTemplate.minZ(), registryTemplate.maxX(), registryTemplate.maxZ(),
                registryTemplate.placementChunks());
    }

    boolean requiresFinalTerrain() { return structuralBlueprint!=null && structuralBlueprint.settings().enabled(); }

    DebugChunkPlacementPlan carvingProtectionSlice(PlacementChunk chunk) {
        return requiresFinalTerrain()?new DebugChunkPlacementPlan(chunk,java.util.List.of()):placementSlice(chunk);
    }

    DebugChunkPlacementPlan placementSlice(PlacementChunk chunk) {
        return placementIndex.slice(chunk);
    }

    private Metadata createRegistryTemplate() {
        var chunks = snapshot.operations().stream()
                .map(op -> ChunkPos.asLong(Math.floorDiv(op.point().x(), 16), Math.floorDiv(op.point().z(), 16)))
                .collect(java.util.stream.Collectors.toSet());
        return new Metadata("cities_arise:unbound", profileId, planId, centerX, centerZ,
                snapshot.minimumX(), snapshot.minimumZ(), snapshot.maximumX(), snapshot.maximumZ(), chunks);
    }

    private static DebugChunkPlacementIndex createPlacementIndex(SuburbStructurePlacementSnapshot snapshot) {
        return new DebugPlacementChunkProjector().partition(snapshot.toPlacementPlan());
    }

    private static WorldgenVegetationCleanupIndex createVegetationCleanupIndex(
            SuburbStructurePlacementSnapshot snapshot
    ) {
        return new WorldgenVegetationCleanupIndex(snapshot.toPlacementPlan());
    }
}
