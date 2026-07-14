package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import java.util.Objects;

public record WorldgenPlanningContext(
        String dimensionId,
        long worldSeed,
        SettlementProfileId profileId,
        GridSize surveySize,
        SuburbPlanningSettings planningSettings,
        WorldgenTerrainSurveyProvider terrainProvider,
        boolean terrainLoggingEnabled,
        boolean planningLoggingEnabled
) {
    public WorldgenPlanningContext {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(surveySize, "surveySize");
        Objects.requireNonNull(planningSettings, "planningSettings");
        Objects.requireNonNull(terrainProvider, "terrainProvider");
    }
}
