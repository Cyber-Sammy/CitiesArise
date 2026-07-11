package com.cybersammy.citiesarise.minecraft.worldgen;

interface WorldgenBlockAccess {
    int minBuildHeight();

    int maxBuildHeight();

    int surfaceHeight(int x, int z);

    WorldgenSurfaceMaterial material(WorldgenBlockPosition position);

    boolean canWrite(WorldgenBlockPosition position);

    boolean clearBlock(WorldgenBlockPosition position);

    boolean placeBlock(WorldgenBlockPosition position, com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole role);
}
