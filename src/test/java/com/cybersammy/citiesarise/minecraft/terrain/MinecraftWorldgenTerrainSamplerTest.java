package com.cybersammy.citiesarise.minecraft.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftWorldgenTerrainSampler.ColumnSample;
import com.cybersammy.citiesarise.minecraft.terrain.MinecraftWorldgenTerrainSampler.TerrainSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MinecraftWorldgenTerrainSamplerTest {
    @Test
    void buildsSurveyFromGeneratorDataWithoutWorldAccess() {
        FakeTerrainSource source = new FakeTerrainSource();
        source.height(point(0, 0), 64);
        source.height(point(4, 0), 68);
        source.height(point(8, 0), 68);
        source.column(point(0, 0), new ColumnSample(true, false, false, false, false));
        source.column(point(4, 0), new ColumnSample(false, false, false, false, false));
        source.biome(point(0, 0), "river");
        source.biome(point(4, 0), "plains");
        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(source);

        TerrainSurvey survey = sampler.sample(new GridBounds(point(0, 0), new GridSize(5, 1)));
        TerrainCell waterCell = requiredCell(survey, point(0, 0));
        TerrainCell plainsCell = requiredCell(survey, point(4, 0));

        assertTrue(waterCell.water());
        assertEquals(TerrainCategory.BLOCKED, waterCell.terrainCategory());
        assertEquals(BiomeCategory.OCEAN, waterCell.biomeCategory());
        assertEquals(0.25, waterCell.slope());
        assertFalse(plainsCell.water());
        assertEquals(TerrainCategory.BUILDABLE, plainsCell.terrainCategory());
        assertEquals(BiomeCategory.PLAINS, plainsCell.biomeCategory());
        assertEquals(0.25, plainsCell.slope());
    }

    @Test
    void cachesSharedHeightSamplesWithinSurvey() {
        FakeTerrainSource source = new FakeTerrainSource();
        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(source);

        sampler.sample(new GridBounds(point(0, 0), new GridSize(8, 8)));

        assertTrue(source.heightCalls.size() <= 25);
        for (int calls : source.heightCalls.values()) {
            assertEquals(1, calls);
        }
        assertTrue(source.supportHeightCalls.isEmpty());
    }

    @Test
    void detectsSwampWaterFromSurfaceAndSupportHeights() {
        FakeTerrainSource source = new FakeTerrainSource();
        for (GridPoint sample : new GridPoint[]{point(0, 0), point(4, 0), point(0, 4), point(4, 4)}) {
            source.height(sample, 64);
            source.supportHeight(sample, 62);
        }
        source.biome(point(0, 0), "swamp");
        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(source);

        TerrainCell cell = requiredCell(
                sampler.sample(
                        new GridBounds(point(0, 0), new GridSize(1, 1)),
                        Set.of(point(0, 0))
                ),
                point(0, 0)
        );

        assertTrue(cell.water());
        assertEquals(BiomeCategory.SWAMP, cell.biomeCategory());
        assertEquals(TerrainCategory.BLOCKED, cell.terrainCategory());
    }

    @Test
    void keepsDrySwampGroundBuildable() {
        FakeTerrainSource source = new FakeTerrainSource();
        source.biome(point(0, 0), "swamp");
        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(source);

        TerrainCell cell = requiredCell(
                sampler.sample(new GridBounds(point(0, 0), new GridSize(1, 1))),
                point(0, 0)
        );

        assertFalse(cell.water());
        assertEquals(BiomeCategory.SWAMP, cell.biomeCategory());
        assertEquals(TerrainCategory.BUILDABLE, cell.terrainCategory());
    }

    @Test
    void detectsOneBlockFluidDepthAtExactColumn() {
        FakeTerrainSource source = new FakeTerrainSource();
        source.supportHeight(point(2, 2), 63);
        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(source);

        TerrainCell cell = requiredCell(
                sampler.sample(
                        new GridBounds(point(2, 2), new GridSize(1, 1)),
                        Set.of(point(2, 2))
                ),
                point(2, 2)
        );

        assertEquals(64, cell.height());
        assertTrue(cell.water());
        assertEquals(TerrainCategory.BLOCKED, cell.terrainCategory());
    }

    @Test
    void detectsSmallPondBetweenSparseHeightSamples() {
        GridPoint pond = point(2, 2);
        FakeTerrainSource source = new FakeTerrainSource();
        source.height(pond, 64);
        source.supportHeight(pond, 62);
        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(source);

        TerrainSurvey survey = sampler.sample(
                new GridBounds(point(0, 0), new GridSize(5, 5)),
                Set.of(pond)
        );

        assertTrue(requiredCell(survey, pond).water());
        assertEquals(TerrainCategory.BLOCKED, requiredCell(survey, pond).terrainCategory());
        assertEquals(1, source.supportHeightCalls.get(pond));
    }

    @Test
    void doesNotExpandCornerWaterOntoDryCheckedColumn() {
        GridPoint dryPoint = point(1, 1);
        FakeTerrainSource source = new FakeTerrainSource();
        source.height(point(0, 0), 64);
        source.supportHeight(point(0, 0), 62);
        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(source);

        TerrainSurvey survey = sampler.sample(
                new GridBounds(point(0, 0), new GridSize(2, 2)),
                Set.of(dryPoint)
        );

        assertFalse(requiredCell(survey, dryPoint).water());
        assertEquals(TerrainCategory.BUILDABLE, requiredCell(survey, dryPoint).terrainCategory());
    }

    @Test
    void preservesDepressionsAtSparseSamplePoints() {
        FakeTerrainSource source = new FakeTerrainSource();
        source.height(point(4, 4), 30);
        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(source);

        TerrainSurvey survey = sampler.sample(new GridBounds(point(0, 0), new GridSize(5, 5)));

        assertEquals(30, requiredCell(survey, point(4, 4)).height());
        assertEquals(47, requiredCell(survey, point(2, 4)).height());
    }

    @Test
    void rejectsNullInputs() {
        assertThrows(NullPointerException.class, () -> new MinecraftWorldgenTerrainSampler((TerrainSource) null));

        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(new FakeTerrainSource());
        assertThrows(NullPointerException.class, () -> sampler.sample(null));
    }

    private static TerrainCell requiredCell(TerrainSurvey survey, GridPoint point) {
        return survey.findCell(point).orElseThrow();
    }

    private static GridPoint point(int x, int z) {
        return new GridPoint(x, z);
    }

    private static final class FakeTerrainSource implements TerrainSource {
        private final Map<GridPoint, Integer> heights = new HashMap<>();
        private final Map<GridPoint, Integer> supportHeights = new HashMap<>();
        private final Map<GridPoint, ColumnSample> columns = new HashMap<>();
        private final Map<GridPoint, String> biomes = new HashMap<>();
        private final Map<GridPoint, Integer> heightCalls = new HashMap<>();
        private final Map<GridPoint, Integer> supportHeightCalls = new HashMap<>();

        void height(GridPoint point, int height) {
            heights.put(point, height);
        }

        void column(GridPoint point, ColumnSample column) {
            columns.put(point, column);
        }

        void supportHeight(GridPoint point, int height) {
            supportHeights.put(point, height);
        }

        void biome(GridPoint point, String biomePath) {
            biomes.put(point, biomePath);
        }

        @Override
        public int height(GridPoint point) {
            heightCalls.merge(point, 1, Integer::sum);
            return heights.getOrDefault(point, 64);
        }

        @Override
        public int supportHeight(GridPoint point) {
            supportHeightCalls.merge(point, 1, Integer::sum);
            return supportHeights.getOrDefault(point, heights.getOrDefault(point, 64));
        }

        @Override
        public ColumnSample column(GridPoint point, int height, String biomePath) {
            return columns.getOrDefault(point, new ColumnSample(false, false, false, false, false));
        }

        @Override
        public String biomePath(GridPoint point, int height) {
            return biomes.getOrDefault(point, "plains");
        }
    }
}
