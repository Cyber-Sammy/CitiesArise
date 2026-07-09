package com.cybersammy.citiesarise.core.profile;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import java.util.Objects;

public record SettlementProfile(
        SettlementProfileId id,
        GridSize surveySize,
        SuburbPlanningSettings suburbPlanningSettings
) {
    public SettlementProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(surveySize, "surveySize");
        Objects.requireNonNull(suburbPlanningSettings, "suburbPlanningSettings");
    }
}
