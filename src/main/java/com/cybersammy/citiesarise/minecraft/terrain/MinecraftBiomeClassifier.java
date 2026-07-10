package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import java.util.Locale;
import java.util.Objects;

public final class MinecraftBiomeClassifier {
    private MinecraftBiomeClassifier() {
    }

    public static BiomeCategory classify(String biomePath) {
        String normalizedPath = Objects.requireNonNull(biomePath, "biomePath").toLowerCase(Locale.ROOT);

        if (normalizedPath.contains("ocean")) {
            return BiomeCategory.OCEAN;
        }

        if (normalizedPath.contains("river")) {
            return BiomeCategory.OCEAN;
        }

        if (normalizedPath.contains("swamp")) {
            return BiomeCategory.SWAMP;
        }

        if (isSnowyBiome(normalizedPath)) {
            return BiomeCategory.SNOWY;
        }

        if (isMountainBiome(normalizedPath)) {
            return BiomeCategory.MOUNTAIN;
        }

        if (isDesertBiome(normalizedPath)) {
            return BiomeCategory.DESERT;
        }

        if (isForestBiome(normalizedPath)) {
            return BiomeCategory.FOREST;
        }

        if (normalizedPath.contains("plains")) {
            return BiomeCategory.PLAINS;
        }

        return BiomeCategory.UNKNOWN;
    }

    private static boolean isSnowyBiome(String biomePath) {
        if (biomePath.contains("snow")) {
            return true;
        }

        return biomePath.contains("frozen");
    }

    private static boolean isMountainBiome(String biomePath) {
        if (biomePath.contains("mountain")) {
            return true;
        }

        if (biomePath.contains("peak")) {
            return true;
        }

        return biomePath.contains("slope");
    }

    private static boolean isDesertBiome(String biomePath) {
        if (biomePath.contains("desert")) {
            return true;
        }

        return biomePath.contains("badlands");
    }

    private static boolean isForestBiome(String biomePath) {
        if (biomePath.contains("forest")) {
            return true;
        }

        if (biomePath.contains("jungle")) {
            return true;
        }

        return biomePath.contains("taiga");
    }
}
