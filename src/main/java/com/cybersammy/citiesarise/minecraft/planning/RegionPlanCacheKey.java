package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy;
import java.util.Objects;

public record RegionPlanCacheKey(
        String dimensionId,
        SettlementRegion region,
        long worldSeed,
        TerrainSurveySource terrainSurveySource,
        SettlementProfileId profileId,
        GridSize surveySize,
        SuburbPlanningSettings planningSettings,
        TerrainResponsePolicy terrainResponsePolicy
) {
    public RegionPlanCacheKey(
            String dimensionId,
            SettlementRegion region,
            long worldSeed,
            TerrainSurveySource terrainSurveySource,
            SettlementProfileId profileId,
            GridSize surveySize,
            SuburbPlanningSettings planningSettings
    ) {
        this(
                dimensionId,
                region,
                worldSeed,
                terrainSurveySource,
                profileId,
                surveySize,
                planningSettings,
                TerrainResponsePolicy.defaults()
        );
    }

    public RegionPlanCacheKey {
        requireNonBlank(dimensionId, "dimensionId");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(terrainSurveySource, "terrainSurveySource");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(surveySize, "surveySize");
        Objects.requireNonNull(planningSettings, "planningSettings");
        Objects.requireNonNull(terrainResponsePolicy, "terrainResponsePolicy");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);

        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
