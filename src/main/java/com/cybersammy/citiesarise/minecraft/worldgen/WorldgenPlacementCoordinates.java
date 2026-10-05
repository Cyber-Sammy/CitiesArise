package com.cybersammy.citiesarise.minecraft.worldgen;

final class WorldgenPlacementCoordinates {
    static com.cybersammy.citiesarise.core.geometry.GridPoint diagnosticCenter(
            com.cybersammy.citiesarise.minecraft.planning.SettlementRegion region) {
        return new com.cybersammy.citiesarise.core.geometry.GridPoint(
                WorldgenRegionSearch.centerCoordinate(region.x()), WorldgenRegionSearch.centerCoordinate(region.z()));
    }
    private WorldgenPlacementCoordinates() {
    }

    static int probeChunk(int regionCoordinate, int spacing) {
        if (spacing <= 0) {
            throw new IllegalArgumentException("spacing must be positive");
        }
        return Math.multiplyExact(regionCoordinate, spacing);
    }
}
