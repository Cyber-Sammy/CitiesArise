package com.cybersammy.citiesarise.minecraft.worldgen;

final class WorldgenPlacementCoordinates {
    private WorldgenPlacementCoordinates() {
    }

    static int probeChunk(int regionCoordinate, int spacing) {
        if (spacing <= 0) {
            throw new IllegalArgumentException("spacing must be positive");
        }
        return Math.multiplyExact(regionCoordinate, spacing);
    }
}
