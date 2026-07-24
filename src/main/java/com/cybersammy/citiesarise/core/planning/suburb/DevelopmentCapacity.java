package com.cybersammy.citiesarise.core.planning.suburb;

public record DevelopmentCapacity(int minimum, int target, int maximum) {
    public DevelopmentCapacity {
        requirePositive(minimum, "minimum");
        requirePositive(target, "target");
        requirePositive(maximum, "maximum");
        if (minimum > target) {
            throw new IllegalArgumentException("minimum must not exceed target");
        }
        if (target > maximum) {
            throw new IllegalArgumentException("target must not exceed maximum");
        }
    }

    public static DevelopmentCapacity fixed(int capacity) {
        return new DevelopmentCapacity(capacity, capacity, capacity);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
