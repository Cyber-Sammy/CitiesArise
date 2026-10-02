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
    TERRAIN_SURFACE(12),
    ROAD_TRANSITION_STEP(13),
    BUILDING_ACCESS_SURFACE(14),
    BUILDING_ACCESS_STEP(15),
    ROAD_END_CURB(16),
    TERRAIN_RETAINING_WALL(17),
    OAK_HOUSE_WALL(18),
    STONE_HOUSE_WALL(19),
    RED_HOUSE_ROOF(20),
    SLATE_HOUSE_ROOF(21),
    BUILDING_WINDOW(22),
    BUILDING_INTERIOR_AIR(23),
    BUILDING_WORKBENCH(24),
    BUILDING_BOOKSHELF(25),
    BUILDING_CEILING_LIGHT(26),
    DOOR_NORTH_LOWER(27),
    DOOR_NORTH_UPPER(28),
    DOOR_SOUTH_LOWER(29),
    DOOR_SOUTH_UPPER(30),
    DOOR_WEST_LOWER(31),
    DOOR_WEST_UPPER(32),
    DOOR_EAST_LOWER(33),
    DOOR_EAST_UPPER(34),
    CONTENT_BLOCK(35);

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
            case CONTENT_BLOCK -> 50;
            case OAK_HOUSE_WALL, STONE_HOUSE_WALL -> 40;
            case RED_HOUSE_ROOF, SLATE_HOUSE_ROOF, BUILDING_WINDOW, BUILDING_WORKBENCH,
                    BUILDING_BOOKSHELF, BUILDING_CEILING_LIGHT -> 50;
            case DOOR_NORTH_LOWER, DOOR_NORTH_UPPER, DOOR_SOUTH_LOWER, DOOR_SOUTH_UPPER,
                    DOOR_WEST_LOWER, DOOR_WEST_UPPER, DOOR_EAST_LOWER, DOOR_EAST_UPPER -> 45;
            case BUILDING_INTERIOR_AIR -> 35;
            case FOUNDATION -> 0;
            case TERRAIN_FILL -> 0;
            case PARCEL_YARD -> 10;
            case PARCEL_BOUNDARY -> 20;
            case TERRAIN_SURFACE -> 25;
            case TERRAIN_RETAINING_WALL -> 26;
            case BUILDING_FLOOR -> 30;
            case BUILDING_WALL -> 40;
            case DECAYED_BUILDING_WALL -> 40;
            case BUILDING_DOORWAY -> 45;
            case BUILDING_ROOF -> 50;
            case DECAYED_BUILDING_ROOF -> 50;
            case ROAD_SURFACE -> 60;
            case WORN_ROAD_SURFACE -> 60;
            case BUILDING_ACCESS_SURFACE -> 65;
            case BUILDING_ACCESS_STEP -> 70;
            case ROAD_TRANSITION_STEP -> 70;
            case ROAD_END_CURB -> 70;
        };
    }
}
