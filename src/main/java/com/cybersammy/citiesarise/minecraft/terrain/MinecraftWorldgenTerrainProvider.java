package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
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

    public MinecraftWorldgenTerrainProvider(
            ChunkGenerator chunkGenerator,
            RandomState randomState,
            int minBuildHeight,
            int worldHeight
    ) {
        this.chunkGenerator = Objects.requireNonNull(chunkGenerator, "chunkGenerator");
        this.randomState = Objects.requireNonNull(randomState, "randomState");
        this.levelHeight = LevelHeightAccessor.create(minBuildHeight, worldHeight);
    }

    @Override
    public TerrainSurvey sample(GridBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        return sampler().sample(bounds);
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
        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(
                chunkGenerator,
                randomState,
                levelHeight
        );
        return sampler;
    }
}
