package com.cybersammy.citiesarise.minecraft.terrain;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.minecraft.cache.BoundedLruCache;
import java.util.function.Function;

/** Bounded primitive answers for one generator/seed, without retaining generated columns. */
final class TiledHeightCache {
    private static final int WIDTH = 16;
    private final BoundedLruCache<GridPoint, Tile> tiles;
    private final Function<GridPoint, Heights> sampler;

    TiledHeightCache(Function<GridPoint, Heights> sampler) { this(sampler, 1536); }

    TiledHeightCache(Function<GridPoint, Heights> sampler, int capacity) {
        this.sampler = java.util.Objects.requireNonNull(sampler);
        tiles = new BoundedLruCache<>(capacity);
    }

    Heights get(GridPoint point) {
        var key = new GridPoint(Math.floorDiv(point.x(), WIDTH), Math.floorDiv(point.z(), WIDTH));
        var tile = tiles.getOrCreate(key, Tile::new);
        return tile.get(point, sampler);
    }

    record Heights(int surface, int support) { }

    private static final class Tile {
        private final int[] surface = new int[WIDTH * WIDTH];
        private final int[] support = new int[WIDTH * WIDTH];
        private final java.util.BitSet known = new java.util.BitSet(WIDTH * WIDTH);

        synchronized Heights get(GridPoint point, Function<GridPoint, Heights> sampler) {
            int index = Math.floorMod(point.z(), WIDTH) * WIDTH + Math.floorMod(point.x(), WIDTH);
            if (!known.get(index)) {
                var value = sampler.apply(point);
                surface[index] = value.surface();
                support[index] = value.support();
                known.set(index);
            }
            return new Heights(surface[index], support[index]);
        }
    }
}
