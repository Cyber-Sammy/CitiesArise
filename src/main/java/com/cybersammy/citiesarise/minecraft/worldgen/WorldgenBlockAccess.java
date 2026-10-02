package com.cybersammy.citiesarise.minecraft.worldgen;

interface WorldgenBlockAccess {
    boolean needsVegetationProtection(com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole role);

    default boolean needsVegetationProtection(com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation operation) {
        return needsVegetationProtection(operation.role());
    }
    default boolean placeOperation(WorldgenBlockPosition position, com.cybersammy.citiesarise.minecraft.placement.DebugBlockPlacementOperation operation) {
        return placeBlock(position,operation.role());
    }
    default boolean placeFill(WorldgenBlockPosition position,com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole role,String material) {
        return placeBlock(position,role);
    }

    int minBuildHeight();

    int maxBuildHeight();

    int surfaceHeight(int x, int z);

    WorldgenSurfaceMaterial material(WorldgenBlockPosition position);

    boolean canWrite(WorldgenBlockPosition position);

    boolean clearBlock(WorldgenBlockPosition position);

    boolean placeBlock(WorldgenBlockPosition position, com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole role);
}
