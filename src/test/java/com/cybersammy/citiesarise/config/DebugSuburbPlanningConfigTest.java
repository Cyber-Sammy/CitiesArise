package com.cybersammy.citiesarise.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import org.junit.jupiter.api.Test;

final class DebugSuburbPlanningConfigTest {
    @Test
    void usesMinecraftFacingDefaults() {
        SuburbPlanningSettings settings = DebugSuburbPlanningConfig.defaults().toSuburbPlanningSettings();

        assertEquals(3, settings.roadWidth());
        assertEquals(0.75, settings.maxBuildableSlope());
        assertEquals(6, settings.targetParcelCount());
    }

    @Test
    void usesDefaultSurveySize() {
        GridSize surveySize = DebugSuburbPlanningConfig.defaults().toSurveySize();

        assertEquals(new GridSize(40, 30), surveySize);
    }

    @Test
    void mapsCustomValuesToRuntimeSettings() {
        DebugSuburbPlanningConfig config = new DebugSuburbPlanningConfig(48, 36, 5, 1.25, 12);

        assertEquals(new GridSize(48, 36), config.toSurveySize());
        assertEquals(new SuburbPlanningSettings(5, 1.25, 12), config.toSuburbPlanningSettings());
    }

    @Test
    void slopeDefaultIsSofterThanCoreDefault() {
        SuburbPlanningSettings debugSettings = DebugSuburbPlanningConfig.defaults().toSuburbPlanningSettings();
        SuburbPlanningSettings coreSettings = SuburbPlanningSettings.defaults();

        assertTrue(debugSettings.maxBuildableSlope() > coreSettings.maxBuildableSlope());
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(0, 30, 3, 0.75, 6));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(40, 0, 3, 0.75, 6));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(40, 30, 0, 0.75, 6));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(40, 30, 3, -0.1, 6));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(40, 30, 3, Double.NaN, 6));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(40, 30, 3, 0.75, 0));
    }
}
