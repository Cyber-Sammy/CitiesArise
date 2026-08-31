package com.cybersammy.citiesarise.minecraft.worldgen;

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

    CitiesAriseSuburbPiece(BoundingBox boundingBox, SuburbStructurePlacementSnapshot snapshot) {
        super(CitiesAriseWorldgen.SUBURB_PIECE_TYPE.get(), 0, Objects.requireNonNull(boundingBox, "boundingBox"));
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.placementIndex = createPlacementIndex(snapshot);
        this.vegetationCleanupIndex = createVegetationCleanupIndex(snapshot);
    }

    CitiesAriseSuburbPiece(CompoundTag tag) {
        super(CitiesAriseWorldgen.SUBURB_PIECE_TYPE.get(), Objects.requireNonNull(tag, "tag"));
        this.snapshot = SuburbStructurePlacementSnapshot.load(tag);
        this.placementIndex = createPlacementIndex(snapshot);
        this.vegetationCleanupIndex = createVegetationCleanupIndex(snapshot);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        snapshot.save(tag);
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
            new WorldgenPlacementApplier().apply(level, chunkPlan);
        }
        WorldgenVegetationCleanupPlan cleanupPlan = vegetationCleanupIndex.slice(chunk);
        if (!cleanupPlan.influencingOperations().isEmpty()) {
            WorldgenVegetationCleanup.enqueue(level.getLevel().dimension(), cleanupPlan);
        }
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
