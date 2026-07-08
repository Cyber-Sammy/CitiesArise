package com.cybersammy.citiesarise.config;

public record CitiesAriseConfigSnapshot(
        int debugSurveyWidth,
        int debugSurveyDepth,
        int debugRoadWidth,
        double debugMaxBuildableSlope,
        int debugTargetParcelCount,
        int debugParcelWidth,
        int debugParcelDepth,
        int debugBuildingMargin,
        boolean debugPlacementEnabled,
        boolean debugPlacementUndoEnabled,
        boolean debugLoggingEnabled,
        boolean terrainLoggingEnabled,
        boolean planningLoggingEnabled,
        boolean placementLoggingEnabled,
        boolean commandLoggingEnabled
) {
    public CitiesAriseConfigSnapshot {
        requirePositive(debugSurveyWidth, "debugSurveyWidth");
        requirePositive(debugSurveyDepth, "debugSurveyDepth");
        requirePositive(debugRoadWidth, "debugRoadWidth");
        requireFiniteNonNegative(debugMaxBuildableSlope, "debugMaxBuildableSlope");
        requirePositive(debugTargetParcelCount, "debugTargetParcelCount");
        requirePositive(debugParcelWidth, "debugParcelWidth");
        requirePositive(debugParcelDepth, "debugParcelDepth");
        requireNonNegative(debugBuildingMargin, "debugBuildingMargin");

        debugBuildingMargin = safeBuildingMargin(
                debugBuildingMargin,
                debugParcelWidth,
                debugParcelDepth
        );
    }

    public static CitiesAriseConfigSnapshot defaults() {
        DebugSuburbPlanningConfig planningDefaults = DebugSuburbPlanningConfig.defaults();

        return new CitiesAriseConfigSnapshot(
                planningDefaults.surveyWidth(),
                planningDefaults.surveyDepth(),
                planningDefaults.roadWidth(),
                planningDefaults.maxBuildableSlope(),
                planningDefaults.targetParcelCount(),
                planningDefaults.parcelWidth(),
                planningDefaults.parcelDepth(),
                planningDefaults.buildingMargin(),
                false,
                true,
                false,
                true,
                true,
                true,
                true
        );
    }

    DebugSuburbPlanningConfig toDebugSuburbPlanningConfig() {
        return new DebugSuburbPlanningConfig(
                debugSurveyWidth,
                debugSurveyDepth,
                debugRoadWidth,
                debugMaxBuildableSlope,
                debugTargetParcelCount,
                debugParcelWidth,
                debugParcelDepth,
                debugBuildingMargin
        );
    }

    static int safeBuildingMargin(int configuredMargin, int parcelWidth, int parcelDepth) {
        int maxWidthMargin = maxBuildingMargin(parcelWidth);
        int maxDepthMargin = maxBuildingMargin(parcelDepth);
        int maxMargin = Math.min(maxWidthMargin, maxDepthMargin);

        if (configuredMargin > maxMargin) {
            return maxMargin;
        }

        return configuredMargin;
    }

    private static int maxBuildingMargin(int parcelSize) {
        return (parcelSize - 1) / 2;
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
