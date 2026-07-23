package com.cybersammy.citiesarise.core.terrain.policy;

public enum TerrainPlanningAction {
    RELOCATE,
    PRESERVE_IN_PLACE,
    DIRECT_TERRAFORMING,
    ROUTE_AROUND,
    CROSS,
    STANDARD_PLACEMENT;

    public boolean permitsCurrentPlacement() {
        return this == DIRECT_TERRAFORMING || this == STANDARD_PLACEMENT;
    }
}
