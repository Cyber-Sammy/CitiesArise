package com.cybersammy.citiesarise.core.geometry;

public record GridSize(int width, int depth) {
    public GridSize {
        requirePositive(width, "width");
        requirePositive(depth, "depth");
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
