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

    CitiesAriseSuburbPiece(BoundingBox boundingBox, SuburbStructurePlacementSnapshot snapshot) {
        this(boundingBox, snapshot, "cities_arise:legacy_unknown", "cities_arise:legacy",
                boundingBox.getCenter().getX(), boundingBox.getCenter().getZ());
    }

    CitiesAriseSuburbPiece(BoundingBox boundingBox, SuburbStructurePlacementSnapshot snapshot,
                          String profileId, String planId, int centerX, int centerZ) {
        super(CitiesAriseWorldgen.SUBURB_PIECE_TYPE.get(), 0, Objects.requireNonNull(boundingBox, "boundingBox"));
        this.profileId = Objects.requireNonNull(profileId);
        this.planId = Objects.requireNonNull(planId);
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.registryTemplate = createRegistryTemplate();
        this.placementIndex = createPlacementIndex(snapshot);
        this.vegetationCleanupIndex = createVegetationCleanupIndex(snapshot);
    }

    CitiesAriseSuburbPiece(CompoundTag tag) {
        super(CitiesAriseWorldgen.SUBURB_PIECE_TYPE.get(), Objects.requireNonNull(tag, "tag"));
        this.snapshot = SuburbStructurePlacementSnapshot.load(tag);
        this.profileId = tag.contains("SettlementProfile") ? tag.getString("SettlementProfile") : "cities_arise:legacy_unknown";
        this.planId = tag.contains("SettlementPlan") ? tag.getString("SettlementPlan") : "cities_arise:legacy";
        this.centerX = tag.contains("SettlementCenterX") ? tag.getInt("SettlementCenterX") : boundingBox.getCenter().getX();
        this.centerZ = tag.contains("SettlementCenterZ") ? tag.getInt("SettlementCenterZ") : boundingBox.getCenter().getZ();
        this.registryTemplate = createRegistryTemplate();
        this.placementIndex = createPlacementIndex(snapshot);
        this.vegetationCleanupIndex = createVegetationCleanupIndex(snapshot);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        snapshot.save(tag);
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
        PlacementChunk chunk = new PlacementChunk(chunkPos.x, chunkPos.z);
        DebugChunkPlacementPlan chunkPlan = placementIndex.slice(chunk);
        if (!chunkPlan.operations().isEmpty()) {
            long started = System.nanoTime();
            new WorldgenPlacementApplier().apply(level, chunkPlan);
            long elapsed = System.nanoTime() - started;
            var serverLevel = level.getLevel();
            var metadata = registryMetadata(serverLevel.dimension().location().toString());
            // Worldgen workers never access SavedData. Publish only after successful placement.
            serverLevel.getServer().execute(() -> SettlementRegistry.get(serverLevel).placed(metadata, chunkPos.toLong(), elapsed));
        }
        WorldgenVegetationCleanupPlan cleanupPlan = vegetationCleanupIndex.slice(chunk);
        if (!cleanupPlan.influencingOperations().isEmpty()) {
            WorldgenVegetationCleanup.enqueue(level.getLevel().dimension(), cleanupPlan);
        }
    }

    Metadata registryMetadata(String dimension) {
        return new Metadata(dimension, profileId, planId, centerX, centerZ,
                registryTemplate.minX(), registryTemplate.minZ(), registryTemplate.maxX(), registryTemplate.maxZ(),
                registryTemplate.placementChunks());
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
