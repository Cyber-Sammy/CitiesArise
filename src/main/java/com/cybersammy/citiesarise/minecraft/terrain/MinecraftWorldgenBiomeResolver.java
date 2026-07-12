package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

final class MinecraftWorldgenBiomeResolver {
    private MinecraftWorldgenBiomeResolver() {
    }

    static String biomePath(
            ChunkGenerator chunkGenerator,
            RandomState randomState,
            GridPoint point,
            int height
    ) {
        return chunkGenerator.getBiomeSource().getNoiseBiome(
                        QuartPos.fromBlock(point.x()),
                        QuartPos.fromBlock(height),
                        QuartPos.fromBlock(point.z()),
                        randomState.sampler()
                )
                .unwrapKey()
                .map(ResourceKey::location)
                .map(location -> location.getPath())
                .orElse("");
    }
}
