package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SuburbPlanningSettingsTest {
    @Test
    void keepsThreeArgumentConstructorCompatibleWithDefaultScale() {
        SuburbPlanningSettings settings = new SuburbPlanningSettings(3, 0.25, 6);

        assertEquals(SuburbPlanningSettings.DEFAULT_PARCEL_WIDTH, settings.parcelWidth());
        assertEquals(SuburbPlanningSettings.DEFAULT_PARCEL_DEPTH, settings.parcelDepth());
        assertEquals(SuburbPlanningSettings.DEFAULT_BUILDING_MARGIN, settings.buildingMargin());
        assertEquals(SuburbPlanningSettings.DEFAULT_PREFERRED_MAX_CUT_DEPTH, settings.preferredMaxCutDepth());
        assertEquals(SuburbPlanningSettings.DEFAULT_PREFERRED_MAX_FILL_DEPTH, settings.preferredMaxFillDepth());
        assertEquals(SuburbPlanningSettings.DEFAULT_MAX_CUT_DEPTH, settings.maxCutDepth());
        assertEquals(SuburbPlanningSettings.DEFAULT_MAX_FILL_DEPTH, settings.maxFillDepth());
        assertEquals(SuburbPlanningSettings.DEFAULT_MAX_EARTHWORK_VOLUME, settings.maxEarthworkVolume());
    }

    @Test
    void rejectsInvalidScaleSettings() {
        assertThrows(IllegalArgumentException.class, () -> new SuburbPlanningSettings(0, 0.25, 6, 10, 12, 2));
        assertThrows(IllegalArgumentException.class, () -> new SuburbPlanningSettings(3, -0.1, 6, 10, 12, 2));
        assertThrows(IllegalArgumentException.class, () -> new SuburbPlanningSettings(3, Double.NaN, 6, 10, 12, 2));
        assertThrows(IllegalArgumentException.class, () -> new SuburbPlanningSettings(3, 0.25, 0, 10, 12, 2));
        assertThrows(IllegalArgumentException.class, () -> new SuburbPlanningSettings(3, 0.25, 6, 0, 12, 2));
        assertThrows(IllegalArgumentException.class, () -> new SuburbPlanningSettings(3, 0.25, 6, 10, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new SuburbPlanningSettings(3, 0.25, 6, 10, 12, -1));
        assertThrows(IllegalArgumentException.class, () -> new SuburbPlanningSettings(3, 0.25, 6, 4, 12, 2));
        assertThrows(IllegalArgumentException.class, () -> new SuburbPlanningSettings(3, 0.25, 6, 10, 12, 2, 12, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> new SuburbPlanningSettings(3, 0.25, 6, 10, 12, 2, 12, 3, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SuburbPlanningSettings(3, 0.25, 6, 10, 12, 2, 12, 3, 3, -1L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SuburbPlanningSettings(3, 0.25, 6, 10, 12, 2, 12, 4, 3, 3, 3, 100L)
        );
    }
}
