package com.cybersammy.citiesarise.minecraft.terrain;

/** Process-wide retained-cache budgets; charges include conservative array/key/entry overhead. */
final class NoiseTerrainCacheBudget {
    static final int WIDTH = 16;
    static final long HEIGHT_BYTES = 12L * 1024 * 1024;
    static final long PRELIMINARY_BYTES = 4L * 1024 * 1024;
    static final long CELL_BYTES = 112L * 1024 * 1024;
    static final int MAX_HEIGHT_ENTRIES = 8192;
    static final int MAX_CELL_ENTRIES = 4096;
    static final int MAX_SAMPLERS = 2;
    static final long HEIGHT_ENTRY_BYTES = 2L * WIDTH * WIDTH * Integer.BYTES + 256;

    private NoiseTerrainCacheBudget() { }

    static boolean fits(int height) {
        if (height <= 0) return false;
        try {
            return cellBytes(height) <= CELL_BYTES;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    static int maskWords(int height) {
        if (height <= 0) throw new IllegalArgumentException("Height must be positive");
        return Math.toIntExact((Math.multiplyExact((long) WIDTH * WIDTH, height) + 63) / 64);
    }

    static long cellBytes(int height) {
        // Height arrays may also be retained separately. Charging them twice is conservative.
        return Math.addExact(HEIGHT_ENTRY_BYTES, Math.multiplyExact((long) maskWords(height), Long.BYTES));
    }
}
