package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy;
import java.util.Objects;

public record WorldgenPlanningContext(
        String dimensionId,
        long worldSeed,
        SettlementProfileId profileId,
        GridSize surveySize,
        SuburbPlanningSettings planningSettings,
        TerrainResponsePolicy terrainResponsePolicy,
        WorldgenTerrainSurveyProvider terrainProvider,
        boolean terrainLoggingEnabled,
        boolean planningLoggingEnabled
) {
    public WorldgenPlanningContext(
            String dimensionId,
            long worldSeed,
            SettlementProfileId profileId,
            GridSize surveySize,
            SuburbPlanningSettings planningSettings,
            WorldgenTerrainSurveyProvider terrainProvider,
            boolean terrainLoggingEnabled,
            boolean planningLoggingEnabled
    ) {
        this(
                dimensionId,
                worldSeed,
                profileId,
                surveySize,
                planningSettings,
                TerrainResponsePolicy.defaults(),
                terrainProvider,
                terrainLoggingEnabled,
                planningLoggingEnabled
        );
    }

    public WorldgenPlanningContext {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(surveySize, "surveySize");
        Objects.requireNonNull(planningSettings, "planningSettings");
        Objects.requireNonNull(terrainResponsePolicy, "terrainResponsePolicy");
        Objects.requireNonNull(terrainProvider, "terrainProvider");
    }
}
