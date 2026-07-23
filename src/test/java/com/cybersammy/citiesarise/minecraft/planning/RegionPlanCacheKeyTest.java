package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainFeatureType;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponse;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy;
import java.util.EnumMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RegionPlanCacheKeyTest {
    @Test
    void equalWhenAllInputsMatch() {
        RegionPlanCacheKey first = key("minecraft:overworld", "cities_arise:suburb", 1234L, SuburbPlanningSettings.defaults());
        RegionPlanCacheKey second = key("minecraft:overworld", "cities_arise:suburb", 1234L, SuburbPlanningSettings.defaults());

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void changesWhenDimensionChanges() {
        assertNotEquals(
                key("minecraft:overworld", "cities_arise:suburb", 1234L, SuburbPlanningSettings.defaults()),
                key("minecraft:the_nether", "cities_arise:suburb", 1234L, SuburbPlanningSettings.defaults())
        );
    }

    @Test
    void changesWhenWorldSeedChanges() {
        assertNotEquals(
                key("minecraft:overworld", "cities_arise:suburb", 1234L, SuburbPlanningSettings.defaults()),
                key("minecraft:overworld", "cities_arise:suburb", 5678L, SuburbPlanningSettings.defaults())
        );
    }

    @Test
    void changesWhenTerrainSurveySourceChanges() {
        assertNotEquals(
                key(
                        "minecraft:overworld",
                        "cities_arise:suburb",
                        1234L,
                        TerrainSurveySource.LOADED_WORLD,
                        SuburbPlanningSettings.defaults()
                ),
                key(
                        "minecraft:overworld",
                        "cities_arise:suburb",
                        1234L,
                        TerrainSurveySource.WORLDGEN_BASE,
                        SuburbPlanningSettings.defaults()
                )
        );
    }

    @Test
    void changesWhenProfileChanges() {
        assertNotEquals(
                key("minecraft:overworld", "cities_arise:suburb", 1234L, SuburbPlanningSettings.defaults()),
                key("minecraft:overworld", "cities_arise:large_suburb", 1234L, SuburbPlanningSettings.defaults())
        );
    }

    @Test
    void changesWhenPlanningSettingsChange() {
        assertNotEquals(
                key("minecraft:overworld", "cities_arise:suburb", 1234L, SuburbPlanningSettings.defaults()),
                key("minecraft:overworld", "cities_arise:suburb", 1234L, new SuburbPlanningSettings(5, 1.0, 8, 18, 20, 4))
        );
    }

    @Test
    void changesWhenTerrainPolicyChanges() {
        RegionPlanCacheKey defaultKey = key(
                "minecraft:overworld",
                "cities_arise:suburb",
                1234L,
                SuburbPlanningSettings.defaults()
        );
        EnumMap<TerrainFeatureType, TerrainResponse> responses =
                new EnumMap<>(TerrainResponsePolicy.defaults().responses());
        responses.put(TerrainFeatureType.WATER, TerrainResponse.PRESERVE);
        TerrainResponsePolicy policy = new TerrainResponsePolicy(responses, Set.of());

        RegionPlanCacheKey changedKey = new RegionPlanCacheKey(
                "minecraft:overworld",
                new SettlementRegion(2, -3),
                1234L,
                TerrainSurveySource.LOADED_WORLD,
                new SettlementProfileId("cities_arise:suburb"),
                new GridSize(120, 72),
                SuburbPlanningSettings.defaults(),
                policy
        );

        assertNotEquals(defaultKey, changedKey);
    }

    @Test
    void rejectsBlankDimensionId() {
        assertThrows(IllegalArgumentException.class, () -> key(" ", "cities_arise:suburb", 1234L, SuburbPlanningSettings.defaults()));
    }

    @Test
    void rejectsNullRegion() {
        assertThrows(NullPointerException.class, () -> new RegionPlanCacheKey(
                "minecraft:overworld",
                null,
                1234L,
                TerrainSurveySource.LOADED_WORLD,
                new SettlementProfileId("cities_arise:suburb"),
                new GridSize(120, 72),
                SuburbPlanningSettings.defaults()
        ));
    }

    private static RegionPlanCacheKey key(
            String dimensionId,
            String profileId,
            long worldSeed,
            SuburbPlanningSettings settings
    ) {
        return key(dimensionId, profileId, worldSeed, TerrainSurveySource.LOADED_WORLD, settings);
    }

    private static RegionPlanCacheKey key(
            String dimensionId,
            String profileId,
            long worldSeed,
            TerrainSurveySource terrainSurveySource,
            SuburbPlanningSettings settings
    ) {
        return new RegionPlanCacheKey(
                dimensionId,
                new SettlementRegion(2, -3),
                worldSeed,
                terrainSurveySource,
                new SettlementProfileId(profileId),
                new GridSize(120, 72),
                settings
        );
    }
}
