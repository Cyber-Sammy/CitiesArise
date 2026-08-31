package com.cybersammy.citiesarise.core.terrain.policy;

public record TerrainAdaptationSettings(
        double sensitivity,
        int maxTerraformArea,
        int maxTerraformRelief,
        long maxTerraformVolume
) {
    private static final TerrainAdaptationSettings DEFAULTS = new TerrainAdaptationSettings(
            0.5,
            24,
            3,
            96L
    );
    private static final TerrainAdaptationSettings DISABLED = new TerrainAdaptationSettings(
            1.0,
            0,
            0,
            0L
    );

    public TerrainAdaptationSettings {
        if (!Double.isFinite(sensitivity) || sensitivity < 0.0 || sensitivity > 1.0) {
            throw new IllegalArgumentException("sensitivity must be finite and between zero and one");
        }
        requireNonNegative(maxTerraformArea, "maxTerraformArea");
        requireNonNegative(maxTerraformRelief, "maxTerraformRelief");
        requireNonNegative(maxTerraformVolume, "maxTerraformVolume");
    }

    public static TerrainAdaptationSettings defaults() {
        return DEFAULTS;
    }

    public static TerrainAdaptationSettings disabled() {
        return DISABLED;
    }

    public boolean permits(TerrainFeatureMetrics metrics) {
        if (metrics.area() > effectiveLimit(maxTerraformArea)) {
            return false;
        }
        if (metrics.relief() > effectiveLimit(maxTerraformRelief)) {
            return false;
        }
        return metrics.estimatedEarthworkVolume() <= effectiveLimit(maxTerraformVolume);
    }

    private int effectiveLimit(int maximum) {
        return (int) Math.floor(maximum * (1.0 - sensitivity));
    }

    private long effectiveLimit(long maximum) {
        return (long) Math.floor(maximum * (1.0 - sensitivity));
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
