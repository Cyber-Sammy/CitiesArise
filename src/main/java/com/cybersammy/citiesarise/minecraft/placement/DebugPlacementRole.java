package com.cybersammy.citiesarise.minecraft.placement;

public enum DebugPlacementRole {
    FOUNDATION,
    ROAD_SURFACE,
    PARCEL_YARD,
    PARCEL_BOUNDARY,
    BUILDING_FLOOR,
    BUILDING_WALL,
    BUILDING_ROOF;

    int priority() {
        return switch (this) {
            case FOUNDATION -> 0;
            case PARCEL_YARD -> 10;
            case PARCEL_BOUNDARY -> 20;
            case BUILDING_FLOOR -> 30;
            case BUILDING_WALL -> 40;
            case BUILDING_ROOF -> 50;
            case ROAD_SURFACE -> 60;
        };
    }
}
