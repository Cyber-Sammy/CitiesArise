package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.terrain.TerrainCategory;

public final class MinecraftTerrainClassifier {
    private MinecraftTerrainClassifier() {
    }

    public static TerrainCategory classify(
            boolean water,
            boolean lava,
            boolean air,
            boolean leaves,
            boolean logs
    ) {
        if (water) {
            return TerrainCategory.BLOCKED;
        }

        if (lava) {
            return TerrainCategory.BLOCKED;
        }

        if (air) {
            return TerrainCategory.ROUGH;
        }

        if (leaves) {
            return TerrainCategory.ROUGH;
        }

        if (logs) {
            return TerrainCategory.ROUGH;
        }

        return TerrainCategory.BUILDABLE;
    }
}
