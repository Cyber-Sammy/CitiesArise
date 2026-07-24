package com.cybersammy.citiesarise.core.earthwork;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BuildingAccessResolverTest {
    @Test
    void selectsTheBuildingSideFacingTheNearestRoad() {
        ElevationZone road = road("road", 0, 5, 8, 1);
        ElevationZone building = building(2, 0, 3, 3);

        BuildingAccessResolver.BuildingAccess access = BuildingAccessResolver.resolve(
                List.of(road, building),
                building
        );

        assertEquals(road, access.roadZone());
        assertEquals(new GridPoint(3, 2), access.anchor());
    }

    @Test
    void selectsAnEastBoundaryForAnEastSideRoad() {
        ElevationZone road = road("road", 8, 0, 1, 8);
        ElevationZone building = building(2, 2, 3, 3);

        BuildingAccessResolver.BuildingAccess access = BuildingAccessResolver.resolve(
                List.of(road, building),
                building
        );

        assertEquals(new GridPoint(4, 3), access.anchor());
    }

    @Test
    void breaksEqualDistanceAndCenterTiesByRoadId() {
        ElevationZone north = road("z_north", 0, 0, 8, 1);
        ElevationZone south = road("a_south", 0, 6, 8, 1);
        ElevationZone building = building(2, 2, 3, 3);

        BuildingAccessResolver.BuildingAccess access = BuildingAccessResolver.resolve(
                List.of(north, south, building),
                building
        );

        assertEquals(south, access.roadZone());
        assertEquals(new GridPoint(3, 4), access.anchor());
    }

    @Test
    void keepsCornerAccessWhenItIsUniquelyNearest() {
        ElevationZone road = road("road", 0, 0, 1, 1);
        ElevationZone building = building(2, 2, 3, 3);

        BuildingAccessResolver.BuildingAccess access = BuildingAccessResolver.resolve(
                List.of(road, building),
                building
        );

        assertEquals(new GridPoint(2, 2), access.anchor());
    }

    private static ElevationZone road(String name, int x, int z, int width, int depth) {
        return new ElevationZone(
                id(name),
                ElevationZoneType.ROAD_SEGMENT,
                bounds(x, z, width, depth),
                64
        );
    }

    private static ElevationZone building(int x, int z, int width, int depth) {
        return new ElevationZone(
                id("building"),
                ElevationZoneType.BUILDING_PAD,
                bounds(x, z, width, depth),
                64
        );
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(new GridPoint(x, z), new GridSize(width, depth));
    }

    private static PlanElementId id(String name) {
        return new PlanElementId("test/" + name);
    }
}
