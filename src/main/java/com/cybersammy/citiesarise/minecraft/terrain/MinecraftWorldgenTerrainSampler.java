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
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

public final class MinecraftWorldgenTerrainSampler {
    private static final int HEIGHT_SAMPLE_STEP = 4;

    private final TerrainSource terrainSource;
    private final Map<GridPoint, Integer> heights = new HashMap<>();
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
        Objects.requireNonNull(bounds, "bounds");
        heights.clear();
        biomePaths.clear();
        return TerrainSurvey.sample(bounds, this::sampleCell);
    }

    private Optional<TerrainCell> sampleCell(GridPoint point) {
        int height = height(point);
        String biomePath = biomePath(point, height);
        ColumnSample column = terrainSource.column(point, height, biomePath);
        double slope = slope(point, height);
        BiomeCategory biomeCategory = MinecraftBiomeClassifier.classify(biomePath);
        TerrainCategory terrainCategory = MinecraftTerrainClassifier.classify(
                column.water(),
                column.lava(),
                column.surfaceAir(),
                column.leaves(),
                column.logs()
        );

        return Optional.of(new TerrainCell(
                point,
                height,
                column.water(),
                slope,
                biomeCategory,
                terrainCategory
        ));
    }

    private int height(GridPoint point) {
        return heights.computeIfAbsent(point, this::sampleHeight);
    }

    private int sampleHeight(GridPoint point) {
        return terrainSource.height(point);
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
        public ColumnSample column(GridPoint point, int height, String biomePath) {
            BiomeCategory biomeCategory = MinecraftBiomeClassifier.classify(biomePath);
            boolean water = isWaterSurface(height, chunkGenerator.getSeaLevel(), biomeCategory);
            return new ColumnSample(water, false, false, false, false);
        }

        @Override
        public String biomePath(GridPoint point, int height) {
            return MinecraftWorldgenBiomeResolver.biomePath(chunkGenerator, randomState, point, height);
        }

        private static boolean isWaterSurface(int height, int seaLevel, BiomeCategory biomeCategory) {
            if (height > seaLevel) {
                return false;
            }

            return biomeCategory == BiomeCategory.OCEAN;
        }
    }
}
