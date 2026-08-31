package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class ParcelTerrainEvaluationCache {
    private final TerrainSurvey survey;
    private final SuburbPlanningSettings settings;
    private final GridBounds surveyBounds;
    private final int[] heights;
    private final Map<GridBounds, ParcelTerrainEvaluation> evaluations = new HashMap<>();

    ParcelTerrainEvaluationCache(TerrainSurvey survey, SuburbPlanningSettings settings) {
        this.survey = Objects.requireNonNull(survey, "survey");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.surveyBounds = survey.bounds();
        this.heights = indexHeights(survey);
    }

    void requireCompatible(TerrainSurvey survey, SuburbPlanningSettings settings) {
        if (this.survey != survey) {
            throw new IllegalArgumentException("terrain evaluation cache belongs to another survey");
        }
        if (!this.settings.equals(settings)) {
            throw new IllegalArgumentException("terrain evaluation cache belongs to other settings");
        }
    }

    ParcelTerrainEvaluation evaluate(GridBounds parcelBounds) {
        Objects.requireNonNull(parcelBounds, "parcelBounds");
        if (!surveyBounds.contains(parcelBounds)) {
            throw new IllegalArgumentException("parcel bounds are outside terrain survey");
        }
        return evaluations.computeIfAbsent(parcelBounds, this::evaluateUncached);
    }

    int size() {
        return evaluations.size();
    }

    private ParcelTerrainEvaluation evaluateUncached(GridBounds parcelBounds) {
        GridBounds buildingBounds = SuburbParcelGeometry.buildingBounds(settings, parcelBounds);
        int targetHeight = maximumHeight(buildingBounds);
        int maximumCorrection = 0;
        long totalCorrection = 0L;
        for (int z = parcelBounds.minZ(); z < parcelBounds.maxZExclusive(); z++) {
            for (int x = parcelBounds.minX(); x < parcelBounds.maxXExclusive(); x++) {
                int correction = Math.abs(targetHeight - heightAt(x, z));
                maximumCorrection = Math.max(maximumCorrection, correction);
                totalCorrection += correction;
            }
        }
        return new ParcelTerrainEvaluation(maximumCorrection, totalCorrection);
    }

    private int maximumHeight(GridBounds bounds) {
        int maximum = Integer.MIN_VALUE;
        for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
            for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
                maximum = Math.max(maximum, heightAt(x, z));
            }
        }
        return maximum;
    }

    private int heightAt(int x, int z) {
        int localX = x - surveyBounds.minX();
        int localZ = z - surveyBounds.minZ();
        return heights[(localZ * surveyBounds.size().width()) + localX];
    }

    private static int[] indexHeights(TerrainSurvey survey) {
        GridBounds bounds = survey.bounds();
        int[] indexed = new int[Math.multiplyExact(bounds.size().width(), bounds.size().depth())];
        for (TerrainCell cell : survey.cells()) {
            int localX = cell.point().x() - bounds.minX();
            int localZ = cell.point().z() - bounds.minZ();
            indexed[(localZ * bounds.size().width()) + localX] = cell.height();
        }
        return indexed;
    }
}
