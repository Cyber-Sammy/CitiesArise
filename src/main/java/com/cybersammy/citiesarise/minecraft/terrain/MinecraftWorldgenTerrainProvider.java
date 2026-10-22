package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationColumn;
import com.cybersammy.citiesarise.core.earthwork.TerrainPreparationPlan;
import com.cybersammy.citiesarise.minecraft.planning.WorldgenTerrainSurveyProvider;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Samples generator data without retaining a live level. ChunkGenerator and RandomState are the parallel-worldgen API.
 */
public final class MinecraftWorldgenTerrainProvider implements WorldgenTerrainSurveyProvider {
    private final ChunkGenerator chunkGenerator;
    private final RandomState randomState;
    private final LevelHeightAccessor levelHeight;
    private final MinecraftWorldgenTerrainSampler.TerrainSource cachedSource;
    private final CachedGroundSupport groundSupport;
    private final BatchedNoiseTerrain cells;
    private volatile MinecraftWorldgenTerrainSampler.TerrainSource preliminarySource;

    public MinecraftWorldgenTerrainProvider(
            ChunkGenerator chunkGenerator,
            RandomState randomState,
            int minBuildHeight,
            int worldHeight
    ) {
        this.chunkGenerator = Objects.requireNonNull(chunkGenerator, "chunkGenerator");
        this.randomState = Objects.requireNonNull(randomState, "randomState");
        this.levelHeight = LevelHeightAccessor.create(minBuildHeight, worldHeight);
        cells = BatchedNoiseTerrain.create(chunkGenerator,randomState,levelHeight);
        this.cachedSource = MinecraftWorldgenTerrainSampler.cachedSource(chunkGenerator, randomState, levelHeight,cells);
        this.groundSupport = new CachedGroundSupport(point -> {
            if(cells!=null) return y -> cells.solid(point,y);
            var terrain = chunkGenerator.getBaseColumn(point.x(),point.z(),levelHeight,randomState);
            return y -> y>=levelHeight.getMinBuildHeight() && y<levelHeight.getMaxBuildHeight()
                    && terrain.getBlock(y).blocksMotion() && terrain.getBlock(y).getFluidState().isEmpty();
        });
    }

    BatchedNoiseTerrain batchedNoise() { return cells; }

    @Override
    public TerrainSurvey sample(GridBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        return sampler().sample(bounds);
    }

    @Override
    public Optional<com.cybersammy.citiesarise.core.terrain.TerrainSiteEstimate.Sample> preliminarySample(GridPoint point) {
        var source=preliminarySource;
        if(source==null) synchronized(this) {
            source=preliminarySource;
            if(source==null)preliminarySource=source=MinecraftWorldgenTerrainSampler.cachedSource(chunkGenerator,randomState,levelHeight);
        }
        // Sparse vanilla queries must not populate a full 16x16 noise cell per sample.
        int surface=source.height(point),support=source.supportHeight(point);
        return Optional.of(new com.cybersammy.citiesarise.core.terrain.TerrainSiteEstimate.Sample(support,surface>support));
    }

    @Override
    public Optional<TerrainSurvey> sampleWithExactWaterMask(
            GridBounds bounds,
            Set<GridPoint> waterCheckPoints
    ) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(waterCheckPoints, "waterCheckPoints");
        return Optional.of(sampler().sample(bounds, waterCheckPoints));
    }

    private MinecraftWorldgenTerrainSampler sampler() {
        return new MinecraftWorldgenTerrainSampler(cachedSource);
    }

    @Override
    public Optional<TerrainPreparationColumn> unsupportedColumn(TerrainPreparationPlan plan) {
        for (var column : plan.columns()) {
            if (!groundSupport.supported(column)) {
                return Optional.of(column);
            }
        }
        return Optional.empty();
    }
}
