package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.earthwork.RoadTerrainShoulderPolicy;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanTag;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;

final class TerrainDerivedRoadSkeletonPlanner {
    RoadGraph plan(
            PlanElementId settlementId,
            DistrictFootprint footprint,
            SuburbPlanningSettings settings,
            long seed
    ) {
        Objects.requireNonNull(settlementId, "settlementId");
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(settings, "settings");

        int supportRadius = ((settings.roadWidth() - 1) / 2) + RoadTerrainShoulderPolicy.RADIUS;
        Axis axis = preferredAxis(footprint, supportRadius);
        Line mainLine = bestLine(footprint, axis, supportRadius);
        Random random = new Random(seed);
        int requestedBranchCount = 2 + random.nextInt(3);
        List<Integer> junctionAxes = junctionAxes(
                footprint,
                axis,
                mainLine,
                requestedBranchCount,
                random
        );

        PlanElementId roadsId = settlementId.child("roads");
        PlanElementId startId = roadsId.child("main-start");
        PlanElementId endId = roadsId.child("main-end");
        List<RoadNode> nodes = new ArrayList<>();
        List<RoadSegment> segments = new ArrayList<>();
        nodes.add(roadNode(startId, axis.point(mainLine.minimum(), mainLine.crossAxis()), "main_road"));
        nodes.add(roadNode(endId, axis.point(mainLine.maximum(), mainLine.crossAxis()), "main_road"));

        List<Integer> activeBranches = new ArrayList<>();
        for (int index = 0; index < junctionAxes.size(); index++) {
            int junctionAxis = junctionAxes.get(index);
            GridPoint junction = axis.point(junctionAxis, mainLine.crossAxis());
            PlanElementId junctionId = roadsId.child("side-" + index + "-junction");
            nodes.add(roadNode(junctionId, junction, "side_road"));
            OptionalInt terminalCrossAxis = terminalCrossAxis(
                    footprint,
                    axis,
                    settings,
                    junctionAxis,
                    mainLine.crossAxis(),
                    index % 2 == 0
            );
            if (terminalCrossAxis.isEmpty()) {
                continue;
            }
            nodes.add(roadNode(
                    roadsId.child("side-" + index + "-dead-end"),
                    axis.point(junctionAxis, terminalCrossAxis.getAsInt()),
                    "dead_end"
            ));
            activeBranches.add(index);
        }

        List<PlanElementId> mainPath = new ArrayList<>();
        mainPath.add(startId);
        for (int index = 0; index < junctionAxes.size(); index++) {
            mainPath.add(roadsId.child("side-" + index + "-junction"));
        }
        mainPath.add(endId);
        for (int index = 0; index < mainPath.size() - 1; index++) {
            segments.add(roadSegment(
                    roadsId.child("main-" + index),
                    mainPath.get(index),
                    mainPath.get(index + 1),
                    settings.roadWidth(),
                    "main_road"
            ));
        }
        for (int index : activeBranches) {
            segments.add(roadSegment(
                    roadsId.child("side-" + index),
                    roadsId.child("side-" + index + "-junction"),
                    roadsId.child("side-" + index + "-dead-end"),
                    settings.roadWidth(),
                    "side_road",
                    "dead_end"
            ));
        }
        return new RoadGraph(nodes, segments);
    }

    private static Axis preferredAxis(DistrictFootprint footprint, int supportRadius) {
        Line horizontal = bestLine(footprint, Axis.HORIZONTAL, supportRadius);
        Line vertical = bestLine(footprint, Axis.VERTICAL, supportRadius);
        int comparison = Line.QUALITY.compare(horizontal, vertical);
        if (comparison < 0) {
            return Axis.HORIZONTAL;
        }
        if (comparison > 0) {
            return Axis.VERTICAL;
        }
        return footprint.bounds().size().width() >= footprint.bounds().size().depth()
                ? Axis.HORIZONTAL
                : Axis.VERTICAL;
    }

    private static Line bestLine(DistrictFootprint footprint, Axis axis, int supportRadius) {
        int center = axis.crossCenter(footprint);
        return axis.occupiedCrossAxes(footprint).stream()
                .map(crossAxis -> line(footprint, axis, crossAxis, center, supportRadius))
                .min(Line.QUALITY)
                .orElseThrow();
    }

    private static Line line(
            DistrictFootprint footprint,
            Axis axis,
            int crossAxis,
            int center,
            int supportRadius
    ) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        int pointCount = 0;
        if (footprint.rectangular()) {
            minimum = axis.minimum(footprint);
            maximum = axis.maximum(footprint);
            pointCount = (maximum - minimum) + 1;
        } else {
            for (GridPoint point : footprint.points()) {
                if (axis.cross(point) != crossAxis) {
                    continue;
                }
                int value = axis.along(point);
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
                pointCount++;
            }
        }
        if (pointCount == 0) {
            throw new IllegalArgumentException("footprint line has no points");
        }
        int supportedPointCount = 0;
        for (int value = minimum; value <= maximum; value++) {
            if (hasCrossSectionSupport(footprint, axis, value, crossAxis, supportRadius)) {
                supportedPointCount++;
            }
        }
        return new Line(
                crossAxis,
                minimum,
                maximum,
                supportedPointCount,
                pointCount,
                Math.abs(crossAxis - center)
        );
    }

    private static boolean hasCrossSectionSupport(
            DistrictFootprint footprint,
            Axis axis,
            int along,
            int cross,
            int supportRadius
    ) {
        for (int offset = -supportRadius; offset <= supportRadius; offset++) {
            GridPoint point = axis.point(along, cross + offset);
            if (footprint.bounds().contains(point) && !footprint.contains(point)) {
                return false;
            }
        }
        return true;
    }

    private static List<Integer> junctionAxes(
            DistrictFootprint footprint,
            Axis axis,
            Line mainLine,
            int count,
            Random random
    ) {
        int availableInteriorPoints = Math.max(0, mainLine.length() - 2);
        count = Math.min(count, availableInteriorPoints);
        if (count == 0) {
            return List.of();
        }
        int spacing = mainLine.length() / (count + 1);
        int jitterRadius = Math.max(1, spacing / 3);
        List<Integer> result = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            int jitter = random.nextInt((jitterRadius * 2) + 1) - jitterRadius;
            int desired = Math.max(
                    mainLine.minimum() + 1,
                    Math.min(mainLine.maximum() - 1, mainLine.minimum() + (spacing * index) + jitter)
            );
            int selected = nearestAxis(footprint, axis, mainLine.crossAxis(), desired);
            if (!result.contains(selected)) {
                result.add(selected);
            }
        }
        result.sort(Integer::compareTo);
        return List.copyOf(result);
    }

    private static int nearestAxis(
            DistrictFootprint footprint,
            Axis axis,
            int crossAxis,
            int desired
    ) {
        if (footprint.rectangular()) {
            return desired;
        }
        return footprint.points().stream()
                .filter(point -> axis.cross(point) == crossAxis)
                .map(axis::along)
                .min(Comparator
                        .comparingInt((Integer value) -> Math.abs(value - desired))
                        .thenComparingInt(Integer::intValue))
                .orElseThrow();
    }

    private static OptionalInt terminalCrossAxis(
            DistrictFootprint footprint,
            Axis axis,
            SuburbPlanningSettings settings,
            int junctionAxis,
            int mainCrossAxis,
            boolean negativeDirection
    ) {
        int reach = settings.parcelDepth() + settings.roadWidth();
        int boundaryMargin = settings.roadWidth();
        int target;
        if (negativeDirection) {
            target = Math.max(axis.minimumCross(footprint) + boundaryMargin, mainCrossAxis - reach);
            for (int value = target; value < mainCrossAxis; value++) {
                if (footprint.contains(axis.point(junctionAxis, value))) {
                    return OptionalInt.of(value);
                }
            }
            return OptionalInt.empty();
        }
        target = Math.min(axis.maximumCross(footprint) - boundaryMargin, mainCrossAxis + reach);
        for (int value = target; value > mainCrossAxis; value--) {
            if (footprint.contains(axis.point(junctionAxis, value))) {
                return OptionalInt.of(value);
            }
        }
        return OptionalInt.empty();
    }

    private static RoadNode roadNode(PlanElementId id, GridPoint point, String tag) {
        return new RoadNode(id, point, Set.of(new PlanTag(tag)), PlanProperties.empty());
    }

    private static RoadSegment roadSegment(
            PlanElementId id,
            PlanElementId startId,
            PlanElementId endId,
            int width,
            String... tags
    ) {
        Set<PlanTag> planTags = java.util.Arrays.stream(tags)
                .map(PlanTag::new)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new RoadSegment(id, startId, endId, width, planTags, PlanProperties.empty());
    }

    private enum Axis {
        HORIZONTAL {
            @Override
            int along(GridPoint point) {
                return point.x();
            }

            @Override
            int cross(GridPoint point) {
                return point.z();
            }

            @Override
            GridPoint point(int along, int cross) {
                return new GridPoint(along, cross);
            }

            @Override
            int minimum(DistrictFootprint footprint) {
                return footprint.bounds().minX();
            }

            @Override
            int maximum(DistrictFootprint footprint) {
                return footprint.bounds().maxXExclusive() - 1;
            }

            @Override
            int minimumCross(DistrictFootprint footprint) {
                return footprint.bounds().minZ();
            }

            @Override
            int maximumCross(DistrictFootprint footprint) {
                return footprint.bounds().maxZExclusive() - 1;
            }

            @Override
            int crossCenter(DistrictFootprint footprint) {
                return footprint.bounds().minZ() + (footprint.bounds().size().depth() / 2);
            }

            @Override
            List<Integer> crossAxes(DistrictFootprint footprint) {
                return range(footprint.bounds().minZ(), footprint.bounds().maxZExclusive());
            }
        },
        VERTICAL {
            @Override
            int along(GridPoint point) {
                return point.z();
            }

            @Override
            int cross(GridPoint point) {
                return point.x();
            }

            @Override
            GridPoint point(int along, int cross) {
                return new GridPoint(cross, along);
            }

            @Override
            int minimum(DistrictFootprint footprint) {
                return footprint.bounds().minZ();
            }

            @Override
            int maximum(DistrictFootprint footprint) {
                return footprint.bounds().maxZExclusive() - 1;
            }

            @Override
            int minimumCross(DistrictFootprint footprint) {
                return footprint.bounds().minX();
            }

            @Override
            int maximumCross(DistrictFootprint footprint) {
                return footprint.bounds().maxXExclusive() - 1;
            }

            @Override
            int crossCenter(DistrictFootprint footprint) {
                return footprint.bounds().minX() + (footprint.bounds().size().width() / 2);
            }

            @Override
            List<Integer> crossAxes(DistrictFootprint footprint) {
                return range(footprint.bounds().minX(), footprint.bounds().maxXExclusive());
            }
        };

        abstract int along(GridPoint point);

        abstract int cross(GridPoint point);

        abstract GridPoint point(int along, int cross);

        abstract int minimum(DistrictFootprint footprint);

        abstract int maximum(DistrictFootprint footprint);

        abstract int minimumCross(DistrictFootprint footprint);

        abstract int maximumCross(DistrictFootprint footprint);

        abstract int crossCenter(DistrictFootprint footprint);

        abstract List<Integer> crossAxes(DistrictFootprint footprint);

        final List<Integer> occupiedCrossAxes(DistrictFootprint footprint) {
            if (footprint.rectangular()) {
                return crossAxes(footprint);
            }
            return footprint.points().stream()
                    .map(this::cross)
                    .distinct()
                    .sorted()
                    .toList();
        }

        private static List<Integer> range(int minimum, int maximumExclusive) {
            List<Integer> values = new ArrayList<>(maximumExclusive - minimum);
            for (int value = minimum; value < maximumExclusive; value++) {
                values.add(value);
            }
            return List.copyOf(values);
        }
    }

    private record Line(
            int crossAxis,
            int minimum,
            int maximum,
            int supportedPointCount,
            int pointCount,
            int centerDistance
    ) {
        private static final Comparator<Line> QUALITY = Comparator
                .comparingInt(Line::supportedPointCount)
                .reversed()
                .thenComparing(Comparator.comparingInt(Line::pointCount).reversed())
                .thenComparing(Comparator.comparingInt(Line::length).reversed())
                .thenComparingInt(Line::centerDistance)
                .thenComparingInt(Line::crossAxis);

        private Line {
            if (minimum > maximum
                    || supportedPointCount < 0
                    || supportedPointCount > pointCount
                    || pointCount <= 0
                    || centerDistance < 0) {
                throw new IllegalArgumentException("invalid road skeleton line");
            }
        }

        private int length() {
            return (maximum - minimum) + 1;
        }
    }
}
