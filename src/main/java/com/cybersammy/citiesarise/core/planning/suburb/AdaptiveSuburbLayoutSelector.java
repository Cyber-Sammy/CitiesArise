package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.terrain.topology.DevelopableRegion;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopology;
import java.util.Objects;
import java.util.Optional;

final class AdaptiveSuburbLayoutSelector {
    Optional<SuburbLayoutSelection> select(
            GridBounds surveyBounds,
            DevelopmentCapacity capacity,
            GridSize minimumSize,
            TerrainTopology topology,
            SuburbLayout preferredLayout,
            LayoutFactory layoutFactory
    ) {
        Objects.requireNonNull(surveyBounds, "surveyBounds");
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(minimumSize, "minimumSize");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(preferredLayout, "preferredLayout");
        Objects.requireNonNull(layoutFactory, "layoutFactory");

        if (hasCapacity(preferredLayout, capacity.target())) {
            if (isDevelopable(preferredLayout, topology)) {
                return Optional.of(selection(preferredLayout, capacity.target(), topology));
            }
        }

        for (int allocatedCapacity = capacity.target(); allocatedCapacity >= capacity.minimum(); allocatedCapacity--) {
            Optional<SuburbLayoutSelection> selection = selectCapacity(
                    surveyBounds,
                    allocatedCapacity,
                    minimumSize,
                    topology,
                    layoutFactory
            );
            if (selection.isPresent()) {
                return selection;
            }
        }

        return Optional.empty();
    }

    private static Optional<SuburbLayoutSelection> selectCapacity(
            GridBounds surveyBounds,
            int allocatedCapacity,
            GridSize minimumSize,
            TerrainTopology topology,
            LayoutFactory layoutFactory
    ) {
        Optional<GridSize> layoutSize = minimumLayoutSize(
                surveyBounds,
                allocatedCapacity,
                minimumSize,
                layoutFactory
        );
        if (layoutSize.isEmpty()) {
            return Optional.empty();
        }
        return bestCandidate(
                surveyBounds,
                allocatedCapacity,
                layoutSize.orElseThrow(),
                topology,
                layoutFactory
        );
    }

    private static Optional<GridSize> minimumLayoutSize(
            GridBounds surveyBounds,
            int targetParcelCount,
            GridSize minimumSize,
            LayoutFactory layoutFactory
    ) {
        for (int width = minimumSize.width(); width <= surveyBounds.size().width(); width++) {
            GridSize size = new GridSize(width, minimumSize.depth());
            SuburbLayout layout = layoutFactory.create(
                    new GridBounds(surveyBounds.origin(), size),
                    targetParcelCount
            );
            if (hasCapacity(layout, targetParcelCount)) {
                return Optional.of(size);
            }
        }
        return Optional.empty();
    }

    private static Optional<SuburbLayoutSelection> bestCandidate(
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
                SuburbLayout layout = layoutFactory.create(bounds, targetParcelCount);
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
        return Optional.ofNullable(best).map(LayoutCandidate::selection);
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
        SuburbLayoutSelection selection = selection(layout, targetParcelCount, topology);
        return Optional.of(new LayoutCandidate(
                selection,
                region.area(),
                centerDistance(layout.bounds(), surveyBounds),
                layout.bounds().minX(),
                layout.bounds().minZ()
        ));
    }

    private static SuburbLayoutSelection selection(
            SuburbLayout layout,
            int allocatedCapacity,
            TerrainTopology topology
    ) {
        DevelopableRegion region = topology.regionAt(layout.plannedFootprints().getFirst().origin()).orElseThrow();
        return new SuburbLayoutSelection(
                layout,
                new DistrictAnchor(region.id(), anchorPoint(layout.bounds(), region)),
                allocatedCapacity
        );
    }

    private static GridPoint anchorPoint(GridBounds layoutBounds, DevelopableRegion region) {
        GridPoint center = new GridPoint(
                layoutBounds.minX() + (layoutBounds.size().width() / 2),
                layoutBounds.minZ() + (layoutBounds.size().depth() / 2)
        );
        GridPoint best = null;
        long bestDistance = Long.MAX_VALUE;
        for (GridPoint point : region.points()) {
            if (!layoutBounds.contains(point)) {
                continue;
            }
            long distance = manhattanDistance(point, center);
            if (isBetterAnchor(point, distance, best, bestDistance)) {
                best = point;
                bestDistance = distance;
            }
        }
        return Objects.requireNonNull(best, "layout has no developable anchor point");
    }

    private static boolean isBetterAnchor(
            GridPoint candidate,
            long candidateDistance,
            GridPoint current,
            long currentDistance
    ) {
        if (current == null) {
            return true;
        }
        if (candidateDistance != currentDistance) {
            return candidateDistance < currentDistance;
        }
        if (candidate.x() != current.x()) {
            return candidate.x() < current.x();
        }
        return candidate.z() < current.z();
    }

    private static long manhattanDistance(GridPoint first, GridPoint second) {
        return Math.abs((long) first.x() - second.x()) + Math.abs((long) first.z() - second.z());
    }

    private static boolean hasCapacity(SuburbLayout layout, int targetParcelCount) {
        return layout.parcelBounds().size() >= targetParcelCount;
    }

    private static boolean isDevelopable(SuburbLayout layout, TerrainTopology topology) {
        Integer regionId = null;
        for (PotentialTerrainPreparationFootprint footprint : layout.terrainPreparationFootprints()) {
            GridBounds preparationBounds = expandWithin(
                    footprint.bounds(),
                    topology.bounds(),
                    footprint.supportRadius()
            );
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
        SuburbLayout create(GridBounds bounds, int parcelCapacity);
    }

    private record LayoutCandidate(
            SuburbLayoutSelection selection,
            int regionArea,
            long centerDistance,
            int minX,
            int minZ
    ) {
        private LayoutCandidate {
            Objects.requireNonNull(selection, "selection");
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
            if (centerDistance != other.centerDistance) {
                return centerDistance < other.centerDistance;
            }
            if (minX != other.minX) {
                return minX < other.minX;
            }
            return minZ < other.minZ;
        }
    }
}
