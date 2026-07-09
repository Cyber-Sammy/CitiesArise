package com.cybersammy.citiesarise.minecraft.planning;

import com.cybersammy.citiesarise.config.DebugSuburbPlanningConfig;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import java.util.Objects;
import java.util.Optional;

final class DebugSettlementProfileSelection {
    private DebugSettlementProfileSelection() {
    }

    static Optional<SettlementProfile> load(ProfileLoadAction loadAction) {
        Objects.requireNonNull(loadAction, "loadAction");

        try {
            return loadAction.load();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    static GridSize surveySize(
            DebugSuburbPlanningConfig config,
            Optional<SettlementProfile> profile
    ) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(profile, "profile");

        return profile
                .map(SettlementProfile::surveySize)
                .orElseGet(config::toSurveySize);
    }

    static SuburbPlanningSettings suburbPlanningSettings(
            DebugSuburbPlanningConfig config,
            Optional<SettlementProfile> profile
    ) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(profile, "profile");

        return profile
                .map(SettlementProfile::suburbPlanningSettings)
                .orElseGet(config::toSuburbPlanningSettings);
    }

    @FunctionalInterface
    interface ProfileLoadAction {
        Optional<SettlementProfile> load();
    }
}
