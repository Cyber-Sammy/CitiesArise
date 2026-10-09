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
        // Freeze exact heights before interpolation/slope queries populate the cache.
        // Water-only refinement used to leave narrow dry ravines hidden between samples.
        for (GridPoint point : exactWaterCheckPoints) {
            heights.put(point, exactHeight(point, sampledHeights, terrainSource::height));
        }
        // GridPoint hashes cluster on dense grids; SetN linear probing is costly at city scale.
        var exactPoints = new java.util.HashSet<>(exactWaterCheckPoints);
        return TerrainSurvey.sample(bounds, point -> sampleCell(point, exactPoints));
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

    static TerrainSource cachedSource(ChunkGenerator generator, RandomState random, LevelHeightAccessor height) {
        return new CachedTerrainSource(new ChunkGeneratorTerrainSource(generator, random, height));
    }

    static TerrainSource cachedSource(ChunkGenerator generator, RandomState random, LevelHeightAccessor height,BatchedNoiseTerrain cells) {
        return new CachedTerrainSource(new ChunkGeneratorTerrainSource(generator, random, height,cells));
    }

    /** Immutable generator inputs belong to one provider/seed; only exact samples are shared between refinements. */
    static final class CachedTerrainSource implements TerrainSource {
        private final TerrainSource delegate;
        private final com.cybersammy.citiesarise.minecraft.cache.BoundedLruCache<GridPoint, Integer> heights =
                new com.cybersammy.citiesarise.minecraft.cache.BoundedLruCache<>(65536);
        private final com.cybersammy.citiesarise.minecraft.cache.BoundedLruCache<GridPoint, Integer> supports =
                new com.cybersammy.citiesarise.minecraft.cache.BoundedLruCache<>(65536);
        CachedTerrainSource(TerrainSource delegate) { this.delegate = Objects.requireNonNull(delegate); }
        public int height(GridPoint point) { return heights.getOrCreate(point, () -> delegate.height(point)); }
        public int supportHeight(GridPoint point) { return supports.getOrCreate(point, () -> delegate.supportHeight(point)); }
        public ColumnSample column(GridPoint point, int height, String biomePath) { return delegate.column(point, height, biomePath); }
        public String biomePath(GridPoint point, int height) { return delegate.biomePath(point, height); }
    }

    private static final class ChunkGeneratorTerrainSource implements TerrainSource {
        private final ChunkGenerator chunkGenerator;
        private final RandomState randomState;
        private final LevelHeightAccessor levelHeight;
        private final BatchedNoiseTerrain cells;

        private ChunkGeneratorTerrainSource(
                ChunkGenerator chunkGenerator,
                RandomState randomState,
                LevelHeightAccessor levelHeight
        ) {
            this(chunkGenerator,randomState,levelHeight,null);
        }

        private ChunkGeneratorTerrainSource(ChunkGenerator chunkGenerator,RandomState randomState,
                LevelHeightAccessor levelHeight,BatchedNoiseTerrain cells) {
            this.chunkGenerator = Objects.requireNonNull(chunkGenerator, "chunkGenerator");
            this.randomState = Objects.requireNonNull(randomState, "randomState");
            this.levelHeight = Objects.requireNonNull(levelHeight, "levelHeight");
            this.cells=cells;
        }

        @Override
        public int height(GridPoint point) {
            if(cells!=null) return cells.surface(point);
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
            if(cells!=null) return cells.support(point);
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
