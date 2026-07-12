package com.cybersammy.citiesarise.minecraft.worldgen;

import java.util.Objects;
import com.cybersammy.citiesarise.minecraft.placement.DebugBlockMaterialProvider;
import com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

final class MinecraftWorldgenBlockAccess implements WorldgenBlockAccess {
    private static final int UPDATE_FLAGS = 2;

    private final WorldGenLevel level;
    private final DebugBlockMaterialProvider materialProvider;

    MinecraftWorldgenBlockAccess(WorldGenLevel level, DebugBlockMaterialProvider materialProvider) {
        this.level = Objects.requireNonNull(level, "level");
        this.materialProvider = Objects.requireNonNull(materialProvider, "materialProvider");
    }

    @Override
    public int minBuildHeight() {
        return level.getMinBuildHeight();
    }

    @Override
    public int maxBuildHeight() {
        return level.getMaxBuildHeight();
    }

    @Override
    public int surfaceHeight(int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    @Override
    public WorldgenSurfaceMaterial material(WorldgenBlockPosition position) {
        BlockState state = level.getBlockState(toBlockPos(position));
        if (state.isAir()) {
            return WorldgenSurfaceMaterial.AIR;
        }
        if (state.is(net.minecraft.tags.BlockTags.LEAVES)) {
            return WorldgenSurfaceMaterial.LEAVES;
        }
        if (state.is(net.minecraft.tags.BlockTags.LOGS)) {
            return WorldgenSurfaceMaterial.LOGS;
        }
        return WorldgenSurfaceMaterial.OTHER;
    }

    @Override
    public boolean canWrite(WorldgenBlockPosition position) {
        return level.ensureCanWrite(toBlockPos(position));
    }

    @Override
    public boolean clearBlock(WorldgenBlockPosition position) {
        return level.setBlock(toBlockPos(position), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
    }

    @Override
    public boolean placeBlock(WorldgenBlockPosition position, DebugPlacementRole role) {
        return level.setBlock(toBlockPos(position), materialProvider.blockState(role), UPDATE_FLAGS);
    }

    private static BlockPos toBlockPos(WorldgenBlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }
}
