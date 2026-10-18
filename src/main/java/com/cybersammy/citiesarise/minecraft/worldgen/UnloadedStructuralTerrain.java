package com.cybersammy.citiesarise.minecraft.worldgen;

import com.cybersammy.citiesarise.minecraft.placement.PlacementChunk;

/** Readiness signal; missing terrain must remain pending rather than reject a settlement. */
final class UnloadedStructuralTerrain extends IllegalStateException {
    private final PlacementChunk chunk;

    UnloadedStructuralTerrain(int x,int z) {
        super("Unloaded structural terrain at chunk " + Math.floorDiv(x,16) + "," + Math.floorDiv(z,16));
        chunk=PlacementChunk.containing(x,z);
    }

    PlacementChunk chunk() {return chunk;}
}
