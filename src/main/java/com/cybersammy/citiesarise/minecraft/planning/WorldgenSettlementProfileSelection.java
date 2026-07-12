package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import java.util.Objects;
import java.util.Optional;

record WorldgenSettlementProfileSelection(
        GridSize surveySize,
        SuburbPlanningSettings planningSettings
) {
    WorldgenSettlementProfileSelection {
        Objects.requireNonNull(surveySize, "surveySize");
        Objects.requireNonNull(planningSettings, "planningSettings");
    }

    static Optional<WorldgenSettlementProfileSelection> from(Optional<SettlementProfile> profile) {
        Objects.requireNonNull(profile, "profile");
        return profile.map(value -> new WorldgenSettlementProfileSelection(
                value.surveySize(),
                value.suburbPlanningSettings()
        ));
    }
}
