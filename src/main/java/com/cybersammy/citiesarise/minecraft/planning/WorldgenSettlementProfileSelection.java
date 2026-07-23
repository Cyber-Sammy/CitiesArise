package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy;
import java.util.Objects;
import java.util.Optional;

record WorldgenSettlementProfileSelection(
        GridSize surveySize,
        SuburbPlanningSettings planningSettings,
        TerrainResponsePolicy terrainResponsePolicy
) {
    WorldgenSettlementProfileSelection {
        Objects.requireNonNull(surveySize, "surveySize");
        Objects.requireNonNull(planningSettings, "planningSettings");
        Objects.requireNonNull(terrainResponsePolicy, "terrainResponsePolicy");
    }

    static Optional<WorldgenSettlementProfileSelection> from(Optional<SettlementProfile> profile) {
        Objects.requireNonNull(profile, "profile");
        return profile.map(value -> new WorldgenSettlementProfileSelection(
                value.surveySize(),
                value.suburbPlanningSettings(),
                value.terrainResponsePolicy()
        ));
    }
}
