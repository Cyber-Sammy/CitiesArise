package com.cybersammy.citiesarise.core.road;

public record RoadRoutingCostPolicy(
        int baseStepCost,
        int heightChangePenalty,
        int slopePenalty,
        int roughTerrainPenalty,
        int crossingPenalty
) {
    public RoadRoutingCostPolicy {
        requirePositive(baseStepCost, "baseStepCost");
        requireNonNegative(heightChangePenalty, "heightChangePenalty");
        requireNonNegative(slopePenalty, "slopePenalty");
        requireNonNegative(roughTerrainPenalty, "roughTerrainPenalty");
        requireNonNegative(crossingPenalty, "crossingPenalty");
    }

    public static RoadRoutingCostPolicy defaults() {
        return new RoadRoutingCostPolicy(10, 12, 4, 3, 200);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
