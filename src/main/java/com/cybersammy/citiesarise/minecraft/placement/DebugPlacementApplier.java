package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner.SurfaceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class DebugPlacementApplier {
    private static final int UPDATE_FLAGS = 3;

    public int apply(ServerLevel level, DebugPlacementPlan placementPlan) {
        int placedBlocks = 0;

        for (DebugBlockPlacementOperation operation : placementPlan.operations()) {
            applyOperation(level, operation);
            placedBlocks++;
        }

        return placedBlocks;
    }

    private void applyOperation(ServerLevel level, DebugBlockPlacementOperation operation) {
        int x = operation.point().x();
        int z = operation.point().z();
        int y = placementY(level, x, z);
        BlockState state = blockState(operation.role());

        level.setBlock(new BlockPos(x, y, z), state, UPDATE_FLAGS);
    }

    private int placementY(ServerLevel level, int x, int z) {
        int topHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        MinecraftSurfaceScanner.SurfaceSample surfaceSample = MinecraftSurfaceScanner.scan(
                topHeight,
                level.getMinBuildHeight(),
                y -> surfaceBlock(level, x, y, z)
        );

        return Math.max(level.getMinBuildHeight(), surfaceSample.height() - 1);
    }

    private SurfaceBlock surfaceBlock(ServerLevel level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));

        return new SurfaceBlock(
                state.isAir(),
                state.is(BlockTags.LEAVES),
                state.is(BlockTags.LOGS)
        );
    }

    private static BlockState blockState(DebugPlacementRole role) {
        return switch (role) {
            case ROAD_SURFACE -> Blocks.STONE_BRICKS.defaultBlockState();
            case PARCEL_MARKER -> Blocks.OAK_PLANKS.defaultBlockState();
            case BUILDING_SLOT_MARKER -> Blocks.YELLOW_CONCRETE.defaultBlockState();
        };
    }
}
