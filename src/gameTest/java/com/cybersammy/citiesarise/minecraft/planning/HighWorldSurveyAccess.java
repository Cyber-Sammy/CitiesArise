package com.cybersammy.citiesarise.minecraft.planning;

/** Development-only access to the exact window partition used by production city planning. */
public final class HighWorldSurveyAccess {
    private HighWorldSurveyAccess() { }
    public static WorldgenTerrainSurveyProvider tiled(WorldgenTerrainSurveyProvider source) {
        return new TiledCityTerrainProvider(source);
    }
}
