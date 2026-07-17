package com.cybersammy.citiesarise.core.earthwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RegionalElevationPlanTest {
    @Test
    void resolvesRequiredZone() {
        ElevationZone road = zone("road", ElevationZoneType.ROAD_SEGMENT, 64);
        RegionalElevationPlan plan = new RegionalElevationPlan(List.of(road), List.of());

        assertEquals(road, plan.requiredZone(road.sourceElementId()));
    }

    @Test
    void rejectsDuplicateZoneIds() {
        ElevationZone road = zone("road", ElevationZoneType.ROAD_SEGMENT, 64);

        assertThrows(
                IllegalArgumentException.class,
                () -> new RegionalElevationPlan(List.of(road, road), List.of())
        );
    }

    @Test
    void rejectsTransitionWithUnknownZone() {
        ElevationZone road = zone("road", ElevationZoneType.ROAD_SEGMENT, 64);
        ElevationTransition transition = new ElevationTransition(
                ElevationTransitionType.BUILDING_ACCESS,
                road.sourceElementId(),
                id("missing"),
                new GridPoint(0, 0),
                64,
                64
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new RegionalElevationPlan(List.of(road), List.of(transition))
        );
    }

    @Test
    void rejectsTransitionElevationThatDiffersFromZone() {
        ElevationZone road = zone("road", ElevationZoneType.ROAD_SEGMENT, 64);
        ElevationZone building = zone("building", ElevationZoneType.BUILDING_PAD, 65);
        ElevationTransition transition = new ElevationTransition(
                ElevationTransitionType.BUILDING_ACCESS,
                road.sourceElementId(),
                building.sourceElementId(),
                new GridPoint(0, 0),
                63,
                65
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new RegionalElevationPlan(List.of(road, building), List.of(transition))
        );
    }

    private static ElevationZone zone(String name, ElevationZoneType type, int elevation) {
        return new ElevationZone(
                id(name),
                type,
                new GridBounds(new GridPoint(0, 0), new GridSize(2, 2)),
                elevation
        );
    }

    private static PlanElementId id(String name) {
        return new PlanElementId("test/" + name);
    }
}
