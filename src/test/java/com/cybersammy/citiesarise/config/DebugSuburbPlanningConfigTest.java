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

        assertEquals(5, settings.roadWidth());
        assertEquals(0.75, settings.maxBuildableSlope());
        assertEquals(8, settings.targetParcelCount());
        assertEquals(18, settings.parcelWidth());
        assertEquals(20, settings.parcelDepth());
        assertEquals(4, settings.buildingMargin());
    }

    @Test
    void usesDefaultSurveySize() {
        GridSize surveySize = DebugSuburbPlanningConfig.defaults().toSurveySize();

        assertEquals(new GridSize(120, 72), surveySize);
    }

    @Test
    void defaultsCreateHabitablePlaceholderFootprint() {
        DebugSuburbPlanningConfig defaults = DebugSuburbPlanningConfig.defaults();

        assertTrue(buildingWidth(defaults) >= 10);
        assertTrue(buildingDepth(defaults) >= 10);
    }

    @Test
    void mapsCustomValuesToRuntimeSettings() {
        DebugSuburbPlanningConfig config = new DebugSuburbPlanningConfig(80, 56, 5, 1.25, 12, 14, 16, 4);

        assertEquals(new GridSize(80, 56), config.toSurveySize());
        assertEquals(new SuburbPlanningSettings(5, 1.25, 12, 14, 16, 4), config.toSuburbPlanningSettings());
    }

    @Test
    void slopeDefaultIsSofterThanCoreDefault() {
        SuburbPlanningSettings debugSettings = DebugSuburbPlanningConfig.defaults().toSuburbPlanningSettings();
        SuburbPlanningSettings coreSettings = SuburbPlanningSettings.defaults();

        assertTrue(debugSettings.maxBuildableSlope() > coreSettings.maxBuildableSlope());
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(0, 48, 5, 0.75, 8, 12, 14, 3));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(72, 0, 5, 0.75, 8, 12, 14, 3));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(72, 48, 0, 0.75, 8, 12, 14, 3));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(72, 48, 5, -0.1, 8, 12, 14, 3));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(72, 48, 5, Double.NaN, 8, 12, 14, 3));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(72, 48, 5, 0.75, 0, 12, 14, 3));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(72, 48, 5, 0.75, 8, 0, 14, 3));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(72, 48, 5, 0.75, 8, 12, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(72, 48, 5, 0.75, 8, 12, 14, -1));
        assertThrows(IllegalArgumentException.class, () -> new DebugSuburbPlanningConfig(72, 48, 5, 0.75, 8, 6, 14, 3));
    }

    private static int buildingWidth(DebugSuburbPlanningConfig config) {
        return buildingSize(config.parcelWidth(), config.buildingMargin());
    }

    private static int buildingDepth(DebugSuburbPlanningConfig config) {
        return buildingSize(config.parcelDepth(), config.buildingMargin());
    }

    private static int buildingSize(int parcelSize, int buildingMargin) {
        return parcelSize - (buildingMargin * 2);
    }
}
