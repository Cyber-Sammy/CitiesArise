package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.Objects;

public record PlacementChunk(int x, int z) {
    public static final int BLOCK_SIZE = 16;

    public static PlacementChunk containing(GridPoint point) {
        Objects.requireNonNull(point, "point");
        return containing(point.x(), point.z());
    }

    public static PlacementChunk containing(int blockX, int blockZ) {
        return new PlacementChunk(
                Math.floorDiv(blockX, BLOCK_SIZE),
                Math.floorDiv(blockZ, BLOCK_SIZE)
        );
    }

    public boolean contains(GridPoint point) {
        Objects.requireNonNull(point, "point");
        return equals(containing(point));
    }
}
