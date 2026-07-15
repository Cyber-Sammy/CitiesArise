package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import java.util.Objects;

public record StructurePlanningContext(
        long worldSeed,
        SettlementProfileId profileId,
        GridSize surveySize,
        SuburbPlanningSettings planningSettings,
        WorldgenTerrainSurveyProvider terrainProvider,
        boolean terrainLoggingEnabled,
        boolean planningLoggingEnabled
) {
    public StructurePlanningContext {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(surveySize, "surveySize");
        Objects.requireNonNull(planningSettings, "planningSettings");
        Objects.requireNonNull(terrainProvider, "terrainProvider");
    }
}
