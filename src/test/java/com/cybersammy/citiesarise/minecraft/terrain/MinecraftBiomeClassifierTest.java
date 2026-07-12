package com.cybersammy.citiesarise.minecraft.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import org.junit.jupiter.api.Test;

final class MinecraftBiomeClassifierTest {
    @Test
    void classifiesKnownBiomeFamilies() {
        assertEquals(BiomeCategory.OCEAN, MinecraftBiomeClassifier.classify("deep_ocean"));
        assertEquals(BiomeCategory.OCEAN, MinecraftBiomeClassifier.classify("river"));
        assertEquals(BiomeCategory.SWAMP, MinecraftBiomeClassifier.classify("mangrove_swamp"));
        assertEquals(BiomeCategory.SNOWY, MinecraftBiomeClassifier.classify("snowy_plains"));
        assertEquals(BiomeCategory.MOUNTAIN, MinecraftBiomeClassifier.classify("jagged_peaks"));
        assertEquals(BiomeCategory.DESERT, MinecraftBiomeClassifier.classify("wooded_badlands"));
        assertEquals(BiomeCategory.FOREST, MinecraftBiomeClassifier.classify("dark_forest"));
        assertEquals(BiomeCategory.PLAINS, MinecraftBiomeClassifier.classify("sunflower_plains"));
    }

    @Test
    void normalizesCaseAndReturnsUnknownForUnrecognizedPath() {
        assertEquals(BiomeCategory.FOREST, MinecraftBiomeClassifier.classify("BIRCH_FOREST"));
        assertEquals(BiomeCategory.UNKNOWN, MinecraftBiomeClassifier.classify("custom_biome"));
    }

    @Test
    void rejectsNullPath() {
        assertThrows(NullPointerException.class, () -> MinecraftBiomeClassifier.classify(null));
    }
}
