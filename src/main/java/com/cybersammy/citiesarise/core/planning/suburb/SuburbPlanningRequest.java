package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy;
import java.util.Objects;

public record SuburbPlanningRequest(
        PlanElementId settlementId,
        TerrainSurvey survey,
        long seed,
        SuburbPlanningSettings settings,
        TerrainResponsePolicy terrainResponsePolicy
) {
    public SuburbPlanningRequest(
            PlanElementId settlementId,
            TerrainSurvey survey,
            long seed,
            SuburbPlanningSettings settings
    ) {
        this(settlementId, survey, seed, settings, TerrainResponsePolicy.defaults());
    }

    public SuburbPlanningRequest {
        Objects.requireNonNull(settlementId, "settlementId");
        Objects.requireNonNull(survey, "survey");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(terrainResponsePolicy, "terrainResponsePolicy");
    }
}
