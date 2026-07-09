package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.config.DebugSuburbPlanningConfig;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DebugSettlementProfileSelectionTest {
    @Test
    void usesLoadedProfileValues() {
        SettlementProfile profile = profile();
        Optional<SettlementProfile> loadedProfile = DebugSettlementProfileSelection.load(() -> Optional.of(profile));
        DebugSuburbPlanningConfig fallbackConfig = DebugSuburbPlanningConfig.defaults();

        assertEquals(Optional.of(profile), loadedProfile);
        assertEquals(profile.surveySize(), DebugSettlementProfileSelection.surveySize(fallbackConfig, loadedProfile));
        assertEquals(
                profile.suburbPlanningSettings(),
                DebugSettlementProfileSelection.suburbPlanningSettings(fallbackConfig, loadedProfile)
        );
    }

    @Test
    void fallsBackToDebugConfigWhenProfileIsMissing() {
        Optional<SettlementProfile> loadedProfile = DebugSettlementProfileSelection.load(Optional::empty);
        DebugSuburbPlanningConfig fallbackConfig = DebugSuburbPlanningConfig.defaults();

        assertTrue(loadedProfile.isEmpty());
        assertEquals(fallbackConfig.toSurveySize(), DebugSettlementProfileSelection.surveySize(fallbackConfig, loadedProfile));
        assertEquals(
                fallbackConfig.toSuburbPlanningSettings(),
                DebugSettlementProfileSelection.suburbPlanningSettings(fallbackConfig, loadedProfile)
        );
    }

    @Test
    void fallsBackToDebugConfigWhenProfileLoadingThrows() {
        Optional<SettlementProfile> loadedProfile = DebugSettlementProfileSelection.load(() -> {
            throw new IllegalArgumentException("invalid profile");
        });
        DebugSuburbPlanningConfig fallbackConfig = DebugSuburbPlanningConfig.defaults();

        assertTrue(loadedProfile.isEmpty());
        assertEquals(fallbackConfig.toSurveySize(), DebugSettlementProfileSelection.surveySize(fallbackConfig, loadedProfile));
        assertEquals(
                fallbackConfig.toSuburbPlanningSettings(),
                DebugSettlementProfileSelection.suburbPlanningSettings(fallbackConfig, loadedProfile)
        );
    }

    private static SettlementProfile profile() {
        return new SettlementProfile(
                new SettlementProfileId("test:profile"),
                new GridSize(96, 64),
                new SuburbPlanningSettings(5, 0.75, 7, 18, 20, 4)
        );
    }
}
