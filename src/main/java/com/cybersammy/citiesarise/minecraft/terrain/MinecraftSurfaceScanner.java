package com.cybersammy.citiesarise.minecraft.terrain;

import java.util.Objects;
import java.util.function.IntFunction;

final class MinecraftSurfaceScanner {
    private MinecraftSurfaceScanner() {
    }

    static MinecraftSurfaceSample scan(
            int topHeight,
            int minBuildHeight,
            IntFunction<MinecraftSurfaceBlock> blockAt
    ) {
        Objects.requireNonNull(blockAt, "blockAt");
        boolean leaves = false;
        boolean logs = false;

        for (int y = topHeight - 1; y >= minBuildHeight; y--) {
            MinecraftSurfaceBlock block = blockAt.apply(y);

            if (block.air()) {
                continue;
            }

            if (block.leaves()) {
                leaves = true;
                continue;
            }

            if (block.logs()) {
                logs = true;
                continue;
            }

            return new MinecraftSurfaceSample(y + 1, leaves, logs);
        }

        return new MinecraftSurfaceSample(topHeight, leaves, logs);
    }
}

record MinecraftSurfaceBlock(boolean air, boolean leaves, boolean logs) {
}

record MinecraftSurfaceSample(int height, boolean leaves, boolean logs) {
}
