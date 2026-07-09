package com.cybersammy.citiesarise.minecraft.profile;

import com.cybersammy.citiesarise.core.profile.SettlementProfile;
import java.util.Objects;

public record MinecraftSettlementProfileLimits(
        int maxSurveyWidth,
        int maxSurveyDepth,
        int maxRoadWidth,
        double maxBuildableSlope,
        int maxTargetParcelCount,
        int maxParcelWidth,
        int maxParcelDepth,
        int maxBuildingMargin
) {
    private static final MinecraftSettlementProfileLimits DEFAULTS = new MinecraftSettlementProfileLimits(
            128,
            128,
            16,
            8.0,
            128,
            64,
            64,
            8
    );

    public MinecraftSettlementProfileLimits {
        requirePositive(maxSurveyWidth, "maxSurveyWidth");
        requirePositive(maxSurveyDepth, "maxSurveyDepth");
        requirePositive(maxRoadWidth, "maxRoadWidth");
        requireFiniteNonNegative(maxBuildableSlope, "maxBuildableSlope");
        requirePositive(maxTargetParcelCount, "maxTargetParcelCount");
        requirePositive(maxParcelWidth, "maxParcelWidth");
        requirePositive(maxParcelDepth, "maxParcelDepth");
        requireNonNegative(maxBuildingMargin, "maxBuildingMargin");
    }

    public static MinecraftSettlementProfileLimits defaults() {
        return DEFAULTS;
    }

    public void validate(SettlementProfile profile) {
        Objects.requireNonNull(profile, "profile");

        requireAtMost(profile.surveySize().width(), maxSurveyWidth, "survey.width");
        requireAtMost(profile.surveySize().depth(), maxSurveyDepth, "survey.depth");
        requireAtMost(profile.suburbPlanningSettings().roadWidth(), maxRoadWidth, "planning.roadWidth");
        requireAtMost(
                profile.suburbPlanningSettings().maxBuildableSlope(),
                maxBuildableSlope,
                "planning.maxBuildableSlope"
        );
        requireAtMost(
                profile.suburbPlanningSettings().targetParcelCount(),
                maxTargetParcelCount,
                "planning.targetParcelCount"
        );
        requireAtMost(profile.suburbPlanningSettings().parcelWidth(), maxParcelWidth, "planning.parcelWidth");
        requireAtMost(profile.suburbPlanningSettings().parcelDepth(), maxParcelDepth, "planning.parcelDepth");
        requireAtMost(profile.suburbPlanningSettings().buildingMargin(), maxBuildingMargin, "planning.buildingMargin");
    }

    private static void requireAtMost(int value, int maxValue, String name) {
        if (value <= maxValue) {
            return;
        }

        throw new IllegalArgumentException(name + " must be at most " + maxValue);
    }

    private static void requireAtMost(double value, double maxValue, String name) {
        if (value <= maxValue) {
            return;
        }

        throw new IllegalArgumentException(name + " must be at most " + maxValue);
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
