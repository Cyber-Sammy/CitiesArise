package com.cybersammy.citiesarise.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CitiesAriseWorldgenConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.ConfigValue<String> SETTLEMENT_PROFILE_ID;
    private static final ModConfigSpec.IntValue CANDIDATE_REGION_MODULO;
    private static final ModConfigSpec.IntValue LOCATE_SEARCH_RADIUS_REGIONS;
    private static final ModConfigSpec.IntValue LOCATE_MAX_CANDIDATE_ATTEMPTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("worldgen");
        ENABLED = builder
                .comment("Enables automatic Cities Arise settlement placement in newly generated chunks.")
                .worldRestart()
                .define("enabled", false);
        SETTLEMENT_PROFILE_ID = builder
                .comment("Settlement profile id used by automatic world generation.")
                .worldRestart()
                .define("settlementProfileId", DebugSuburbPlanningConfig.DEFAULT_SETTLEMENT_PROFILE_ID);
        CANDIDATE_REGION_MODULO = builder
                .comment("Plans one deterministic candidate per approximately this many settlement regions.")
                .worldRestart()
                .defineInRange("candidateRegionModulo", 16, 1, 1024);
        LOCATE_SEARCH_RADIUS_REGIONS = builder
                .comment("Maximum settlement-region radius searched by /citiesarise locate.")
                .defineInRange("locateSearchRadiusRegions", 64, 1, 512);
        LOCATE_MAX_CANDIDATE_ATTEMPTS = builder
                .comment("Maximum deterministic candidates fully planned by /citiesarise locate.")
                .defineInRange("locateMaxCandidateAttempts", 256, 1, 4096);
        builder.pop();

        SPEC = builder.build();
    }

    private CitiesAriseWorldgenConfig() {
    }

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static String settlementProfileId() {
        return SETTLEMENT_PROFILE_ID.get();
    }

    public static int candidateRegionModulo() {
        return CANDIDATE_REGION_MODULO.get();
    }

    public static int locateSearchRadiusRegions() {
        return LOCATE_SEARCH_RADIUS_REGIONS.get();
    }

    public static int locateMaxCandidateAttempts() {
        return LOCATE_MAX_CANDIDATE_ATTEMPTS.get();
    }
}
