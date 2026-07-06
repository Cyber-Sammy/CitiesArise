package com.cybersammy.citiesarise.core.terrain.scoring;

import com.cybersammy.citiesarise.core.terrain.TerrainCell;

final class SlopeSuitabilityRule implements TerrainSuitabilityRule {
    @Override
    public String name() {
        return "slope";
    }

    @Override
    public TerrainSuitabilityContribution evaluate(TerrainCell cell, TerrainSuitabilityContext context) {
        if (cell.slope() > context.maxBuildableSlope()) {
            return TerrainSuitabilityContribution.rejection(TerrainRejectionReason.STEEP_SLOPE);
        }

        return TerrainSuitabilityContribution.multiplier(slopeMultiplier(cell.slope(), context.maxBuildableSlope()));
    }

    private static double slopeMultiplier(double slope, double maxBuildableSlope) {
        if (maxBuildableSlope == 0.0) {
            return 1.0;
        }

        double normalizedSlope = slope / maxBuildableSlope;
        return Math.max(0.0, 1.0 - (normalizedSlope * 0.5));
    }
}
