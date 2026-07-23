package com.cybersammy.citiesarise.core.terrain.topology;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public final class TerrainTopology {
    private final GridBounds bounds;
    private final List<DevelopableRegion> regions;
    private final List<TerrainBarrier> barriers;
    private final Map<GridPoint, Integer> regionIdsByPoint;
    private final int[][] barrierPrefix;

    TerrainTopology(
            GridBounds bounds,
            List<DevelopableRegion> regions,
            List<TerrainBarrier> barriers,
            Map<GridPoint, Integer> regionIdsByPoint
    ) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.regions = List.copyOf(Objects.requireNonNull(regions, "regions"));
        this.barriers = List.copyOf(Objects.requireNonNull(barriers, "barriers"));
        this.regionIdsByPoint = Map.copyOf(Objects.requireNonNull(regionIdsByPoint, "regionIdsByPoint"));
        this.barrierPrefix = createBarrierPrefix(bounds, this.regionIdsByPoint);
    }

    public GridBounds bounds() {
        return bounds;
    }

    public List<DevelopableRegion> regions() {
        return regions;
    }

    public List<TerrainBarrier> barriers() {
        return barriers;
    }

    public OptionalInt regionIdAt(GridPoint point) {
        Objects.requireNonNull(point, "point");
        Integer regionId = regionIdsByPoint.get(point);
        if (regionId == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(regionId);
    }

    public Optional<DevelopableRegion> regionAt(GridPoint point) {
        OptionalInt regionId = regionIdAt(point);
        if (regionId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(regions.get(regionId.getAsInt()));
    }

    public Optional<DevelopableRegion> largestRegion() {
        DevelopableRegion largest = null;
        for (DevelopableRegion region : regions) {
            if (largest == null || region.area() > largest.area()) {
                largest = region;
            }
        }
        return Optional.ofNullable(largest);
    }

    public boolean isEntirelyDevelopable(GridBounds candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!bounds.contains(candidate)) {
            return false;
        }
        return barrierCount(candidate) == 0;
    }

    private int barrierCount(GridBounds candidate) {
        int minX = candidate.minX() - bounds.minX();
        int minZ = candidate.minZ() - bounds.minZ();
        int maxX = candidate.maxXExclusive() - bounds.minX();
        int maxZ = candidate.maxZExclusive() - bounds.minZ();
        return barrierPrefix[maxZ][maxX]
                - barrierPrefix[minZ][maxX]
                - barrierPrefix[maxZ][minX]
                + barrierPrefix[minZ][minX];
    }

    private static int[][] createBarrierPrefix(
            GridBounds bounds,
            Map<GridPoint, Integer> regionIdsByPoint
    ) {
        int width = bounds.size().width();
        int depth = bounds.size().depth();
        int[][] prefix = new int[depth + 1][width + 1];
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                GridPoint point = new GridPoint(bounds.minX() + x, bounds.minZ() + z);
                int barrier = regionIdsByPoint.containsKey(point) ? 0 : 1;
                prefix[z + 1][x + 1] = barrier
                        + prefix[z][x + 1]
                        + prefix[z + 1][x]
                        - prefix[z][x];
            }
        }
        return prefix;
    }
}
