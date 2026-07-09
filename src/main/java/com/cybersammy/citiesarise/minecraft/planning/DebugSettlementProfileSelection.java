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

    static SelectionResult load(ProfileLoadAction loadAction) {
        Objects.requireNonNull(loadAction, "loadAction");

        try {
            return SelectionResult.loaded(loadAction.load());
        } catch (RuntimeException exception) {
            return SelectionResult.failed(exception);
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

    record SelectionResult(Optional<SettlementProfile> profile, RuntimeException error) {
        SelectionResult {
            profile = Objects.requireNonNull(profile, "profile");
        }

        static SelectionResult loaded(Optional<SettlementProfile> profile) {
            return new SelectionResult(profile, null);
        }

        static SelectionResult failed(RuntimeException error) {
            return new SelectionResult(Optional.empty(), Objects.requireNonNull(error, "error"));
        }

        boolean failed() {
            return error != null;
        }
    }
}
