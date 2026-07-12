package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner.SurfaceBlock;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftSurfaceScanner.SurfaceSample;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class MinecraftTerrainSampler {
    private final LevelAccessor level;

    public MinecraftTerrainSampler(LevelAccessor level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    public TerrainSurvey sample(GridBounds bounds) {
        return TerrainSurvey.sample(bounds, this::sampleCell);
    }

    private Optional<TerrainCell> sampleCell(GridPoint point) {
        SurfaceSample surfaceSample = surfaceSample(point.x(), point.z());
        int height = surfaceSample.height();
        boolean water = isWater(point.x(), height, point.z());
        double slope = slope(point, height);
        BiomeCategory biomeCategory = biomeCategory(point.x(), height, point.z());
        TerrainCategory terrainCategory = terrainCategory(point.x(), height, point.z(), water, surfaceSample);

        return Optional.of(new TerrainCell(point, height, water, slope, biomeCategory, terrainCategory));
    }

    private SurfaceSample surfaceSample(int x, int z) {
        int topHeight = topSurfaceHeight(x, z);

        return MinecraftSurfaceScanner.scan(topHeight, level.getMinBuildHeight(), y -> surfaceBlock(x, y, z));
    }

    private SurfaceBlock surfaceBlock(int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));

        return new SurfaceBlock(
                state.isAir(),
                state.is(BlockTags.LEAVES),
                state.is(BlockTags.LOGS)
        );
    }

    private int topSurfaceHeight(int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    private boolean isWater(int x, int height, int z) {
        if (hasWaterAt(x, height, z)) {
            return true;
        }

        return hasWaterAt(x, height - 1, z);
    }

    private boolean hasWaterAt(int x, int y, int z) {
        return level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER);
    }

    private double slope(GridPoint point, int centerHeight) {
        int maxDifference = 0;

        maxDifference = Math.max(maxDifference, heightDifference(centerHeight, point.x() + 1, point.z()));
        maxDifference = Math.max(maxDifference, heightDifference(centerHeight, point.x() - 1, point.z()));
        maxDifference = Math.max(maxDifference, heightDifference(centerHeight, point.x(), point.z() + 1));
        maxDifference = Math.max(maxDifference, heightDifference(centerHeight, point.x(), point.z() - 1));

        return MinecraftSlopeNormalizer.fromHeightDelta(maxDifference);
    }

    private int heightDifference(int centerHeight, int x, int z) {
        return Math.abs(centerHeight - surfaceSample(x, z).height());
    }

    private BiomeCategory biomeCategory(int x, int height, int z) {
        return MinecraftBiomeClassifier.classify(biomePath(x, height, z));
    }

    private String biomePath(int x, int height, int z) {
        return level.getBiome(new BlockPos(x, height, z))
                .unwrapKey()
                .map(ResourceKey::location)
                .map(location -> location.getPath())
                .orElse("");
    }

    private TerrainCategory terrainCategory(int x, int height, int z, boolean water, SurfaceSample surfaceSample) {
        BlockPos surfacePosition = new BlockPos(x, height - 1, z);
        BlockState surfaceState = level.getBlockState(surfacePosition);

        return MinecraftTerrainClassifier.classify(
                water,
                isLava(x, height, z),
                surfaceState.isAir(),
                hasLeaves(surfaceState, surfaceSample),
                hasLogs(surfaceState, surfaceSample)
        );
    }

    private boolean hasLeaves(BlockState surfaceState, SurfaceSample surfaceSample) {
        if (surfaceSample.leaves()) {
            return true;
        }

        return surfaceState.is(BlockTags.LEAVES);
    }

    private boolean hasLogs(BlockState surfaceState, SurfaceSample surfaceSample) {
        if (surfaceSample.logs()) {
            return true;
        }

        return surfaceState.is(BlockTags.LOGS);
    }

    private boolean isLava(int x, int height, int z) {
        if (hasLavaAt(x, height, z)) {
            return true;
        }

        return hasLavaAt(x, height - 1, z);
    }

    private boolean hasLavaAt(int x, int y, int z) {
        return level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.LAVA);
    }
}
