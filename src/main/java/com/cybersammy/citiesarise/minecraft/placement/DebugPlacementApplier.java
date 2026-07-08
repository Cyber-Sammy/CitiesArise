package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner.SurfaceBlock;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class DebugPlacementApplier {
    private static final int UPDATE_FLAGS = 3;

    private final DebugBlockMaterialProvider materialProvider;
    private final DebugPlacementUndoStore undoStore;

    public DebugPlacementApplier() {
        this(new VanillaDebugBlockMaterialProvider(), new DebugPlacementUndoStore());
    }

    public DebugPlacementApplier(DebugBlockMaterialProvider materialProvider) {
        this(materialProvider, new DebugPlacementUndoStore());
    }

    public DebugPlacementApplier(DebugBlockMaterialProvider materialProvider, DebugPlacementUndoStore undoStore) {
        this.materialProvider = Objects.requireNonNull(materialProvider, "materialProvider");
        this.undoStore = Objects.requireNonNull(undoStore, "undoStore");
    }

    public int apply(ServerLevel level, DebugPlacementPlan placementPlan, boolean undoEnabled) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(placementPlan, "placementPlan");

        int placedBlocks = 0;
        DebugPlacementSnapshotBuilder snapshotBuilder = new DebugPlacementSnapshotBuilder();

        for (DebugBlockPlacementOperation operation : placementPlan.operations()) {
            applyOperation(level, operation, snapshotBuilder);
            placedBlocks++;
        }

        saveUndoSnapshot(snapshotBuilder, undoEnabled);
        return placedBlocks;
    }

    public int undoLast(ServerLevel level) {
        return undoStore.undoLast(level);
    }

    private void saveUndoSnapshot(DebugPlacementSnapshotBuilder snapshotBuilder, boolean undoEnabled) {
        if (!undoEnabled) {
            undoStore.clear();
            return;
        }

        undoStore.save(snapshotBuilder.build());
    }

    private void applyOperation(
            ServerLevel level,
            DebugBlockPlacementOperation operation,
            DebugPlacementSnapshotBuilder snapshotBuilder
    ) {
        int x = operation.point().x();
        int z = operation.point().z();
        int topHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        int baseY = placementY(level, x, z, topHeight);
        int targetY = targetY(level, baseY, operation.verticalOffset());
        BlockState state = materialProvider.blockState(operation.role());
        BlockPos position = new BlockPos(x, targetY, z);

        snapshotBuilder.capture(position, level.getBlockState(position));
        level.setBlock(position, state, UPDATE_FLAGS);
        clearVegetationAbove(level, x, baseY, z, topHeight, snapshotBuilder);
    }

    private int placementY(ServerLevel level, int x, int z, int topHeight) {
        MinecraftSurfaceScanner.SurfaceSample surfaceSample = MinecraftSurfaceScanner.scan(
                topHeight,
                level.getMinBuildHeight(),
                y -> surfaceBlock(level, x, y, z)
        );

        return Math.max(level.getMinBuildHeight(), surfaceSample.height() - 1);
    }

    private static int targetY(ServerLevel level, int baseY, int verticalOffset) {
        int targetY = baseY + verticalOffset;

        if (targetY < level.getMinBuildHeight()) {
            return level.getMinBuildHeight();
        }

        if (targetY >= level.getMaxBuildHeight()) {
            return level.getMaxBuildHeight() - 1;
        }

        return targetY;
    }

    private void clearVegetationAbove(
            ServerLevel level,
            int x,
            int placementY,
            int z,
            int topHeight,
            DebugPlacementSnapshotBuilder snapshotBuilder
    ) {
        for (int y = placementY + 1; y < topHeight; y++) {
            clearVegetationBlock(level, x, y, z, snapshotBuilder);
        }
    }

    private void clearVegetationBlock(
            ServerLevel level,
            int x,
            int y,
            int z,
            DebugPlacementSnapshotBuilder snapshotBuilder
    ) {
        BlockPos position = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(position);

        if (!isVegetation(state)) {
            return;
        }

        snapshotBuilder.capture(position, state);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
    }

    private static boolean isVegetation(BlockState state) {
        if (state.is(BlockTags.LEAVES)) {
            return true;
        }

        return state.is(BlockTags.LOGS);
    }

    private SurfaceBlock surfaceBlock(ServerLevel level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));

        return new SurfaceBlock(
                state.isAir(),
                state.is(BlockTags.LEAVES),
                state.is(BlockTags.LOGS)
        );
    }
}
