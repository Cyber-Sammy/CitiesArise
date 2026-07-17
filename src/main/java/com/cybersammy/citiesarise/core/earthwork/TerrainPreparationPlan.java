package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.GridPoint;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record TerrainPreparationPlan(
        RegionalElevationPlan elevationPlan,
        TerrainPreparationStatus status,
        List<TerrainPreparationArea> areas,
        List<TerrainPreparationColumn> columns,
        long cutVolume,
        long fillVolume
) {
    public TerrainPreparationPlan {
        Objects.requireNonNull(elevationPlan, "elevationPlan");
        Objects.requireNonNull(status, "status");
        areas = immutableAreas(areas);
        columns = immutableColumns(columns);
        requireSuccessfulStatus(status);
        requireNonNegative(cutVolume, "cutVolume");
        requireNonNegative(fillVolume, "fillVolume");
        requireUniqueColumns(columns);
        requireMatchingVolume(areas, columns, cutVolume, fillVolume);
        requireMatchingStatus(status, cutVolume, fillVolume);
    }

    public static TerrainPreparationPlan of(
            RegionalElevationPlan elevationPlan,
            List<TerrainPreparationArea> areas,
            List<TerrainPreparationColumn> columns
    ) {
        Objects.requireNonNull(elevationPlan, "elevationPlan");
        List<TerrainPreparationArea> immutableAreas = immutableAreas(areas);
        List<TerrainPreparationColumn> immutableColumns = immutableColumns(columns);
        long cutVolume = sumColumnCutVolume(immutableColumns);
        long fillVolume = sumColumnFillVolume(immutableColumns);
        TerrainPreparationStatus status = preparationStatus(cutVolume, fillVolume);
        return new TerrainPreparationPlan(
                elevationPlan,
                status,
                immutableAreas,
                immutableColumns,
                cutVolume,
                fillVolume
        );
    }

    public long totalVolume() {
        return Math.addExact(cutVolume, fillVolume);
    }

    public boolean requiresEarthworks() {
        return status == TerrainPreparationStatus.ACCEPTED_WITH_EARTHWORKS;
    }

    private static TerrainPreparationStatus preparationStatus(long cutVolume, long fillVolume) {
        if (cutVolume == 0L && fillVolume == 0L) {
            return TerrainPreparationStatus.ACCEPTED;
        }
        return TerrainPreparationStatus.ACCEPTED_WITH_EARTHWORKS;
    }

    private static List<TerrainPreparationArea> immutableAreas(List<TerrainPreparationArea> areas) {
        Objects.requireNonNull(areas, "areas");
        for (TerrainPreparationArea area : areas) {
            if (area == null) {
                throw new IllegalArgumentException("areas must not contain null values");
            }
        }
        return List.copyOf(areas);
    }

    private static List<TerrainPreparationColumn> immutableColumns(List<TerrainPreparationColumn> columns) {
        Objects.requireNonNull(columns, "columns");
        for (TerrainPreparationColumn column : columns) {
            if (column == null) {
                throw new IllegalArgumentException("columns must not contain null values");
            }
        }
        return List.copyOf(columns);
    }

    private static long sumCutVolume(List<TerrainPreparationArea> areas) {
        long total = 0L;
        for (TerrainPreparationArea area : areas) {
            total = Math.addExact(total, area.cutVolume());
        }
        return total;
    }

    private static long sumFillVolume(List<TerrainPreparationArea> areas) {
        long total = 0L;
        for (TerrainPreparationArea area : areas) {
            total = Math.addExact(total, area.fillVolume());
        }
        return total;
    }

    private static void requireSuccessfulStatus(TerrainPreparationStatus status) {
        if (status == TerrainPreparationStatus.REJECTED) {
            throw new IllegalArgumentException("terrain preparation plan must not have rejected status");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static long sumColumnCutVolume(List<TerrainPreparationColumn> columns) {
        long total = 0L;
        for (TerrainPreparationColumn column : columns) {
            total = Math.addExact(total, column.cutDepth());
        }
        return total;
    }

    private static long sumColumnFillVolume(List<TerrainPreparationColumn> columns) {
        long total = 0L;
        for (TerrainPreparationColumn column : columns) {
            total = Math.addExact(total, column.fillDepth());
        }
        return total;
    }

    private static void requireUniqueColumns(List<TerrainPreparationColumn> columns) {
        Set<GridPoint> points = new HashSet<>();
        for (TerrainPreparationColumn column : columns) {
            if (!points.add(column.point())) {
                throw new IllegalArgumentException("columns must contain unique points");
            }
        }
    }

    private static void requireMatchingVolume(
            List<TerrainPreparationArea> areas,
            List<TerrainPreparationColumn> columns,
            long cutVolume,
            long fillVolume
    ) {
        if (sumCutVolume(areas) != cutVolume) {
            throw new IllegalArgumentException("cutVolume must equal area cut volume");
        }
        if (sumFillVolume(areas) != fillVolume) {
            throw new IllegalArgumentException("fillVolume must equal area fill volume");
        }
        if (sumColumnCutVolume(columns) != cutVolume) {
            throw new IllegalArgumentException("cutVolume must equal column cut volume");
        }
        if (sumColumnFillVolume(columns) != fillVolume) {
            throw new IllegalArgumentException("fillVolume must equal column fill volume");
        }
    }

    private static void requireMatchingStatus(
            TerrainPreparationStatus status,
            long cutVolume,
            long fillVolume
    ) {
        TerrainPreparationStatus expected = preparationStatus(cutVolume, fillVolume);
        if (status != expected) {
            throw new IllegalArgumentException("status must match calculated earthwork volume");
        }
    }
}
