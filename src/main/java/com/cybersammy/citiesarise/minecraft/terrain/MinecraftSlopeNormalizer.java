package com.cybersammy.citiesarise.minecraft.terrain;

public final class MinecraftSlopeNormalizer {
    private static final double BLOCKS_PER_SLOPE_UNIT = 4.0;

    private MinecraftSlopeNormalizer() {
    }

    public static double fromHeightDelta(int heightDelta) {
        if (heightDelta < 0) {
            throw new IllegalArgumentException("heightDelta must not be negative");
        }

        return heightDelta / BLOCKS_PER_SLOPE_UNIT;
    }
}
