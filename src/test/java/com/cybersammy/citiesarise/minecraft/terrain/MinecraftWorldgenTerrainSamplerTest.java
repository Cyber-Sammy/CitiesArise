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
        assertEquals(0.0, waterCell.slope());
        assertFalse(plainsCell.water());
        assertEquals(TerrainCategory.BUILDABLE, plainsCell.terrainCategory());
        assertEquals(BiomeCategory.PLAINS, plainsCell.biomeCategory());
        assertEquals(1.0, plainsCell.slope());
    }

    @Test
    void cachesExactHeightSamplesWithinSurvey() {
        FakeTerrainSource source = new FakeTerrainSource();
        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(source);

        sampler.sample(new GridBounds(point(0, 0), new GridSize(8, 8)));

        assertTrue(source.heightCalls.size() <= 100);
        for (int calls : source.heightCalls.values()) {
            assertEquals(1, calls);
        }
    }

    @Test
    void preservesNarrowTerrainDepressions() {
        FakeTerrainSource source = new FakeTerrainSource();
        source.height(point(2, 2), 30);
        MinecraftWorldgenTerrainSampler sampler = new MinecraftWorldgenTerrainSampler(source);

        TerrainSurvey survey = sampler.sample(new GridBounds(point(0, 0), new GridSize(5, 5)));

        assertEquals(30, requiredCell(survey, point(2, 2)).height());
        assertEquals(64, requiredCell(survey, point(1, 2)).height());
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
        private final Map<GridPoint, ColumnSample> columns = new HashMap<>();
        private final Map<GridPoint, String> biomes = new HashMap<>();
        private final Map<GridPoint, Integer> heightCalls = new HashMap<>();

        void height(GridPoint point, int height) {
            heights.put(point, height);
        }

        void column(GridPoint point, ColumnSample column) {
            columns.put(point, column);
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
        public ColumnSample column(GridPoint point, int height, String biomePath) {
            return columns.getOrDefault(point, new ColumnSample(false, false, false, false, false));
        }

        @Override
        public String biomePath(GridPoint point, int height) {
            return biomes.getOrDefault(point, "plains");
        }
    }
}
