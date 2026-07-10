package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.minecraft.placement.DebugBlockMaterialProvider;
import com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation;
import com.cybersammy.citiesarise.minecraft.placement.DebugChunkPlacementPlan;
import com.cybersammy.citiesarise.minecraft.placement.VanillaDebugBlockMaterialProvider;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner.SurfaceBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class WorldgenPlacementApplier {
    private static final int UPDATE_FLAGS = 2;

    private final DebugBlockMaterialProvider materialProvider;

    public WorldgenPlacementApplier() {
        this(new VanillaDebugBlockMaterialProvider());
    }

    WorldgenPlacementApplier(DebugBlockMaterialProvider materialProvider) {
        this.materialProvider = Objects.requireNonNull(materialProvider, "materialProvider");
    }

    public int apply(WorldGenLevel level, DebugChunkPlacementPlan placementPlan) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(placementPlan, "placementPlan");

        Map<GridPoint, SurfaceColumn> surfaceColumns = surfaceColumns(level, placementPlan);
        clearVegetation(level, surfaceColumns);

        int placedBlocks = 0;
        for (DebugBlockPlacementOperation operation : placementPlan.operations()) {
            SurfaceColumn column = surfaceColumns.get(operation.point());
            BlockPos position = targetPosition(level, operation, column.placementY());

            if (!level.ensureCanWrite(position)) {
                continue;
            }

            BlockState state = materialProvider.blockState(operation.role());
            if (level.setBlock(position, state, UPDATE_FLAGS)) {
                placedBlocks++;
            }
        }

        return placedBlocks;
    }

    private static Map<GridPoint, SurfaceColumn> surfaceColumns(
            WorldGenLevel level,
            DebugChunkPlacementPlan placementPlan
    ) {
        Map<GridPoint, SurfaceColumn> columns = new LinkedHashMap<>();

        for (DebugBlockPlacementOperation operation : placementPlan.operations()) {
            columns.computeIfAbsent(operation.point(), point -> surfaceColumn(level, point));
        }

        return Map.copyOf(columns);
    }

    private static SurfaceColumn surfaceColumn(WorldGenLevel level, GridPoint point) {
        int topHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, point.x(), point.z());
        MinecraftSurfaceScanner.SurfaceSample surfaceSample = MinecraftSurfaceScanner.scan(
                topHeight,
                level.getMinBuildHeight(),
                y -> surfaceBlock(level, point.x(), y, point.z())
        );
        int placementY = Math.max(level.getMinBuildHeight(), surfaceSample.height() - 1);

        return new SurfaceColumn(point, placementY, topHeight);
    }

    private static SurfaceBlock surfaceBlock(WorldGenLevel level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return new SurfaceBlock(
                state.isAir(),
                state.is(BlockTags.LEAVES),
                state.is(BlockTags.LOGS)
        );
    }

    private static void clearVegetation(WorldGenLevel level, Map<GridPoint, SurfaceColumn> surfaceColumns) {
        for (SurfaceColumn column : surfaceColumns.values()) {
            clearVegetationColumn(level, column);
        }
    }

    private static void clearVegetationColumn(WorldGenLevel level, SurfaceColumn column) {
        for (int y = column.placementY() + 1; y < column.topHeight(); y++) {
            BlockPos position = new BlockPos(column.point().x(), y, column.point().z());

            if (!level.ensureCanWrite(position)) {
                continue;
            }

            BlockState state = level.getBlockState(position);
            if (isVegetation(state)) {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
            }
        }
    }

    private static boolean isVegetation(BlockState state) {
        if (state.is(BlockTags.LEAVES)) {
            return true;
        }

        return state.is(BlockTags.LOGS);
    }

    private static BlockPos targetPosition(
            WorldGenLevel level,
            DebugBlockPlacementOperation operation,
            int placementY
    ) {
        int targetY = clampedY(level, placementY + operation.verticalOffset());
        return new BlockPos(operation.point().x(), targetY, operation.point().z());
    }

    private static int clampedY(WorldGenLevel level, int targetY) {
        if (targetY < level.getMinBuildHeight()) {
            return level.getMinBuildHeight();
        }

        if (targetY >= level.getMaxBuildHeight()) {
            return level.getMaxBuildHeight() - 1;
        }

        return targetY;
    }

    private record SurfaceColumn(GridPoint point, int placementY, int topHeight) {
    }
}
