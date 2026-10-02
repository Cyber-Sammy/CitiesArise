package com.cybersammy.citiesarise.minecraft.placement;

import com.cybersammy.citiesarise.fixtures.VanillaCatalogFixture;
import com.cybersammy.citiesarise.core.building.BuildingAsset;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VanillaBuildingPlacementProviderTest {
    private final VanillaBuildingPlacementProvider provider = new VanillaBuildingPlacementProvider();

    static BuildingSlot slot(BuildingAsset asset, String palette) {
        return new BuildingSlot(new PlanElementId("test:house"), new PlanElementId("test:parcel"),
                new GridBounds(new GridPoint(12, -4), new GridSize(10, 12)), Set.of(),
                PlanProperties.of(PlanPropertyKeys.BUILDING_ASSET, asset.id()).with(PlanPropertyKeys.BUILDING_PALETTE, palette), Optional.of(VanillaCatalogFixture.content(asset,palette)));
    }

    @Test void eachVariantStaysWithinDeclaredVolumeAndHasDoorsWindowsAndFurniture() {
        Set<List<DebugBlockPlacementOperation>> geometries = new HashSet<>();
        for (BuildingAsset asset : List.of(VanillaCatalogFixture.COTTAGE, VanillaCatalogFixture.BUNGALOW, VanillaCatalogFixture.STUDIO)) {
            BuildingSlot slot = slot(asset, "oak");
            var operations = provider.create(slot, new GridPoint(17, -4));
            assertEquals(operations, provider.create(slot, new GridPoint(17, -4)));
            assertTrue(operations.stream().allMatch(o -> slot.bounds().contains(o.point()) && o.verticalOffset() <= asset.height()));
            assertTrue(operations.stream().allMatch(o -> o.sourceElementId().equals(slot.id())));
            for (var role : List.of(DebugPlacementRole.DOOR_NORTH_LOWER, DebugPlacementRole.DOOR_NORTH_UPPER,
                    DebugPlacementRole.BUILDING_WINDOW, DebugPlacementRole.BUILDING_WORKBENCH, DebugPlacementRole.BUILDING_BOOKSHELF)) {
                assertTrue(operations.stream().anyMatch(o -> o.role() == role), role.toString());
            }
            geometries.add(operations);
        }
        assertEquals(3, geometries.size());
    }

    @Test void everyPerimeterEntranceHasAnOpenInwardStepIncludingCorners() {
        BuildingSlot slot = slot(VanillaCatalogFixture.COTTAGE, "stone");
        var b = slot.bounds();
        for (int x = b.minX(); x < b.maxXExclusive(); x++) {
            checkEntrance(slot, new GridPoint(x, b.minZ()), new GridPoint(x, b.minZ() + 1));
            checkEntrance(slot, new GridPoint(x, b.maxZExclusive() - 1), new GridPoint(x, b.maxZExclusive() - 2));
        }
        for (int z = b.minZ() + 1; z < b.maxZExclusive() - 1; z++) {
            checkEntrance(slot, new GridPoint(b.minX(), z), new GridPoint(b.minX() + 1, z));
            checkEntrance(slot, new GridPoint(b.maxXExclusive() - 1, z), new GridPoint(b.maxXExclusive() - 2, z));
        }
        assertThrows(IllegalArgumentException.class, () -> provider.create(slot, new GridPoint(17, 0)));
    }

    private void checkEntrance(BuildingSlot slot, GridPoint entrance, GridPoint inward) {
        var ops = provider.create(slot, entrance);
        assertEquals(2, ops.stream().filter(o -> o.point().equals(entrance) && o.role().name().startsWith("DOOR_")).count());
        for (int y = 1; y <= 2; y++) {
            int offset = y;
            assertTrue(ops.stream().anyMatch(o -> o.point().equals(inward) && o.verticalOffset() == offset
                    && o.role() == DebugPlacementRole.BUILDING_INTERIOR_AIR));
        }
    }

    @Test void paletteChangesMaterialsAndExistingRoleIdsRemainStable() {
        assertNotEquals(provider.create(slot(VanillaCatalogFixture.STUDIO, "oak"), new GridPoint(17, -4)),
                provider.create(slot(VanillaCatalogFixture.STUDIO, "stone"), new GridPoint(17, -4)));
        assertEquals(0, DebugPlacementRole.FOUNDATION.serializedId());
        assertEquals(17, DebugPlacementRole.TERRAIN_RETAINING_WALL.serializedId());
        for (var role : DebugPlacementRole.values()) assertEquals(role, DebugPlacementRole.fromSerializedId(role.serializedId()));
    }
}
