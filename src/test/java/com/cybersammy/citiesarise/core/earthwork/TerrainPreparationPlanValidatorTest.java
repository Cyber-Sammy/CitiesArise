package com.cybersammy.citiesarise.core.earthwork;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.BuildingSlot;
import com.cybersammy.citiesarise.core.model.Parcel;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.PlanPropertyKeys;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.model.SettlementPlan;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TerrainPreparationPlanValidatorTest {
    private final TerrainPreparationPlanValidator validator = new TerrainPreparationPlanValidator();

    @Test
    void rejectsConnectedRoadZonesThatDifferByMoreThanOneBlock() {
        RoadNode west = node("west", 0, 0);
        RoadNode middle = node("middle", 3, 0);
        RoadNode east = node("east", 6, 0);
        RoadSegment lower = segment("lower", west, middle, 64);
        RoadSegment upper = segment("upper", middle, east, 66);
        SettlementPlan plan = new SettlementPlan(
                id("settlement"),
                new RoadGraph(List.of(west, middle, east), List.of(lower, upper)),
                List.of(),
                List.of(),
                Set.of(),
                PlanProperties.empty()
        );
        ElevationZone lowerZone = zone(lower, 0, 64);
        ElevationZone upperZone = zone(upper, 3, 66);
        RegionalElevationPlan elevationPlan = new RegionalElevationPlan(
                List.of(lowerZone, upperZone),
                List.of(new ElevationTransition(
                        ElevationTransitionType.ROAD_CONNECTION,
                        lower.id(),
                        upper.id(),
                        middle.point(),
                        64,
                        66
                ))
        );
        TerrainPreparationPlan preparationPlan = TerrainPreparationPlan.of(
                elevationPlan,
                List.of(area(lowerZone), area(upperZone)),
                List.of()
        );

        assertTrue(validator.validate(plan, preparationPlan).stream()
                .anyMatch(error -> error.message().contains("delta exceeds one block")));
    }

    @Test
    void rejectsRoadTransitionAnchorOutsideTheConnectedRoadZones() {
        RoadNode west = node("west", 0, 0);
        RoadNode middle = node("middle", 3, 0);
        RoadNode east = node("east", 6, 0);
        RoadSegment first = segment("first", west, middle, 64);
        RoadSegment second = segment("second", middle, east, 65);
        SettlementPlan plan = roadPlan(List.of(west, middle, east), List.of(first, second));
        ElevationZone firstZone = zone(first, 0, 64);
        ElevationZone secondZone = zone(second, 3, 65);
        RegionalElevationPlan elevationPlan = new RegionalElevationPlan(
                List.of(firstZone, secondZone),
                List.of(new ElevationTransition(
                        ElevationTransitionType.ROAD_CONNECTION,
                        first.id(),
                        second.id(),
                        new GridPoint(20, 20),
                        64,
                        65
                ))
        );
        TerrainPreparationPlan preparationPlan = TerrainPreparationPlan.of(
                elevationPlan,
                List.of(area(firstZone), area(secondZone)),
                List.of()
        );

        assertTrue(validator.validate(plan, preparationPlan).stream()
                .anyMatch(error -> error.message().contains("belong to both road zones")));
    }

    @Test
    void rejectsBuildingAccessAnchorInsideThePadInsteadOfOnItsPerimeter() {
        BuildingFixture fixture = buildingFixture(new GridPoint(3, 1));

        assertTrue(validator.validate(fixture.plan(), fixture.preparationPlan()).stream()
                .anyMatch(error -> error.message().contains("building perimeter")));
    }

    @Test
    void rejectsBuildingAccessAnchorOnTheWrongSideOfThePad() {
        BuildingFixture fixture = buildingFixture(new GridPoint(2, 0));

        assertTrue(validator.validate(fixture.plan(), fixture.preparationPlan()).stream()
                .anyMatch(error -> error.message().contains("nearest perimeter point")));
    }

    @Test
    void rejectsBuildingAccessThatReferencesAMoreDistantRoad() {
        BuildingFixture fixture = buildingFixture(new GridPoint(2, 2), true);

        assertTrue(validator.validate(fixture.plan(), fixture.preparationPlan()).stream()
                .anyMatch(error -> error.message().contains("nearest road zone")));
    }

    private static BuildingFixture buildingFixture(GridPoint accessAnchor) {
        return buildingFixture(accessAnchor, false);
    }

    private static BuildingFixture buildingFixture(GridPoint accessAnchor, boolean useDistantRoad) {
        RoadNode west = node("west", 0, 5);
        RoadNode east = node("east", 6, 5);
        RoadSegment road = segment("road", west, east, 64);
        RoadNode distantWest = node("distant_west", 0, 8);
        RoadNode distantEast = node("distant_east", 6, 8);
        RoadSegment distantRoad = segment("distant_road", distantWest, distantEast, 64);
        GridBounds buildingBounds = new GridBounds(new GridPoint(2, 0), new GridSize(3, 3));
        Parcel parcel = new Parcel(id("parcel"), buildingBounds, Set.of(), PlanProperties.empty());
        BuildingSlot building = new BuildingSlot(
                id("building"),
                parcel.id(),
                buildingBounds,
                Set.of(),
                properties(64)
        );
        SettlementPlan plan = new SettlementPlan(
                id("settlement"),
                buildingRoadGraph(west, east, road, distantWest, distantEast, distantRoad, useDistantRoad),
                List.of(parcel),
                List.of(building),
                Set.of(),
                PlanProperties.empty()
        );
        ElevationZone roadZone = new ElevationZone(
                road.id(),
                ElevationZoneType.ROAD_SEGMENT,
                AxisAlignedGridCorridor.bounds(west.point(), east.point(), road.width()),
                64
        );
        ElevationZone distantRoadZone = new ElevationZone(
                distantRoad.id(),
                ElevationZoneType.ROAD_SEGMENT,
                AxisAlignedGridCorridor.bounds(distantWest.point(), distantEast.point(), distantRoad.width()),
                64
        );
        ElevationZone buildingZone = new ElevationZone(
                building.id(),
                ElevationZoneType.BUILDING_PAD,
                buildingBounds,
                64
        );
        List<ElevationZone> zones = useDistantRoad
                ? List.of(roadZone, distantRoadZone, buildingZone)
                : List.of(roadZone, buildingZone);
        ElevationZone accessRoadZone = useDistantRoad ? distantRoadZone : roadZone;
        RegionalElevationPlan elevationPlan = new RegionalElevationPlan(
                zones,
                List.of(new ElevationTransition(
                        ElevationTransitionType.BUILDING_ACCESS,
                        accessRoadZone.sourceElementId(),
                        building.id(),
                        accessAnchor,
                        64,
                        64
                ))
        );
        List<TerrainPreparationArea> areas = useDistantRoad
                ? List.of(area(roadZone), area(distantRoadZone), area(buildingZone))
                : List.of(area(roadZone), area(buildingZone));
        TerrainPreparationPlan preparationPlan = TerrainPreparationPlan.of(
                elevationPlan,
                areas,
                List.of()
        );
        return new BuildingFixture(plan, preparationPlan);
    }

    private static RoadGraph buildingRoadGraph(
            RoadNode west,
            RoadNode east,
            RoadSegment road,
            RoadNode distantWest,
            RoadNode distantEast,
            RoadSegment distantRoad,
            boolean includeDistantRoad
    ) {
        if (!includeDistantRoad) {
            return new RoadGraph(List.of(west, east), List.of(road));
        }
        return new RoadGraph(
                List.of(west, east, distantWest, distantEast),
                List.of(road, distantRoad)
        );
    }

    private static SettlementPlan roadPlan(List<RoadNode> nodes, List<RoadSegment> segments) {
        return new SettlementPlan(
                id("settlement"),
                new RoadGraph(nodes, segments),
                List.of(),
                List.of(),
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static ElevationZone zone(RoadSegment segment, int x, int elevation) {
        return new ElevationZone(
                segment.id(),
                ElevationZoneType.ROAD_SEGMENT,
                new GridBounds(new GridPoint(x, 0), new GridSize(4, 1)),
                elevation
        );
    }

    private static TerrainPreparationArea area(ElevationZone zone) {
        return new TerrainPreparationArea(
                zone.sourceElementId(),
                zone.bounds(),
                zone.targetElevation(),
                0L,
                0L
        );
    }

    private static RoadNode node(String name, int x, int z) {
        return new RoadNode(id(name), new GridPoint(x, z), Set.of(), PlanProperties.empty());
    }

    private static RoadSegment segment(
            String name,
            RoadNode start,
            RoadNode end,
            int elevation
    ) {
        return new RoadSegment(
                id(name),
                start.id(),
                end.id(),
                1,
                Set.of(),
                properties(elevation)
        );
    }

    private static PlanProperties properties(int elevation) {
        return PlanProperties.of(PlanPropertyKeys.PLATFORM_Y, Integer.toString(elevation));
    }

    private static PlanElementId id(String name) {
        return new PlanElementId("test/" + name);
    }

    private record BuildingFixture(SettlementPlan plan, TerrainPreparationPlan preparationPlan) {
    }
}
