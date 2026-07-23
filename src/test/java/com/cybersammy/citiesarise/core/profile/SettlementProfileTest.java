package com.cybersammy.citiesarise.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy;
import org.junit.jupiter.api.Test;

final class SettlementProfileTest {
    @Test
    void acceptsOpaqueProfileIds() {
        SettlementProfileId id = new SettlementProfileId("cities_arise:suburb");

        assertEquals("cities_arise:suburb", id.value());
    }

    @Test
    void trimsProfileId() {
        SettlementProfileId id = new SettlementProfileId("  cities_arise:suburb  ");

        assertEquals("cities_arise:suburb", id.value());
    }

    @Test
    void rejectsBlankOrWhitespaceProfileIds() {
        assertThrows(IllegalArgumentException.class, () -> new SettlementProfileId(""));
        assertThrows(IllegalArgumentException.class, () -> new SettlementProfileId("cities arise:suburb"));
    }

    @Test
    void requiresProfileParts() {
        assertThrows(NullPointerException.class, () -> new SettlementProfile(null, new GridSize(10, 10), settings()));
        assertThrows(NullPointerException.class, () -> new SettlementProfile(id(), null, settings()));
        assertThrows(NullPointerException.class, () -> new SettlementProfile(id(), new GridSize(10, 10), null));
        assertThrows(
                NullPointerException.class,
                () -> new SettlementProfile(id(), new GridSize(10, 10), settings(), null)
        );
    }

    @Test
    void legacyConstructorUsesDefaultTerrainPolicy() {
        SettlementProfile profile = new SettlementProfile(id(), new GridSize(10, 10), settings());

        assertEquals(TerrainResponsePolicy.defaults(), profile.terrainResponsePolicy());
    }

    private static SettlementProfileId id() {
        return new SettlementProfileId("cities_arise:suburb");
    }

    private static SuburbPlanningSettings settings() {
        return SuburbPlanningSettings.defaults();
    }
}
