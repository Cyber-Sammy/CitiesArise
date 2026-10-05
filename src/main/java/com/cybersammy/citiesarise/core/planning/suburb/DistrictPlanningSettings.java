package com.cybersammy.citiesarise.core.planning.suburb;

/** Bounded, profile-owned city subdivision. One district preserves legacy planning. */
public record DistrictPlanningSettings(int maxCount, int targetParcels, int maxConnectionAttempts) {
    public DistrictPlanningSettings {
        if (maxCount < 1 || maxCount > 8 || targetParcels < 1 || targetParcels > 32
                || maxConnectionAttempts < 1 || maxConnectionAttempts > 32)
            throw new IllegalArgumentException("district limits: count 1..8, parcels 1..32, connection attempts 1..32");
    }
    public static DistrictPlanningSettings single() { return new DistrictPlanningSettings(1, 4, 8); }
}
