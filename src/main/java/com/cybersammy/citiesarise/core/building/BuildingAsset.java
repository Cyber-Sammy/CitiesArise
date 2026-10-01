package com.cybersammy.citiesarise.core.building;

import com.cybersammy.citiesarise.core.geometry.GridSize;

/** Procedural assets stretch within these declared bounds; they never clip a fixed template. */
public enum BuildingAsset {
    PLACEHOLDER("cities_arise:placeholder", 1, Integer.MAX_VALUE, 5),
    COTTAGE("cities_arise:cottage", 5, 32, 7),
    BUNGALOW("cities_arise:bungalow", 5, 32, 7),
    STUDIO("cities_arise:studio", 5, 32, 5);

    private final String id;
    private final int minimumSize;
    private final int maximumSize;
    private final int height;

    BuildingAsset(String id, int minimumSize, int maximumSize, int height) {
        this.id = id;
        this.minimumSize = minimumSize;
        this.maximumSize = maximumSize;
        this.height = height;
    }

    public String id() { return id; }
    public int height() { return height; }

    public boolean fits(GridSize size) {
        return size.width() >= minimumSize && size.depth() >= minimumSize
                && size.width() <= maximumSize && size.depth() <= maximumSize;
    }

    public static BuildingAsset fromId(String id) {
        for (BuildingAsset asset : values()) {
            if (asset.id.equals(id)) return asset;
        }
        throw new IllegalArgumentException("Unknown building asset: " + id);
    }
}
