package com.cybersammy.citiesarise.minecraft.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import org.junit.jupiter.api.Test;

final class MinecraftTerrainClassifierTest {
    @Test
    void blocksWater() {
        assertEquals(TerrainCategory.BLOCKED, classifyWater());
    }

    @Test
    void blocksLava() {
        assertEquals(TerrainCategory.BLOCKED, classifyLava());
    }

    @Test
    void treatsLeavesAsRoughTerrain() {
        assertEquals(TerrainCategory.ROUGH, classifyLeaves());
    }

    @Test
    void treatsLogsAsRoughTerrain() {
        assertEquals(TerrainCategory.ROUGH, classifyLogs());
    }

    @Test
    void treatsAirSurfaceAsRoughTerrain() {
        assertEquals(TerrainCategory.ROUGH, classifyAir());
    }

    @Test
    void treatsSolidDrySurfaceAsBuildable() {
        assertEquals(TerrainCategory.BUILDABLE, classifySolidDrySurface());
    }

    private static TerrainCategory classifyWater() {
        return MinecraftTerrainClassifier.classify(true, false, false, false, false);
    }

    private static TerrainCategory classifyLava() {
        return MinecraftTerrainClassifier.classify(false, true, false, false, false);
    }

    private static TerrainCategory classifyLeaves() {
        return MinecraftTerrainClassifier.classify(false, false, false, true, false);
    }

    private static TerrainCategory classifyLogs() {
        return MinecraftTerrainClassifier.classify(false, false, false, false, true);
    }

    private static TerrainCategory classifyAir() {
        return MinecraftTerrainClassifier.classify(false, false, true, false, false);
    }

    private static TerrainCategory classifySolidDrySurface() {
        return MinecraftTerrainClassifier.classify(false, false, false, false, false);
    }
}
