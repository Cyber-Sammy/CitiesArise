package com.cybersammy.citiesarise.minecraft.placement;

public enum DebugPlacementRole {
    FOUNDATION(0),
    ROAD_SURFACE(1),
    WORN_ROAD_SURFACE(2),
    PARCEL_YARD(3),
    PARCEL_BOUNDARY(4),
    BUILDING_FLOOR(5),
    BUILDING_WALL(6),
    BUILDING_DOORWAY(7),
    BUILDING_ROOF(8),
    DECAYED_BUILDING_WALL(9),
    DECAYED_BUILDING_ROOF(10),
    TERRAIN_FILL(11),
    TERRAIN_SURFACE(12);

    private final int serializedId;

    DebugPlacementRole(int serializedId) {
        this.serializedId = serializedId;
    }

    public int serializedId() {
        return serializedId;
    }

    public static DebugPlacementRole fromSerializedId(int serializedId) {
        for (DebugPlacementRole role : values()) {
            if (role.serializedId == serializedId) {
                return role;
            }
        }
        throw new IllegalArgumentException("unknown placement role id: " + serializedId);
    }

    int priority() {
        return switch (this) {
            case FOUNDATION -> 0;
            case TERRAIN_FILL -> 0;
            case PARCEL_YARD -> 10;
            case PARCEL_BOUNDARY -> 20;
            case TERRAIN_SURFACE -> 25;
            case BUILDING_FLOOR -> 30;
            case BUILDING_WALL -> 40;
            case DECAYED_BUILDING_WALL -> 40;
            case BUILDING_DOORWAY -> 45;
            case BUILDING_ROOF -> 50;
            case DECAYED_BUILDING_ROOF -> 50;
            case ROAD_SURFACE -> 60;
            case WORN_ROAD_SURFACE -> 60;
        };
    }
}
