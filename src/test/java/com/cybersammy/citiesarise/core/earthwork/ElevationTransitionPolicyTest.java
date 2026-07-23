package com.cybersammy.citiesarise.core.earthwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ElevationTransitionPolicyTest {
    @Test
    void materializesWholeRoadIntersectionAsOneBlockStep() {
        ElevationZone horizontal = road("horizontal", bounds(0, 4, 9, 3), 64);
        ElevationZone vertical = road("vertical", bounds(3, 0, 3, 9), 65);
        ElevationTransition transition = transition(
                ElevationTransitionType.ROAD_CONNECTION,
                horizontal,
                vertical,
                point(4, 4)
        );

        List<ElevationTransitionPolicy.TransitionPoint> points = ElevationTransitionPolicy.materialize(
                transition,
                horizontal,
                vertical
        );

        assertEquals(9, points.size());
        assertTrue(points.stream().allMatch(ElevationTransitionPolicy.TransitionPoint::step));
        assertTrue(points.stream().allMatch(point -> point.targetElevation() == 65));
    }

    @Test
    void gradesBuildingAccessWithoutReplacingBuildingAnchor() {
        ElevationZone road = road("road", bounds(0, 4, 3, 3), 64);
        ElevationZone building = building("building", bounds(7, 4, 4, 4), 67);
        GridPoint anchor = point(7, 5);
        ElevationTransition transition = transition(
                ElevationTransitionType.BUILDING_ACCESS,
                road,
                building,
                anchor
        );

        List<ElevationTransitionPolicy.TransitionPoint> points = ElevationTransitionPolicy.materialize(
                transition,
                road,
                building
        );

        assertEquals(5, points.size());
        assertFalse(points.stream().anyMatch(point -> point.point().equals(anchor)));
        assertEquals(67, points.getLast().targetElevation());
        assertEquals(3L, points.stream().filter(ElevationTransitionPolicy.TransitionPoint::step).count());
    }

    @Test
    void rejectsElevationChangeLongerThanAvailableAccessPath() {
        ElevationZone road = road("road", bounds(0, 4, 3, 3), 64);
        ElevationZone building = building("building", bounds(4, 4, 4, 4), 84);
        ElevationTransition transition = transition(
                ElevationTransitionType.BUILDING_ACCESS,
                road,
                building,
                point(4, 5)
        );

        assertFalse(ElevationTransitionPolicy.canMaterialize(transition, road, building));
    }

    @Test
    void doesNotBorrowBuildingAccessLengthFromLongRoad() {
        ElevationZone road = road("road", bounds(0, 4, 21, 3), 64);
        ElevationZone building = building("building", bounds(22, 4, 4, 4), 70);
        ElevationTransition transition = transition(
                ElevationTransitionType.BUILDING_ACCESS,
                road,
                building,
                point(22, 5)
        );

        assertEquals(2, ElevationTransitionPolicy.maximumMaterializableDelta(transition, road));
        assertFalse(ElevationTransitionPolicy.canMaterialize(transition, road, building));
    }

    private static ElevationTransition transition(
            ElevationTransitionType type,
            ElevationZone source,
            ElevationZone target,
            GridPoint anchor
    ) {
        return new ElevationTransition(
                type,
                source.sourceElementId(),
                target.sourceElementId(),
                anchor,
                source.targetElevation(),
                target.targetElevation()
        );
    }

    private static ElevationZone road(String id, GridBounds bounds, int elevation) {
        return new ElevationZone(elementId(id), ElevationZoneType.ROAD_SEGMENT, bounds, elevation);
    }

    private static ElevationZone building(String id, GridBounds bounds, int elevation) {
        return new ElevationZone(elementId(id), ElevationZoneType.BUILDING_PAD, bounds, elevation);
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(point(x, z), new GridSize(width, depth));
    }

    private static GridPoint point(int x, int z) {
        return new GridPoint(x, z);
    }

    private static PlanElementId elementId(String value) {
        return new PlanElementId("test/" + value);
    }
}
