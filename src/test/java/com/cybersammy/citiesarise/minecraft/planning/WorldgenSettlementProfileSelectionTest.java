package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorldgenSettlementProfileSelectionTest {
    @Test
    void rejectsMissingProfileWithoutFallbackSettings() {
        assertTrue(WorldgenSettlementProfileSelection.from(Optional.empty()).isEmpty());
    }

    @Test
    void usesOnlyLoadedProfileSettings() {
        SettlementProfile profile = new SettlementProfile(
                new SettlementProfileId("test:worldgen"),
                new GridSize(96, 64),
                new SuburbPlanningSettings(5, 0.75, 7, 18, 20, 4)
        );

        WorldgenSettlementProfileSelection selection = WorldgenSettlementProfileSelection
                .from(Optional.of(profile))
                .orElseThrow();

        assertEquals(profile.surveySize(), selection.surveySize());
        assertEquals(profile.suburbPlanningSettings(), selection.planningSettings());
    }
}
