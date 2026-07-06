package com.cybersammy.citiesarise.core.planning.suburb;

import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.Objects;

public record SuburbPlanningRequest(
        PlanElementId settlementId,
        TerrainSurvey survey,
        long seed,
        SuburbPlanningSettings settings
) {
    public SuburbPlanningRequest {
        Objects.requireNonNull(settlementId, "settlementId");
        Objects.requireNonNull(survey, "survey");
        Objects.requireNonNull(settings, "settings");
    }
}
