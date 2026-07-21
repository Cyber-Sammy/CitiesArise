package com.cybersammy.citiesarise.core.earthwork;

import java.math.BigInteger;
import java.util.Objects;

public record EarthworkSiteAssessment(
        EarthworkSiteQuality quality,
        long preferredDepthExcess,
        int columnsAbovePreferred,
        long totalVolume,
        int preparedColumnCount,
        int maximumCutDepth,
        int maximumFillDepth
) implements Comparable<EarthworkSiteAssessment> {
    public EarthworkSiteAssessment {
        Objects.requireNonNull(quality, "quality");
        requireNonNegative(preferredDepthExcess, "preferredDepthExcess");
        requireNonNegative(columnsAbovePreferred, "columnsAbovePreferred");
        requireNonNegative(totalVolume, "totalVolume");
        requireNonNegative(preparedColumnCount, "preparedColumnCount");
        requireNonNegative(maximumCutDepth, "maximumCutDepth");
        requireNonNegative(maximumFillDepth, "maximumFillDepth");
        requireConsistentPreferredExcess(preferredDepthExcess, columnsAbovePreferred, totalVolume);
        requireColumnCount(columnsAbovePreferred, preparedColumnCount);
        requireMatchingQuality(quality, preferredDepthExcess, totalVolume);
        requireColumnsForVolume(preparedColumnCount, totalVolume);
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
        int maximumCutDepth = 0;
        int maximumFillDepth = 0;
        for (TerrainPreparationColumn column : plan.columns()) {
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
                maximumCutDepth,
                maximumFillDepth
        );
    }

    @Override
    public int compareTo(EarthworkSiteAssessment other) {
        Objects.requireNonNull(other, "other");
        int comparison = quality.compareTo(other.quality);
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
        comparison = compareDensity(other);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Long.compare(totalVolume, other.totalVolume);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(maximumCutDepth, other.maximumCutDepth);
        if (comparison != 0) {
            return comparison;
        }
        return Integer.compare(maximumFillDepth, other.maximumFillDepth);
    }

    public double earthworkDensity() {
        if (preparedColumnCount == 0) {
            return 0.0;
        }
        return (double) totalVolume / preparedColumnCount;
    }

    private int compareDensity(EarthworkSiteAssessment other) {
        BigInteger left = BigInteger.valueOf(totalVolume)
                .multiply(BigInteger.valueOf(other.preparedColumnCount));
        BigInteger right = BigInteger.valueOf(other.totalVolume)
                .multiply(BigInteger.valueOf(preparedColumnCount));
        return left.compareTo(right);
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

    private static void requireColumnsForVolume(int preparedColumnCount, long totalVolume) {
        if (preparedColumnCount != 0) {
            return;
        }
        if (totalVolume == 0L) {
            return;
        }
        throw new IllegalArgumentException("positive volume requires prepared columns");
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

    private static void requireColumnCount(int columnsAbovePreferred, int preparedColumnCount) {
        if (columnsAbovePreferred > preparedColumnCount) {
            throw new IllegalArgumentException("columnsAbovePreferred must not exceed preparedColumnCount");
        }
    }
}
