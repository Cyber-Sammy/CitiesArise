package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        DebugSettlementProfileSelection.SelectionResult selection = DebugSettlementProfileSelection.load(() -> Optional.of(profile));
        DebugSuburbPlanningConfig fallbackConfig = DebugSuburbPlanningConfig.defaults();

        assertEquals(Optional.of(profile), selection.profile());
        assertFalse(selection.failed());
        assertEquals(profile.surveySize(), DebugSettlementProfileSelection.surveySize(fallbackConfig, selection.profile()));
        assertEquals(
                profile.suburbPlanningSettings(),
                DebugSettlementProfileSelection.suburbPlanningSettings(fallbackConfig, selection.profile())
        );
    }

    @Test
    void fallsBackToDebugConfigWhenProfileIsMissing() {
        DebugSettlementProfileSelection.SelectionResult selection = DebugSettlementProfileSelection.load(Optional::empty);
        DebugSuburbPlanningConfig fallbackConfig = DebugSuburbPlanningConfig.defaults();

        assertTrue(selection.profile().isEmpty());
        assertFalse(selection.failed());
        assertEquals(fallbackConfig.toSurveySize(), DebugSettlementProfileSelection.surveySize(fallbackConfig, selection.profile()));
        assertEquals(
                fallbackConfig.toSuburbPlanningSettings(),
                DebugSettlementProfileSelection.suburbPlanningSettings(fallbackConfig, selection.profile())
        );
    }

    @Test
    void fallsBackToDebugConfigAndKeepsErrorWhenProfileLoadingThrows() {
        RuntimeException error = new IllegalArgumentException("invalid profile");
        DebugSettlementProfileSelection.SelectionResult selection = DebugSettlementProfileSelection.load(() -> {
            throw error;
        });
        DebugSuburbPlanningConfig fallbackConfig = DebugSuburbPlanningConfig.defaults();

        assertTrue(selection.profile().isEmpty());
        assertTrue(selection.failed());
        assertSame(error, selection.error());
        assertEquals(fallbackConfig.toSurveySize(), DebugSettlementProfileSelection.surveySize(fallbackConfig, selection.profile()));
        assertEquals(
                fallbackConfig.toSuburbPlanningSettings(),
                DebugSettlementProfileSelection.suburbPlanningSettings(fallbackConfig, selection.profile())
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
