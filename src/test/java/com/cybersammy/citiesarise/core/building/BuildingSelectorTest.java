package com.cybersammy.citiesarise.core.building;

import com.cybersammy.citiesarise.fixtures.VanillaCatalogFixture;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.SuburbPlanningSettings;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingSelectorTest {
    static BuildingContentSettings content() {
        return VanillaCatalogFixture.settings(List.of(new BuildingContentSettings.Entry(VanillaCatalogFixture.COTTAGE, 3),
                new BuildingContentSettings.Entry(VanillaCatalogFixture.BUNGALOW, 2),
                new BuildingContentSettings.Entry(VanillaCatalogFixture.STUDIO, 1)), List.of("oak", "stone"), VanillaCatalogFixture.PLACEHOLDER);
    }

    static BuildingSlot slot(int size) {
        return new BuildingSlot(new PlanElementId("test:house"), new PlanElementId("test:parcel"),
                new GridBounds(new GridPoint(-12, 14), new GridSize(size, size)), Set.of(), PlanProperties.empty());
    }

    @Test void deterministicSelectionCoversAllVariantsAndPalettesWithoutChangingGeometry() {
        Set<String> assets = new HashSet<>();
        Set<String> palettes = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            BuildingSlot selected = BuildingSelector.select(slot(10), seed, content());
            assertEquals(selected, BuildingSelector.select(slot(10), seed, content()));
            assertEquals(slot(10).bounds(), selected.bounds());
            assets.add(selected.properties().find(PlanPropertyKeys.BUILDING_ASSET).orElseThrow());
            palettes.add(selected.properties().find(PlanPropertyKeys.BUILDING_PALETTE).orElseThrow());
        }
        assertEquals(Set.of("cities_arise:cottage", "cities_arise:bungalow", "cities_arise:studio"), assets);
        assertEquals(Set.of("oak", "stone"), palettes);
    }

    @Test void incompatibleAssetsUseDeclaredFallbackWithoutClipping() {
        assertEquals(slot(3), BuildingSelector.select(slot(3), 5, content()));
        assertEquals(slot(40), BuildingSelector.select(slot(40), 5, content()));
    }

    @Test void rejectsInvalidPoolsAndIncompatibleFallback() {
        assertThrows(IllegalArgumentException.class, () -> new BuildingContentSettings.Entry(VanillaCatalogFixture.COTTAGE, 0));
        assertThrows(IllegalArgumentException.class, () -> VanillaCatalogFixture.settings(List.of(), List.of("oak"), VanillaCatalogFixture.PLACEHOLDER));
        var incompatible = VanillaCatalogFixture.settings(List.of(new BuildingContentSettings.Entry(VanillaCatalogFixture.COTTAGE, 1)),
                List.of("oak"), VanillaCatalogFixture.COTTAGE);
        assertThrows(IllegalArgumentException.class, () -> SuburbPlanningSettings.defaults().withBuildings(incompatible));
    }

    @Test void settingsCopiesPreserveContentAndCacheIdentityChanges() {
        var original = SuburbPlanningSettings.defaults();
        var configured = original.withBuildings(content());
        assertNotEquals(original, configured);
        assertEquals(content(), configured.withTerrainTransitions(original.terrainTransitions()).buildings());
        assertEquals(slot(10), BuildingSelector.select(slot(10), 2, BuildingContentSettings.legacy()));
    }
}
