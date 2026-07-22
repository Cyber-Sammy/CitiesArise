package com.cybersammy.citiesarise.core.earthwork;

import java.math.BigInteger;
import java.util.Objects;

public record EarthworkSiteAssessment(
        EarthworkSiteQuality quality,
        long preferredDepthExcess,
        int columnsAbovePreferred,
        long totalVolume,
        int footprintColumnCount,
        int earthworkColumnCount,
        int maximumCutDepth,
        int maximumFillDepth
) implements Comparable<EarthworkSiteAssessment> {
    public EarthworkSiteAssessment {
        Objects.requireNonNull(quality, "quality");
        requireNonNegative(preferredDepthExcess, "preferredDepthExcess");
        requireNonNegative(columnsAbovePreferred, "columnsAbovePreferred");
        requireNonNegative(totalVolume, "totalVolume");
        requireNonNegative(footprintColumnCount, "footprintColumnCount");
        requireNonNegative(earthworkColumnCount, "earthworkColumnCount");
        requireNonNegative(maximumCutDepth, "maximumCutDepth");
        requireNonNegative(maximumFillDepth, "maximumFillDepth");
        requireConsistentPreferredExcess(preferredDepthExcess, columnsAbovePreferred, totalVolume);
        requireEarthworkColumnCount(earthworkColumnCount, footprintColumnCount, totalVolume);
        requireColumnCount(columnsAbovePreferred, earthworkColumnCount);
        requireMatchingQuality(quality, preferredDepthExcess, totalVolume);
        Math.addExact(totalVolume, preferredDepthExcess);
    }

    public static EarthworkSiteAssessment evaluate(
            TerrainPreparationPlan plan,
            int preferredCutDepth,
            int preferredFillDepth
    ) {
        Objects.requireNonNull(plan, "plan");
        requireNonNegative(preferredCutDepth, "preferredCutDepth");
        requireNonNegative(preferredFillDepth, "preferredFillDepth");

        long preferredDepthExcess = 0L;
        int columnsAbovePreferred = 0;
        int earthworkColumnCount = 0;
        int maximumCutDepth = 0;
        int maximumFillDepth = 0;
        for (TerrainPreparationColumn column : plan.columns()) {
            if (column.totalVolume() > 0) {
                earthworkColumnCount = Math.incrementExact(earthworkColumnCount);
            }
            long columnExcess = preferredExcess(column, preferredCutDepth, preferredFillDepth);
            preferredDepthExcess = Math.addExact(preferredDepthExcess, columnExcess);
            if (columnExcess > 0L) {
                columnsAbovePreferred = Math.incrementExact(columnsAbovePreferred);
            }
            maximumCutDepth = Math.max(maximumCutDepth, column.cutDepth());
            maximumFillDepth = Math.max(maximumFillDepth, column.fillDepth());
        }

        return new EarthworkSiteAssessment(
                quality(plan.totalVolume(), preferredDepthExcess),
                preferredDepthExcess,
                columnsAbovePreferred,
                plan.totalVolume(),
                plan.columns().size(),
                earthworkColumnCount,
                maximumCutDepth,
                maximumFillDepth
        );
    }

    @Override
    public int compareTo(EarthworkSiteAssessment other) {
        Objects.requireNonNull(other, "other");
        int comparison = Long.compare(rankingCost(), other.rankingCost());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(maximumDepth(), other.maximumDepth());
        if (comparison != 0) {
            return comparison;
        }
        comparison = compareDensity(other);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Long.compare(totalVolume, other.totalVolume);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Long.compare(preferredDepthExcess, other.preferredDepthExcess);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(columnsAbovePreferred, other.columnsAbovePreferred);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(maximumFillDepth, other.maximumFillDepth);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(maximumCutDepth, other.maximumCutDepth);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(earthworkColumnCount, other.earthworkColumnCount);
        if (comparison != 0) {
            return comparison;
        }
        return Integer.compare(footprintColumnCount, other.footprintColumnCount);
    }

    public long rankingCost() {
        return Math.addExact(totalVolume, preferredDepthExcess);
    }

    public double earthworkDensity() {
        if (earthworkColumnCount == 0) {
            return 0.0;
        }
        return (double) totalVolume / earthworkColumnCount;
    }

    private int compareDensity(EarthworkSiteAssessment other) {
        BigInteger left = BigInteger.valueOf(totalVolume)
                .multiply(BigInteger.valueOf(other.earthworkColumnCount));
        BigInteger right = BigInteger.valueOf(other.totalVolume)
                .multiply(BigInteger.valueOf(earthworkColumnCount));
        return left.compareTo(right);
    }

    private int maximumDepth() {
        return Math.max(maximumCutDepth, maximumFillDepth);
    }

    private static long preferredExcess(
            TerrainPreparationColumn column,
            int preferredCutDepth,
            int preferredFillDepth
    ) {
        long cutExcess = Math.max(0L, (long) column.cutDepth() - preferredCutDepth);
        long fillExcess = Math.max(0L, (long) column.fillDepth() - preferredFillDepth);
        return Math.addExact(cutExcess, fillExcess);
    }

    private static EarthworkSiteQuality quality(long totalVolume, long preferredDepthExcess) {
        if (totalVolume == 0L) {
            return EarthworkSiteQuality.DIRECT;
        }
        if (preferredDepthExcess == 0L) {
            return EarthworkSiteQuality.MODERATE;
        }
        return EarthworkSiteQuality.MAJOR;
    }

    private static void requireMatchingQuality(
            EarthworkSiteQuality quality,
            long preferredDepthExcess,
            long totalVolume
    ) {
        if (quality == quality(totalVolume, preferredDepthExcess)) {
            return;
        }
        throw new IllegalArgumentException("quality must match earthwork values");
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requireEarthworkColumnCount(
            int earthworkColumnCount,
            int footprintColumnCount,
            long totalVolume
    ) {
        if (earthworkColumnCount > footprintColumnCount) {
            throw new IllegalArgumentException("earthworkColumnCount must not exceed footprintColumnCount");
        }
        if (totalVolume == 0L) {
            if (earthworkColumnCount != 0) {
                throw new IllegalArgumentException("earthwork columns require positive volume");
            }
            return;
        }
        if (earthworkColumnCount > 0) {
            return;
        }
        throw new IllegalArgumentException("positive volume requires earthwork columns");
    }

    private static void requireConsistentPreferredExcess(
            long preferredDepthExcess,
            int columnsAbovePreferred,
            long totalVolume
    ) {
        if (preferredDepthExcess > totalVolume) {
            throw new IllegalArgumentException("preferredDepthExcess must not exceed totalVolume");
        }
        if (preferredDepthExcess == 0L) {
            if (columnsAbovePreferred != 0) {
                throw new IllegalArgumentException("columnsAbovePreferred requires preferred depth excess");
            }
            return;
        }
        if (columnsAbovePreferred == 0) {
            throw new IllegalArgumentException("preferred depth excess requires affected columns");
        }
    }

    private static void requireColumnCount(int columnsAbovePreferred, int earthworkColumnCount) {
        if (columnsAbovePreferred > earthworkColumnCount) {
            throw new IllegalArgumentException("columnsAbovePreferred must not exceed earthworkColumnCount");
        }
    }
}
