package com.cybersammy.citiesarise.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CitiesAriseWorldgenConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.ConfigValue<String> SETTLEMENT_PROFILE_ID;
    private static final ModConfigSpec.IntValue CANDIDATE_REGION_MODULO;
    private static final ModConfigSpec.IntValue LOCATE_SEARCH_RADIUS_REGIONS;
    private static final ModConfigSpec.IntValue LOCATE_MAX_CANDIDATE_ATTEMPTS;
    private static final ModConfigSpec.IntValue LOCATE_IMPROVEMENT_CANDIDATE_ATTEMPTS;
    private static final ModConfigSpec.IntValue LOCATE_TIME_LIMIT_SECONDS;
    private static final ModConfigSpec.BooleanValue LOCATE_TERRAIN_RANKING;

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
                .comment("Plans one deterministic candidate per approximately this many ordinary settlement regions.",
                        "Partitioned large cities account for their 6x6 region reservation before applying this divisor.")
                .worldRestart()
                .defineInRange("candidateRegionModulo", 16, 1, 1024);
        LOCATE_SEARCH_RADIUS_REGIONS = builder
                .comment("Maximum settlement-region radius searched by potential and diagnostic locate modes.")
                .defineInRange("locateSearchRadiusRegions", 64, 1, 512);
        LOCATE_MAX_CANDIDATE_ATTEMPTS = builder
                .comment("Maximum deterministic candidates fully planned by /citiesarise locate diagnostic.")
                .defineInRange("locateMaxCandidateAttempts", 256, 1, 4096);
        LOCATE_IMPROVEMENT_CANDIDATE_ATTEMPTS = builder
                .comment("Additional candidates checked only by /citiesarise locate diagnostic best after its first accepted site.")
                .defineInRange("locateImprovementCandidateAttempts", 16, 0, 4096);
        LOCATE_TIME_LIMIT_SECONDS = builder
                .comment("Diagnostic search time budget, checked between candidates. A running candidate may finish after this budget.")
                .defineInRange("locateTimeLimitSeconds", 60, 1, 3600);
        LOCATE_TERRAIN_RANKING = builder
                .comment("Orders diagnostic candidates by sparse generator heights and water before full validation. Does not affect natural city generation or potential locate.")
                .define("locateTerrainRanking",true);
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

    public static int locateTimeLimitSeconds() { return LOCATE_TIME_LIMIT_SECONDS.get(); }
    public static boolean locateTerrainRanking() { return LOCATE_TERRAIN_RANKING.get(); }

    public static int locateImprovementCandidateAttempts() {
        return LOCATE_IMPROVEMENT_CANDIDATE_ATTEMPTS.get();
    }
}
