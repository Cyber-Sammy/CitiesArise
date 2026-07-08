package com.cybersammy.citiesarise.config;

import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CitiesAriseConfig {
    private static final int MAX_DEBUG_SURVEY_SIZE = 128;

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue DEBUG_SURVEY_WIDTH;
    private static final ModConfigSpec.IntValue DEBUG_SURVEY_DEPTH;
    private static final ModConfigSpec.IntValue DEBUG_ROAD_WIDTH;
    private static final ModConfigSpec.DoubleValue DEBUG_MAX_BUILDABLE_SLOPE;
    private static final ModConfigSpec.IntValue DEBUG_TARGET_PARCEL_COUNT;
    private static final ModConfigSpec.IntValue DEBUG_PARCEL_WIDTH;
    private static final ModConfigSpec.IntValue DEBUG_PARCEL_DEPTH;
    private static final ModConfigSpec.IntValue DEBUG_BUILDING_MARGIN;
    private static final ModConfigSpec.BooleanValue DEBUG_PLACEMENT_ENABLED;
    private static final ModConfigSpec.BooleanValue DEBUG_LOGGING_ENABLED;
    private static final ModConfigSpec.BooleanValue TERRAIN_LOGGING_ENABLED;
    private static final ModConfigSpec.BooleanValue PLANNING_LOGGING_ENABLED;
    private static final ModConfigSpec.BooleanValue PLACEMENT_LOGGING_ENABLED;
    private static final ModConfigSpec.BooleanValue COMMAND_LOGGING_ENABLED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("planning");
        DEBUG_SURVEY_WIDTH = builder
                .comment("Survey width used by the Minecraft debug suburb planner.")
                .defineInRange(
                        "debugSurveyWidth",
                        DebugSuburbPlanningConfig.DEFAULT_SURVEY_WIDTH,
                        1,
                        MAX_DEBUG_SURVEY_SIZE
                );
        DEBUG_SURVEY_DEPTH = builder
                .comment("Survey depth used by the Minecraft debug suburb planner.")
                .defineInRange(
                        "debugSurveyDepth",
                        DebugSuburbPlanningConfig.DEFAULT_SURVEY_DEPTH,
                        1,
                        MAX_DEBUG_SURVEY_SIZE
                );
        DEBUG_ROAD_WIDTH = builder
                .comment("Road width used by the Minecraft debug suburb planner.")
                .defineInRange("debugRoadWidth", DebugSuburbPlanningConfig.DEFAULT_ROAD_WIDTH, 1, 16);
        DEBUG_MAX_BUILDABLE_SLOPE = builder
                .comment("Maximum normalized slope accepted by the Minecraft debug suburb planner.")
                .defineInRange(
                        "debugMaxBuildableSlope",
                        DebugSuburbPlanningConfig.DEFAULT_MAX_BUILDABLE_SLOPE,
                        0.0,
                        8.0
                );
        DEBUG_TARGET_PARCEL_COUNT = builder
                .comment("Target parcel count used by the Minecraft debug suburb planner.")
                .defineInRange("debugTargetParcelCount", DebugSuburbPlanningConfig.DEFAULT_TARGET_PARCEL_COUNT, 1, 128);
        DEBUG_PARCEL_WIDTH = builder
                .comment("Parcel width used by the Minecraft debug suburb planner.")
                .defineInRange("debugParcelWidth", DebugSuburbPlanningConfig.DEFAULT_PARCEL_WIDTH, 3, 64);
        DEBUG_PARCEL_DEPTH = builder
                .comment("Parcel depth used by the Minecraft debug suburb planner.")
                .defineInRange("debugParcelDepth", DebugSuburbPlanningConfig.DEFAULT_PARCEL_DEPTH, 3, 64);
        DEBUG_BUILDING_MARGIN = builder
                .comment("Empty parcel margin around each debug placeholder building.")
                .defineInRange("debugBuildingMargin", DebugSuburbPlanningConfig.DEFAULT_BUILDING_MARGIN, 0, 16);
        DEBUG_PLACEMENT_ENABLED = builder
                .comment("Allows /citiesarise debug place to permanently place vanilla debug blocks.")
                .define("debugPlacementEnabled", false);
        builder.pop();

        builder.push("logging");
        DEBUG_LOGGING_ENABLED = builder
                .comment("Enables Cities Arise debug logs.")
                .define("debugLoggingEnabled", false);
        TERRAIN_LOGGING_ENABLED = builder
                .comment("Enables terrain survey logs when debug logging is enabled.")
                .define("terrainLoggingEnabled", true);
        PLANNING_LOGGING_ENABLED = builder
                .comment("Enables planner result logs when debug logging is enabled.")
                .define("planningLoggingEnabled", true);
        PLACEMENT_LOGGING_ENABLED = builder
                .comment("Enables debug placement logs when debug logging is enabled.")
                .define("placementLoggingEnabled", true);
        COMMAND_LOGGING_ENABLED = builder
                .comment("Enables debug command logs when debug logging is enabled.")
                .define("commandLoggingEnabled", true);
        builder.pop();

        SPEC = builder.build();
    }

    private CitiesAriseConfig() {
    }

    public static DebugSuburbPlanningConfig debugSuburbPlanningConfig() {
        return new DebugSuburbPlanningConfig(
                DEBUG_SURVEY_WIDTH.get(),
                DEBUG_SURVEY_DEPTH.get(),
                DEBUG_ROAD_WIDTH.get(),
                DEBUG_MAX_BUILDABLE_SLOPE.get(),
                DEBUG_TARGET_PARCEL_COUNT.get(),
                DEBUG_PARCEL_WIDTH.get(),
                DEBUG_PARCEL_DEPTH.get(),
                DEBUG_BUILDING_MARGIN.get()
        );
    }

    public static SuburbPlanningSettings debugSuburbPlanningSettings() {
        return debugSuburbPlanningConfig().toSuburbPlanningSettings();
    }

    public static boolean debugPlacementEnabled() {
        return DEBUG_PLACEMENT_ENABLED.get();
    }

    public static boolean terrainLoggingEnabled() {
        if (!debugLoggingEnabled()) {
            return false;
        }

        return TERRAIN_LOGGING_ENABLED.get();
    }

    public static boolean planningLoggingEnabled() {
        if (!debugLoggingEnabled()) {
            return false;
        }

        return PLANNING_LOGGING_ENABLED.get();
    }

    public static boolean placementLoggingEnabled() {
        if (!debugLoggingEnabled()) {
            return false;
        }

        return PLACEMENT_LOGGING_ENABLED.get();
    }

    public static boolean commandLoggingEnabled() {
        if (!debugLoggingEnabled()) {
            return false;
        }

        return COMMAND_LOGGING_ENABLED.get();
    }

    private static boolean debugLoggingEnabled() {
        return DEBUG_LOGGING_ENABLED.get();
    }
}
