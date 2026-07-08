package com.cybersammy.citiesarise.minecraft.terrain;

import java.util.Objects;
import java.util.function.IntFunction;

public final class MinecraftSurfaceScanner {
    private MinecraftSurfaceScanner() {
    }

    public static SurfaceSample scan(
            int topHeight,
            int minBuildHeight,
            IntFunction<SurfaceBlock> blockAt
    ) {
        Objects.requireNonNull(blockAt, "blockAt");
        boolean leaves = false;
        boolean logs = false;

        for (int y = topHeight - 1; y >= minBuildHeight; y--) {
            SurfaceBlock block = blockAt.apply(y);

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

            return new SurfaceSample(y + 1, leaves, logs);
        }

        return new SurfaceSample(topHeight, leaves, logs);
    }

    public record SurfaceBlock(boolean air, boolean leaves, boolean logs) {
    }

    public record SurfaceSample(int height, boolean leaves, boolean logs) {
    }
}
