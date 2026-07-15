package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.Objects;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Samples generator data without retaining a live level. ChunkGenerator and RandomState are the parallel-worldgen API.
 */
public final class MinecraftWorldgenTerrainProvider {
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

    public TerrainSurvey sample(GridBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(
                chunkGenerator,
                randomState,
                levelHeight
        );
        return sampler.sample(bounds);
    }
}
