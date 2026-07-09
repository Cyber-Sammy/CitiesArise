package com.cybersammy.citiesarise.minecraft.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.profile.SettlementProfileId;
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
    void rejectsBlankDimensionId() {
        assertThrows(IllegalArgumentException.class, () -> key(" ", "cities_arise:suburb", 1234L, SuburbPlanningSettings.defaults()));
    }

    @Test
    void rejectsNullRegion() {
        assertThrows(NullPointerException.class, () -> new RegionPlanCacheKey(
                "minecraft:overworld",
                null,
                1234L,
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
        return new RegionPlanCacheKey(
                dimensionId,
                new SettlementRegion(2, -3),
                worldSeed,
                new SettlementProfileId(profileId),
                new GridSize(120, 72),
                settings
        );
    }
}
