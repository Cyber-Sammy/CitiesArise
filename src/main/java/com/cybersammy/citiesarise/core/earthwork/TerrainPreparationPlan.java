package com.cybersammy.citiesarise.core.earthwork;

import java.util.List;
import java.util.Objects;

public record TerrainPreparationPlan(
        TerrainPreparationStatus status,
        List<TerrainPreparationArea> areas,
        long cutVolume,
        long fillVolume
) {
    public TerrainPreparationPlan {
        Objects.requireNonNull(status, "status");
        areas = immutableAreas(areas);
        requireSuccessfulStatus(status);
        requireNonNegative(cutVolume, "cutVolume");
        requireNonNegative(fillVolume, "fillVolume");
        requireMatchingVolume(areas, cutVolume, fillVolume);
        requireMatchingStatus(status, cutVolume, fillVolume);
    }

    public static TerrainPreparationPlan of(List<TerrainPreparationArea> areas) {
        List<TerrainPreparationArea> immutableAreas = immutableAreas(areas);
        long cutVolume = sumCutVolume(immutableAreas);
        long fillVolume = sumFillVolume(immutableAreas);
        TerrainPreparationStatus status = preparationStatus(cutVolume, fillVolume);
        return new TerrainPreparationPlan(status, immutableAreas, cutVolume, fillVolume);
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

    private static void requireMatchingVolume(
            List<TerrainPreparationArea> areas,
            long cutVolume,
            long fillVolume
    ) {
        if (sumCutVolume(areas) != cutVolume) {
            throw new IllegalArgumentException("cutVolume must equal area cut volume");
        }
        if (sumFillVolume(areas) != fillVolume) {
            throw new IllegalArgumentException("fillVolume must equal area fill volume");
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
