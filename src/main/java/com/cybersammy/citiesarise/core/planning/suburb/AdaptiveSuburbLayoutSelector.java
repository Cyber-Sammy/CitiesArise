package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.terrain.topology.DevelopableRegion;
import com.cybersammy.citiesarise.core.terrain.topology.TerrainTopology;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class AdaptiveSuburbLayoutSelector {
    private static final int MIN_FINALIZATION_ATTEMPTS_PER_CAPACITY = 6;
    static final int MAX_FINALIZATION_ATTEMPTS_PER_CAPACITY = 6;
    private static final int MAX_FINALIZATION_ATTEMPTS_PER_SIZE = 2;
    private static final List<Integer> DISTRICT_GROWTH_STEPS = List.of(0, 4, 8, 16);

    Optional<SuburbLayoutSelection> select(
            GridBounds surveyBounds,
            DevelopmentCapacity capacity,
            GridSize minimumSize,
            TerrainTopology topology,
            SuburbLayout preferredLayout,
            LayoutFactory layoutFactory,
            LayoutFinalizer layoutFinalizer
    ) {
        Objects.requireNonNull(surveyBounds, "surveyBounds");
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(minimumSize, "minimumSize");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(preferredLayout, "preferredLayout");
        Objects.requireNonNull(layoutFactory, "layoutFactory");
        Objects.requireNonNull(layoutFinalizer, "layoutFinalizer");
        LayoutSearchBudget searchBudget = new LayoutSearchBudget(maximumLayoutFinalizationAttempts(capacity));
        DistrictFootprint.RegionMap regionMap = DistrictFootprint.RegionMap.from(topology);

        if (hasCapacity(preferredLayout, capacity.target())
                && isDevelopable(preferredLayout, topology)
                && searchBudget.visit()) {
            Optional<SuburbLayout> routedPreferred = layoutFinalizer.finalize(preferredLayout);
            if (routedPreferred.isPresent()) {
                SuburbLayout layout = routedPreferred.orElseThrow();
                if (isDevelopable(layout, topology)) {
                    return Optional.of(selection(layout, capacity.target(), topology));
                }
            }
        }

        return selectReducedCapacity(
                surveyBounds,
                capacity,
                minimumSize,
                topology,
                regionMap,
                layoutFactory,
                layoutFinalizer,
                searchBudget
        );
    }

    private static Optional<SuburbLayoutSelection> selectReducedCapacity(
            GridBounds surveyBounds,
            DevelopmentCapacity capacity,
            GridSize minimumSize,
            TerrainTopology topology,
            DistrictFootprint.RegionMap regionMap,
            LayoutFactory layoutFactory,
            LayoutFinalizer layoutFinalizer,
        LayoutSearchBudget searchBudget
    ) {
        for (int allocatedCapacity = capacity.target(); allocatedCapacity >= capacity.minimum(); allocatedCapacity--) {
            int lowerCapacityCount = allocatedCapacity - capacity.minimum();
            LayoutSearchBudget capacityBudget = searchBudget.capacityBudget(lowerCapacityCount);
            Optional<SuburbLayoutSelection> selection = selectCapacity(
                    surveyBounds,
                    allocatedCapacity,
                    minimumSize,
                    topology,
                    regionMap,
                    layoutFactory,
                    layoutFinalizer,
                    capacityBudget
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
            DistrictFootprint.RegionMap regionMap,
            LayoutFactory layoutFactory,
            LayoutFinalizer layoutFinalizer,
            LayoutSearchBudget searchBudget
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
        List<GridSize> sizes = candidateSizes(layoutSize.orElseThrow(), surveyBounds.size());
        List<List<UnroutedLayoutCandidate>> candidatesBySize = new ArrayList<>(sizes.size());
        for (int index = 0; index < sizes.size(); index++) {
            candidatesBySize.add(null);
        }
        for (int attemptIndex = 0; attemptIndex < MAX_FINALIZATION_ATTEMPTS_PER_SIZE; attemptIndex++) {
            for (int sizeIndex = 0; sizeIndex < sizes.size(); sizeIndex++) {
                List<UnroutedLayoutCandidate> candidates = candidatesBySize.get(sizeIndex);
                if (candidates == null) {
                    candidates = finalizationCandidates(unroutedCandidates(
                            surveyBounds,
                            allocatedCapacity,
                            sizes.get(sizeIndex),
                            topology,
                            regionMap,
                            layoutFactory
                    ));
                    candidatesBySize.set(sizeIndex, candidates);
                }
                if (attemptIndex >= candidates.size()) {
                    continue;
                }
                if (!searchBudget.visit()) {
                    return Optional.empty();
                }
                Optional<SuburbLayoutSelection> selection = finalizeCandidate(
                        candidates.get(attemptIndex),
                        allocatedCapacity,
                        topology,
                        layoutFinalizer
                );
                if (selection.isPresent()) {
                    return selection;
                }
            }
        }
        return Optional.empty();
    }

    private static List<GridSize> candidateSizes(GridSize minimum, GridSize maximum) {
        List<GridSize> sizes = new ArrayList<>();
        for (int growth : DISTRICT_GROWTH_STEPS) {
            int width = Math.min(maximum.width(), minimum.width() + growth);
            int depth = Math.min(maximum.depth(), minimum.depth() + growth);
            addUnique(sizes, new GridSize(width, depth));
        }
        addUnique(sizes, maximum);
        return List.copyOf(sizes);
    }

    private static void addUnique(List<GridSize> sizes, GridSize candidate) {
        if (!sizes.contains(candidate)) {
            sizes.add(candidate);
        }
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

    private static List<UnroutedLayoutCandidate> unroutedCandidates(
            GridBounds surveyBounds,
            int targetParcelCount,
            GridSize layoutSize,
            TerrainTopology topology,
            DistrictFootprint.RegionMap regionMap,
            LayoutFactory layoutFactory
    ) {
        List<UnroutedLayoutCandidate> candidates = new ArrayList<>();
        int maxX = surveyBounds.maxXExclusive() - layoutSize.width();
        int maxZ = surveyBounds.maxZExclusive() - layoutSize.depth();
        for (int z = surveyBounds.minZ(); z <= maxZ; z++) {
            for (int x = surveyBounds.minX(); x <= maxX; x++) {
                GridBounds bounds = new GridBounds(new GridPoint(x, z), layoutSize);
                SuburbLayout layout = layoutFactory.create(bounds, targetParcelCount);
                Optional<UnroutedLayoutCandidate> candidate = unroutedCandidate(
                        layout,
                        targetParcelCount,
                        topology,
                        regionMap,
                        surveyBounds
                );
                candidate.ifPresent(candidates::add);
            }
        }
        candidates.sort(UnroutedLayoutCandidate.ORDER);
        return List.copyOf(candidates);
    }

    private static Optional<SuburbLayoutSelection> finalizeCandidate(
            UnroutedLayoutCandidate candidate,
            int targetParcelCount,
            TerrainTopology topology,
            LayoutFinalizer layoutFinalizer
    ) {
        Optional<SuburbLayout> routed = layoutFinalizer.finalize(candidate.layout());
        if (routed.isEmpty()) {
            return Optional.empty();
        }
        SuburbLayout routedLayout = routed.orElseThrow();
        if (isDevelopable(routedLayout, topology)) {
            return Optional.of(selection(routedLayout, targetParcelCount, topology));
        }
        return Optional.empty();
    }

    static int maximumLayoutFinalizationAttempts(DevelopmentCapacity capacity) {
        Objects.requireNonNull(capacity, "capacity");
        int capacityCount = Math.addExact(Math.subtractExact(capacity.target(), capacity.minimum()), 1);
        return Math.addExact(
                Math.multiplyExact(capacityCount, MIN_FINALIZATION_ATTEMPTS_PER_CAPACITY),
                1
        );
    }

    private static List<UnroutedLayoutCandidate> finalizationCandidates(
            List<UnroutedLayoutCandidate> candidates
    ) {
        if (candidates.size() <= MAX_FINALIZATION_ATTEMPTS_PER_SIZE) {
            return List.copyOf(candidates);
        }
        int preferredCount = MAX_FINALIZATION_ATTEMPTS_PER_SIZE / 2;
        boolean[] selected = new boolean[candidates.size()];
        for (int index = 0; index < preferredCount; index++) {
            selected[index] = true;
        }
        int representativeCount = MAX_FINALIZATION_ATTEMPTS_PER_SIZE - preferredCount;
        int remainingCandidates = candidates.size() - preferredCount;
        for (int index = 0; index < representativeCount; index++) {
            int offset = representativeOffset(index, representativeCount, remainingCandidates);
            selected[preferredCount + offset] = true;
        }
        List<UnroutedLayoutCandidate> result = new ArrayList<>(MAX_FINALIZATION_ATTEMPTS_PER_SIZE);
        for (int index = 0; index < candidates.size(); index++) {
            if (selected[index]) {
                result.add(candidates.get(index));
            }
        }
        return List.copyOf(result);
    }

    private static int representativeOffset(int index, int count, int candidateCount) {
        if (count == 1) {
            return candidateCount - 1;
        }
        return (int) (((long) index * (candidateCount - 1)) / (count - 1));
    }

    private static Optional<UnroutedLayoutCandidate> unroutedCandidate(
            SuburbLayout layout,
            int targetParcelCount,
            TerrainTopology topology,
            DistrictFootprint.RegionMap regionMap,
            GridBounds surveyBounds
    ) {
        if (!hasCapacity(layout, targetParcelCount)) {
            return Optional.empty();
        }
        Optional<DistrictFootprint.ComponentSelection> component =
                DistrictFootprint.selectionFromRegionMap(layout.bounds(), regionMap);
        if (component.isEmpty()) {
            return Optional.empty();
        }
        DistrictFootprint.ComponentSelection selected = component.orElseThrow();
        return Optional.of(new UnroutedLayoutCandidate(
                layout,
                selected.area(),
                excludedSupportCells(layout, selected.regionId(), topology, regionMap),
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
        DevelopableRegion region = candidateRegion(layout, topology).orElseThrow();
        return new SuburbLayoutSelection(
                layout,
                new DistrictAnchor(region.id(), anchorPoint(layout.districtFootprint(), region)),
                allocatedCapacity
        );
    }

    private static GridPoint anchorPoint(DistrictFootprint footprint, DevelopableRegion region) {
        GridBounds layoutBounds = footprint.bounds();
        GridPoint center = new GridPoint(
                layoutBounds.minX() + (layoutBounds.size().width() / 2),
                layoutBounds.minZ() + (layoutBounds.size().depth() / 2)
        );
        GridPoint best = null;
        long bestDistance = Long.MAX_VALUE;
        for (GridPoint point : region.points()) {
            if (!footprint.contains(point)) {
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
        if (layout.routedRoadGraph().isEmpty()) {
            return candidateRegion(layout, topology).isPresent();
        }
        Integer regionId = null;
        for (PotentialTerrainPreparationFootprint footprint : layout.terrainPreparationFootprints()) {
            GridBounds preparationBounds = expandWithin(
                    footprint.bounds(),
                    topology.bounds(),
                    footprint.requiredSupportRadius()
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

    private static Optional<DevelopableRegion> candidateRegion(
            SuburbLayout layout,
            TerrainTopology topology
    ) {
        int footprintRegionId = layout.districtFootprint().developableRegionId();
        if (footprintRegionId >= 0) {
            return topology.regions().stream()
                    .filter(region -> region.id() == footprintRegionId)
                    .findFirst();
        }
        GridPoint center = new GridPoint(
                layout.bounds().minX() + (layout.bounds().size().width() / 2),
                layout.bounds().minZ() + (layout.bounds().size().depth() / 2)
        );
        DevelopableRegion best = null;
        long bestDistance = Long.MAX_VALUE;
        for (DevelopableRegion region : topology.regions()) {
            for (GridPoint point : region.points()) {
                if (!layout.bounds().contains(point)) {
                    continue;
                }
                long distance = manhattanDistance(point, center);
                if (best == null || distance < bestDistance) {
                    best = region;
                    bestDistance = distance;
                    continue;
                }
                if (distance == bestDistance && region.id() < best.id()) {
                    best = region;
                }
            }
        }
        return Optional.ofNullable(best);
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

    private static int excludedSupportCells(
            SuburbLayout layout,
            int selectedRegionId,
            TerrainTopology topology,
            DistrictFootprint.RegionMap regionMap
    ) {
        int supportRadius = layout.terrainPreparationFootprints().stream()
                .mapToInt(PotentialTerrainPreparationFootprint::requiredSupportRadius)
                .max()
                .orElse(0);
        if (supportRadius == 0) {
            return 0;
        }
        GridBounds expanded = expandWithin(layout.bounds(), topology.bounds(), supportRadius);
        int excluded = 0;
        for (int z = expanded.minZ(); z < expanded.maxZExclusive(); z++) {
            for (int x = expanded.minX(); x < expanded.maxXExclusive(); x++) {
                if (layout.bounds().contains(new GridPoint(x, z))) {
                    continue;
                }
                if (regionMap.regionIdAt(x, z) != selectedRegionId) {
                    excluded++;
                }
            }
        }
        return excluded;
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

    @FunctionalInterface
    interface LayoutFinalizer {
        Optional<SuburbLayout> finalize(SuburbLayout layout);
    }

    private static final class LayoutSearchBudget {
        private final LayoutSearchBudget parent;
        private int remainingAttempts;

        private LayoutSearchBudget(int maximumAttempts) {
            this(maximumAttempts, null);
        }

        private LayoutSearchBudget(int maximumAttempts, LayoutSearchBudget parent) {
            if (maximumAttempts < 0) {
                throw new IllegalArgumentException("maximumAttempts must not be negative");
            }
            remainingAttempts = maximumAttempts;
            this.parent = parent;
        }

        private LayoutSearchBudget capacityBudget(int lowerCapacityCount) {
            if (parent != null) {
                throw new IllegalStateException("only the root budget can allocate capacity budgets");
            }
            int reservedForLowerCapacities = Math.multiplyExact(
                    lowerCapacityCount,
                    MIN_FINALIZATION_ATTEMPTS_PER_CAPACITY
            );
            int available = Math.max(0, remainingAttempts - reservedForLowerCapacities);
            return new LayoutSearchBudget(
                    Math.min(MAX_FINALIZATION_ATTEMPTS_PER_CAPACITY, available),
                    this
            );
        }

        private boolean visit() {
            if (exhausted()) {
                return false;
            }
            remainingAttempts--;
            if (parent != null && !parent.visit()) {
                throw new IllegalStateException("capacity budget exceeded its root budget");
            }
            return true;
        }

        private boolean exhausted() {
            return remainingAttempts == 0 || (parent != null && parent.exhausted());
        }
    }

    private record UnroutedLayoutCandidate(
            SuburbLayout layout,
            int regionArea,
            int excludedSupportCells,
            long centerDistance,
            int minX,
            int minZ
    ) {
        private static final Comparator<UnroutedLayoutCandidate> ORDER = Comparator
                .comparingInt(UnroutedLayoutCandidate::regionArea)
                .reversed()
                .thenComparingInt(UnroutedLayoutCandidate::excludedSupportCells)
                .thenComparingLong(UnroutedLayoutCandidate::centerDistance)
                .thenComparingInt(UnroutedLayoutCandidate::minX)
                .thenComparingInt(UnroutedLayoutCandidate::minZ);

        private UnroutedLayoutCandidate {
            Objects.requireNonNull(layout, "layout");
            if (regionArea <= 0) {
                throw new IllegalArgumentException("regionArea must be positive");
            }
            if (excludedSupportCells < 0) {
                throw new IllegalArgumentException("excludedSupportCells must not be negative");
            }
            if (centerDistance < 0L) {
                throw new IllegalArgumentException("centerDistance must not be negative");
            }
        }
    }
}
