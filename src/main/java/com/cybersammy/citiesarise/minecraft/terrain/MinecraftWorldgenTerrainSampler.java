package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

public final class MinecraftWorldgenTerrainSampler {
    private static final int HEIGHT_SAMPLE_STEP = 4;

    private final TerrainSource terrainSource;
    private final Map<GridPoint, Integer> heights = new HashMap<>();
    private final Map<GridPoint, Integer> sampledHeights = new HashMap<>();
    private final Map<GridPoint, Integer> sampledSupportHeights = new HashMap<>();
    private final Map<BiomeSampleKey, String> biomePaths = new HashMap<>();

    public MinecraftWorldgenTerrainSampler(
            ChunkGenerator chunkGenerator,
            RandomState randomState,
            LevelHeightAccessor levelHeight
    ) {
        this(new ChunkGeneratorTerrainSource(chunkGenerator, randomState, levelHeight));
    }

    MinecraftWorldgenTerrainSampler(TerrainSource terrainSource) {
        this.terrainSource = Objects.requireNonNull(terrainSource, "terrainSource");
    }

    public TerrainSurvey sample(GridBounds bounds) {
        return sample(bounds, Set.of());
    }

    TerrainSurvey sample(GridBounds bounds, Set<GridPoint> exactWaterCheckPoints) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(exactWaterCheckPoints, "exactWaterCheckPoints");
        heights.clear();
        sampledHeights.clear();
        sampledSupportHeights.clear();
        biomePaths.clear();
        return TerrainSurvey.sample(bounds, point -> sampleCell(point, exactWaterCheckPoints));
    }

    private Optional<TerrainCell> sampleCell(GridPoint point, Set<GridPoint> exactWaterCheckPoints) {
        int height = height(point);
        String biomePath = biomePath(point, height);
        ColumnSample column = terrainSource.column(point, height, biomePath);
        boolean water = column.water() || hasExactFluidSurface(point, exactWaterCheckPoints);
        double slope = slope(point, height);
        BiomeCategory biomeCategory = MinecraftBiomeClassifier.classify(biomePath);
        TerrainCategory terrainCategory = MinecraftTerrainClassifier.classify(
                water,
                column.lava(),
                column.surfaceAir(),
                column.leaves(),
                column.logs()
        );

        return Optional.of(new TerrainCell(
                point,
                height,
                water,
                slope,
                biomeCategory,
                terrainCategory
        ));
    }

    private int height(GridPoint point) {
        return heights.computeIfAbsent(point, this::sampleHeight);
    }

    private int sampleHeight(GridPoint point) {
        return roundedInterpolatedHeight(point, sampledHeights, terrainSource::height);
    }

    private boolean hasExactFluidSurface(GridPoint point, Set<GridPoint> exactWaterCheckPoints) {
        if (!exactWaterCheckPoints.contains(point)) {
            return false;
        }

        int surface = exactHeight(point, sampledHeights, terrainSource::height);
        int support = exactHeight(point, sampledSupportHeights, terrainSource::supportHeight);
        return surface > support;
    }

    private static int exactHeight(
            GridPoint point,
            Map<GridPoint, Integer> samples,
            ToIntFunction<GridPoint> sampler
    ) {
        return samples.computeIfAbsent(point, sampler::applyAsInt);
    }

    private static int roundedInterpolatedHeight(
            GridPoint point,
            Map<GridPoint, Integer> samples,
            ToIntFunction<GridPoint> sampler
    ) {
        return (int) Math.round(interpolatedHeight(point, samples, sampler));
    }

    private static double interpolatedHeight(
            GridPoint point,
            Map<GridPoint, Integer> samples,
            ToIntFunction<GridPoint> sampler
    ) {
        int minX = sampleCoordinate(point.x());
        int minZ = sampleCoordinate(point.z());
        int maxX = Math.addExact(minX, HEIGHT_SAMPLE_STEP);
        int maxZ = Math.addExact(minZ, HEIGHT_SAMPLE_STEP);
        int northWest = sampledHeight(samples, sampler, minX, minZ);
        int northEast = sampledHeight(samples, sampler, maxX, minZ);
        int southWest = sampledHeight(samples, sampler, minX, maxZ);
        int southEast = sampledHeight(samples, sampler, maxX, maxZ);
        double xProgress = progress(point.x(), minX);
        double zProgress = progress(point.z(), minZ);
        double north = interpolate(northWest, northEast, xProgress);
        double south = interpolate(southWest, southEast, xProgress);

        return interpolate(north, south, zProgress);
    }

    private static int sampledHeight(
            Map<GridPoint, Integer> samples,
            ToIntFunction<GridPoint> sampler,
            int x,
            int z
    ) {
        GridPoint point = new GridPoint(x, z);
        return samples.computeIfAbsent(point, ignored -> sampler.applyAsInt(point));
    }

    private String biomePath(GridPoint point, int height) {
        BiomeSampleKey key = new BiomeSampleKey(
                sampleCoordinate(point.x()),
                sampleCoordinate(height),
                sampleCoordinate(point.z())
        );
        return biomePaths.computeIfAbsent(
                key,
                ignoredKey -> terrainSource.biomePath(new GridPoint(key.x(), key.z()), key.y())
        );
    }

    private static int sampleCoordinate(int coordinate) {
        return Math.multiplyExact(Math.floorDiv(coordinate, HEIGHT_SAMPLE_STEP), HEIGHT_SAMPLE_STEP);
    }

    private static double progress(int coordinate, int sampleMinimum) {
        return (coordinate - sampleMinimum) / (double) HEIGHT_SAMPLE_STEP;
    }

    private static double interpolate(double start, double end, double progress) {
        return start + ((end - start) * progress);
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
        return Math.abs(centerHeight - height(new GridPoint(x, z)));
    }

    interface TerrainSource {
        int height(GridPoint point);

        int supportHeight(GridPoint point);

        ColumnSample column(GridPoint point, int height, String biomePath);

        String biomePath(GridPoint point, int height);
    }

    record ColumnSample(boolean water, boolean lava, boolean surfaceAir, boolean leaves, boolean logs) {
    }

    private record BiomeSampleKey(int x, int y, int z) {
    }

    private static final class ChunkGeneratorTerrainSource implements TerrainSource {
        private final ChunkGenerator chunkGenerator;
        private final RandomState randomState;
        private final LevelHeightAccessor levelHeight;

        private ChunkGeneratorTerrainSource(
                ChunkGenerator chunkGenerator,
                RandomState randomState,
                LevelHeightAccessor levelHeight
        ) {
            this.chunkGenerator = Objects.requireNonNull(chunkGenerator, "chunkGenerator");
            this.randomState = Objects.requireNonNull(randomState, "randomState");
            this.levelHeight = Objects.requireNonNull(levelHeight, "levelHeight");
        }

        @Override
        public int height(GridPoint point) {
            return chunkGenerator.getBaseHeight(
                    point.x(),
                    point.z(),
                    Heightmap.Types.WORLD_SURFACE_WG,
                    levelHeight,
                    randomState
            );
        }

        @Override
        public int supportHeight(GridPoint point) {
            return chunkGenerator.getBaseHeight(
                    point.x(),
                    point.z(),
                    Heightmap.Types.OCEAN_FLOOR_WG,
                    levelHeight,
                    randomState
            );
        }

        @Override
        public ColumnSample column(GridPoint point, int height, String biomePath) {
            return new ColumnSample(false, false, false, false, false);
        }

        @Override
        public String biomePath(GridPoint point, int height) {
            return MinecraftWorldgenBiomeResolver.biomePath(chunkGenerator, randomState, point, height);
        }
    }
}
