package com.cybersammy.citiesarise.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CitiesAriseConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue DEBUG_LOGGING_ENABLED;
    private static final ModConfigSpec.BooleanValue TERRAIN_LOGGING_ENABLED;
    private static final ModConfigSpec.BooleanValue PLANNING_LOGGING_ENABLED;
    private static final ModConfigSpec.BooleanValue COMMAND_LOGGING_ENABLED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

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
        COMMAND_LOGGING_ENABLED = builder
                .comment("Enables debug command logs when debug logging is enabled.")
                .define("commandLoggingEnabled", true);
        builder.pop();

        SPEC = builder.build();
    }

    private CitiesAriseConfig() {
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
