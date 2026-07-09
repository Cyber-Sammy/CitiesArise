package com.cybersammy.citiesarise.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CitiesAriseConfigSnapshotTest {
    @Test
    void defaultsMirrorDebugPlanningDefaults() {
        CitiesAriseConfigSnapshot snapshot = CitiesAriseConfigSnapshot.defaults();

        assertEquals(DebugSuburbPlanningConfig.DEFAULT_SETTLEMENT_PROFILE_ID, snapshot.debugSettlementProfileId());
        assertEquals(DebugSuburbPlanningConfig.DEFAULT_SURVEY_WIDTH, snapshot.debugSurveyWidth());
        assertEquals(DebugSuburbPlanningConfig.DEFAULT_SURVEY_DEPTH, snapshot.debugSurveyDepth());
        assertEquals(DebugSuburbPlanningConfig.DEFAULT_ROAD_WIDTH, snapshot.debugRoadWidth());
        assertEquals(DebugSuburbPlanningConfig.DEFAULT_MAX_BUILDABLE_SLOPE, snapshot.debugMaxBuildableSlope());
        assertEquals(DebugSuburbPlanningConfig.DEFAULT_TARGET_PARCEL_COUNT, snapshot.debugTargetParcelCount());
        assertEquals(DebugSuburbPlanningConfig.DEFAULT_PARCEL_WIDTH, snapshot.debugParcelWidth());
        assertEquals(DebugSuburbPlanningConfig.DEFAULT_PARCEL_DEPTH, snapshot.debugParcelDepth());
        assertEquals(DebugSuburbPlanningConfig.DEFAULT_BUILDING_MARGIN, snapshot.debugBuildingMargin());
        assertFalse(snapshot.debugPlacementEnabled());
        assertTrue(snapshot.debugPlacementUndoEnabled());
        assertFalse(snapshot.debugLoggingEnabled());
        assertTrue(snapshot.terrainLoggingEnabled());
        assertTrue(snapshot.planningLoggingEnabled());
        assertTrue(snapshot.placementLoggingEnabled());
        assertTrue(snapshot.commandLoggingEnabled());
    }

    @Test
    void clampsBuildingMarginToCurrentParcelSize() {
        CitiesAriseConfigSnapshot snapshot = snapshotWithParcel(3, 3, 8);

        assertEquals(1, snapshot.debugBuildingMargin());
        assertEquals(1, snapshot.toDebugSuburbPlanningConfig().buildingMargin());
    }

    @Test
    void rejectsInvalidNumericValues() {
        assertThrows(IllegalArgumentException.class, () -> snapshotWithSurveyWidth(0));
        assertThrows(IllegalArgumentException.class, () -> snapshotWithSlope(-0.1));
        assertThrows(IllegalArgumentException.class, () -> snapshotWithSlope(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> snapshotWithBuildingMargin(-1));
    }

    @Test
    void rejectsInvalidProfileIds() {
        assertThrows(IllegalArgumentException.class, () -> snapshotWithProfileId(""));
        assertThrows(IllegalArgumentException.class, () -> snapshotWithProfileId("cities arise:suburb"));
    }

    private static CitiesAriseConfigSnapshot snapshotWithProfileId(String profileId) {
        CitiesAriseConfigSnapshot defaults = CitiesAriseConfigSnapshot.defaults();

        return new CitiesAriseConfigSnapshot(
                profileId,
                defaults.debugSurveyWidth(),
                defaults.debugSurveyDepth(),
                defaults.debugRoadWidth(),
                defaults.debugMaxBuildableSlope(),
                defaults.debugTargetParcelCount(),
                defaults.debugParcelWidth(),
                defaults.debugParcelDepth(),
                defaults.debugBuildingMargin(),
                defaults.debugPlacementEnabled(),
                defaults.debugPlacementUndoEnabled(),
                defaults.debugLoggingEnabled(),
                defaults.terrainLoggingEnabled(),
                defaults.planningLoggingEnabled(),
                defaults.placementLoggingEnabled(),
                defaults.commandLoggingEnabled()
        );
    }

    private static CitiesAriseConfigSnapshot snapshotWithParcel(int width, int depth, int margin) {
        CitiesAriseConfigSnapshot defaults = CitiesAriseConfigSnapshot.defaults();

        return new CitiesAriseConfigSnapshot(
                defaults.debugSettlementProfileId(),
                defaults.debugSurveyWidth(),
                defaults.debugSurveyDepth(),
                defaults.debugRoadWidth(),
                defaults.debugMaxBuildableSlope(),
                defaults.debugTargetParcelCount(),
                width,
                depth,
                margin,
                defaults.debugPlacementEnabled(),
                defaults.debugPlacementUndoEnabled(),
                defaults.debugLoggingEnabled(),
                defaults.terrainLoggingEnabled(),
                defaults.planningLoggingEnabled(),
                defaults.placementLoggingEnabled(),
                defaults.commandLoggingEnabled()
        );
    }

    private static CitiesAriseConfigSnapshot snapshotWithSurveyWidth(int surveyWidth) {
        CitiesAriseConfigSnapshot defaults = CitiesAriseConfigSnapshot.defaults();

        return new CitiesAriseConfigSnapshot(
                defaults.debugSettlementProfileId(),
                surveyWidth,
                defaults.debugSurveyDepth(),
                defaults.debugRoadWidth(),
                defaults.debugMaxBuildableSlope(),
                defaults.debugTargetParcelCount(),
                defaults.debugParcelWidth(),
                defaults.debugParcelDepth(),
                defaults.debugBuildingMargin(),
                defaults.debugPlacementEnabled(),
                defaults.debugPlacementUndoEnabled(),
                defaults.debugLoggingEnabled(),
                defaults.terrainLoggingEnabled(),
                defaults.planningLoggingEnabled(),
                defaults.placementLoggingEnabled(),
                defaults.commandLoggingEnabled()
        );
    }

    private static CitiesAriseConfigSnapshot snapshotWithSlope(double slope) {
        CitiesAriseConfigSnapshot defaults = CitiesAriseConfigSnapshot.defaults();

        return new CitiesAriseConfigSnapshot(
                defaults.debugSettlementProfileId(),
                defaults.debugSurveyWidth(),
                defaults.debugSurveyDepth(),
                defaults.debugRoadWidth(),
                slope,
                defaults.debugTargetParcelCount(),
                defaults.debugParcelWidth(),
                defaults.debugParcelDepth(),
                defaults.debugBuildingMargin(),
                defaults.debugPlacementEnabled(),
                defaults.debugPlacementUndoEnabled(),
                defaults.debugLoggingEnabled(),
                defaults.terrainLoggingEnabled(),
                defaults.planningLoggingEnabled(),
                defaults.placementLoggingEnabled(),
                defaults.commandLoggingEnabled()
        );
    }

    private static CitiesAriseConfigSnapshot snapshotWithBuildingMargin(int buildingMargin) {
        CitiesAriseConfigSnapshot defaults = CitiesAriseConfigSnapshot.defaults();

        return new CitiesAriseConfigSnapshot(
                defaults.debugSettlementProfileId(),
                defaults.debugSurveyWidth(),
                defaults.debugSurveyDepth(),
                defaults.debugRoadWidth(),
                defaults.debugMaxBuildableSlope(),
                defaults.debugTargetParcelCount(),
                defaults.debugParcelWidth(),
                defaults.debugParcelDepth(),
                buildingMargin,
                defaults.debugPlacementEnabled(),
                defaults.debugPlacementUndoEnabled(),
                defaults.debugLoggingEnabled(),
                defaults.terrainLoggingEnabled(),
                defaults.planningLoggingEnabled(),
                defaults.placementLoggingEnabled(),
                defaults.commandLoggingEnabled()
        );
    }
}
