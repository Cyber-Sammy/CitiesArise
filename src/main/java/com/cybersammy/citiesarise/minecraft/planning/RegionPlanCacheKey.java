package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import java.util.Objects;

public record RegionPlanCacheKey(
        String dimensionId,
        SettlementRegion region,
        long worldSeed,
        SettlementProfileId profileId,
        GridSize surveySize,
        SuburbPlanningSettings planningSettings
) {
    public RegionPlanCacheKey {
        requireNonBlank(dimensionId, "dimensionId");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(surveySize, "surveySize");
        Objects.requireNonNull(planningSettings, "planningSettings");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);

        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
