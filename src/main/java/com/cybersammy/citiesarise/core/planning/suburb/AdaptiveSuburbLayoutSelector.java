package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.BuildingTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.earthwork.RoadTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.terrain.topology.DevelopableRegion;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopology;
import java.util.Objects;
import java.util.Optional;

final class AdaptiveSuburbLayoutSelector {
    private static final int TERRAIN_SUPPORT_BUFFER = Math.max(
            BuildingTerrainShoulderPolicy.RADIUS,
            RoadTerrainShoulderPolicy.RADIUS
    );

    SuburbLayout select(
            GridBounds surveyBounds,
            int targetParcelCount,
            GridSize minimumSize,
            TerrainTopology topology,
            SuburbLayout preferredLayout,
            LayoutFactory layoutFactory
    ) {
        Objects.requireNonNull(surveyBounds, "surveyBounds");
        if (targetParcelCount <= 0) {
            throw new IllegalArgumentException("targetParcelCount must be positive");
        }
        Objects.requireNonNull(minimumSize, "minimumSize");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(preferredLayout, "preferredLayout");
        Objects.requireNonNull(layoutFactory, "layoutFactory");

        if (isDevelopable(preferredLayout, topology)) {
            return preferredLayout;
        }

        Optional<GridSize> layoutSize = minimumLayoutSize(
                surveyBounds,
                targetParcelCount,
                minimumSize,
                layoutFactory
        );
        if (layoutSize.isEmpty()) {
            return preferredLayout;
        }
        return bestCandidate(
                surveyBounds,
                targetParcelCount,
                layoutSize.orElseThrow(),
                topology,
                layoutFactory
        ).orElse(preferredLayout);
    }

    private static Optional<GridSize> minimumLayoutSize(
            GridBounds surveyBounds,
            int targetParcelCount,
            GridSize minimumSize,
            LayoutFactory layoutFactory
    ) {
        for (int width = minimumSize.width(); width <= surveyBounds.size().width(); width++) {
            GridSize size = new GridSize(width, minimumSize.depth());
            SuburbLayout layout = layoutFactory.create(new GridBounds(surveyBounds.origin(), size));
            if (hasCapacity(layout, targetParcelCount)) {
                return Optional.of(size);
            }
        }
        return Optional.empty();
    }

    private static Optional<SuburbLayout> bestCandidate(
            GridBounds surveyBounds,
            int targetParcelCount,
            GridSize layoutSize,
            TerrainTopology topology,
            LayoutFactory layoutFactory
    ) {
        LayoutCandidate best = null;
        int maxX = surveyBounds.maxXExclusive() - layoutSize.width();
        int maxZ = surveyBounds.maxZExclusive() - layoutSize.depth();
        for (int z = surveyBounds.minZ(); z <= maxZ; z++) {
            for (int x = surveyBounds.minX(); x <= maxX; x++) {
                GridBounds bounds = new GridBounds(new GridPoint(x, z), layoutSize);
                SuburbLayout layout = layoutFactory.create(bounds);
                Optional<LayoutCandidate> candidate = candidate(
                        layout,
                        targetParcelCount,
                        topology,
                        surveyBounds
                );
                if (candidate.isPresent() && candidate.orElseThrow().isBetterThan(best)) {
                    best = candidate.orElseThrow();
                }
            }
        }
        return Optional.ofNullable(best).map(LayoutCandidate::layout);
    }

    private static Optional<LayoutCandidate> candidate(
            SuburbLayout layout,
            int targetParcelCount,
            TerrainTopology topology,
            GridBounds surveyBounds
    ) {
        if (!hasCapacity(layout, targetParcelCount)) {
            return Optional.empty();
        }
        if (!isDevelopable(layout, topology)) {
            return Optional.empty();
        }
        DevelopableRegion region = topology.regionAt(layout.plannedFootprints().getFirst().origin()).orElseThrow();
        return Optional.of(new LayoutCandidate(
                layout,
                region.area(),
                centerDistance(layout.bounds(), surveyBounds)
        ));
    }

    private static boolean hasCapacity(SuburbLayout layout, int targetParcelCount) {
        return layout.parcelBounds().size() >= targetParcelCount;
    }

    private static boolean isDevelopable(SuburbLayout layout, TerrainTopology topology) {
        Integer regionId = null;
        for (GridBounds footprint : layout.plannedFootprints()) {
            GridBounds preparationBounds = expandWithin(footprint, topology.bounds(), TERRAIN_SUPPORT_BUFFER);
            if (!topology.isEntirelyDevelopable(preparationBounds)) {
                return false;
            }
            int footprintRegionId = topology.regionIdAt(preparationBounds.origin()).orElseThrow();
            if (regionId == null) {
                regionId = footprintRegionId;
                continue;
            }
            if (regionId != footprintRegionId) {
                return false;
            }
        }
        return true;
    }

    private static GridBounds expandWithin(GridBounds bounds, GridBounds limit, int radius) {
        int minX = Math.max(limit.minX(), subtractClamped(bounds.minX(), radius));
        int minZ = Math.max(limit.minZ(), subtractClamped(bounds.minZ(), radius));
        int maxX = Math.min(limit.maxXExclusive(), addClamped(bounds.maxXExclusive(), radius));
        int maxZ = Math.min(limit.maxZExclusive(), addClamped(bounds.maxZExclusive(), radius));
        return new GridBounds(
                new GridPoint(minX, minZ),
                new GridSize(maxX - minX, maxZ - minZ)
        );
    }

    private static int subtractClamped(int value, int offset) {
        long result = (long) value - offset;
        return (int) Math.max(Integer.MIN_VALUE, result);
    }

    private static int addClamped(int value, int offset) {
        long result = (long) value + offset;
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    private static long centerDistance(GridBounds first, GridBounds second) {
        long firstCenterX = (long) first.minX() + first.maxXExclusive();
        long firstCenterZ = (long) first.minZ() + first.maxZExclusive();
        long secondCenterX = (long) second.minX() + second.maxXExclusive();
        long secondCenterZ = (long) second.minZ() + second.maxZExclusive();
        return Math.abs(firstCenterX - secondCenterX) + Math.abs(firstCenterZ - secondCenterZ);
    }

    @FunctionalInterface
    interface LayoutFactory {
        SuburbLayout create(GridBounds bounds);
    }

    private record LayoutCandidate(SuburbLayout layout, int regionArea, long centerDistance) {
        private LayoutCandidate {
            Objects.requireNonNull(layout, "layout");
            if (regionArea <= 0) {
                throw new IllegalArgumentException("regionArea must be positive");
            }
            if (centerDistance < 0L) {
                throw new IllegalArgumentException("centerDistance must not be negative");
            }
        }

        private boolean isBetterThan(LayoutCandidate other) {
            if (other == null) {
                return true;
            }
            if (regionArea != other.regionArea) {
                return regionArea > other.regionArea;
            }
            return centerDistance < other.centerDistance;
        }
    }
}
