package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.earthwork.BuildingAccessResolver;
import com.cybersammy.citiesarise.core.earthwork.ElevationTransition;
import com.cybersammy.citiesarise.core.earthwork.ElevationTransitionType;
import com.cybersammy.citiesarise.core.earthwork.ElevationZone;
import com.cybersammy.citiesarise.core.earthwork.ElevationZoneType;
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
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RegionalElevationPlannerTest {
    @Test
    void preservesMultipleRoadLevelsAndDescribesTheirTransitions() {
        RegionalElevationPlanningResult result = RegionalElevationPlanner.plan(request(), settlementPlan());
        List<ElevationZone> roadZones = result.elevationPlan().zones().stream()
                .filter(zone -> zone.type() == ElevationZoneType.ROAD_SEGMENT)
                .toList();
        List<ElevationTransition> roadTransitions = result.elevationPlan().transitions().stream()
                .filter(transition -> transition.type() == ElevationTransitionType.ROAD_CONNECTION)
                .toList();

        assertTrue(roadZones.stream().map(ElevationZone::targetElevation).distinct().count() > 1L);
        assertEquals(2, roadTransitions.size());
        assertTrue(roadTransitions.stream().allMatch(transition -> transition.elevationDelta() <= 1L));
    }

    @Test
    void describesBuildingAccessWithoutFlatteningTheWholeSettlement() {
        RegionalElevationPlanningResult result = RegionalElevationPlanner.plan(request(), settlementPlan());
        ElevationTransition access = result.elevationPlan().transitions().stream()
                .filter(transition -> transition.type() == ElevationTransitionType.BUILDING_ACCESS)
                .findFirst()
                .orElseThrow();
        ElevationZone building = result.elevationPlan().requiredZone(id("building"));

        assertEquals(id("building"), access.targetZoneId());
        assertTrue(building.bounds().contains(access.anchor()));
        assertTrue(BuildingAccessResolver.isPerimeterPoint(building.bounds(), access.anchor()));
        assertEquals(building.targetElevation(), access.targetElevation());
    }

    @Test
    void assignsOneAuthoritativeElevationToParcelAndBuilding() {
        RegionalElevationPlanningResult result = RegionalElevationPlanner.plan(request(), settlementPlan());
        ElevationZone parcel = result.elevationPlan().requiredZone(id("parcel"));
        ElevationZone building = result.elevationPlan().requiredZone(id("building"));

        assertEquals(ElevationZoneType.PARCEL_PAD, parcel.type());
        assertEquals(building.targetElevation(), parcel.targetElevation());
        assertEquals(
                Integer.toString(parcel.targetElevation()),
                result.settlementPlan().parcels().getFirst().properties()
                        .find(PlanPropertyKeys.PLATFORM_Y)
                        .orElseThrow()
        );
    }

    @Test
    void producesSameRegionalPlanWhenRoadSegmentOrderChanges() {
        SettlementPlan plan = settlementPlan();
        List<RoadSegment> reversed = new ArrayList<>(plan.roadGraph().segments());
        Collections.reverse(reversed);
        SettlementPlan reordered = new SettlementPlan(
                plan.id(),
                new RoadGraph(plan.roadGraph().nodes(), reversed),
                plan.parcels(),
                plan.buildingSlots(),
                plan.tags(),
                plan.properties()
        );

        RegionalElevationPlanningResult first = RegionalElevationPlanner.plan(request(), plan);
        RegionalElevationPlanningResult second = RegionalElevationPlanner.plan(request(), reordered);

        assertEquals(first.elevationPlan(), second.elevationPlan());
    }

    private static SettlementPlan settlementPlan() {
        RoadNode west = node("west", 1, 4);
        RoadNode middleWest = node("middle_west", 4, 4);
        RoadNode middleEast = node("middle_east", 7, 4);
        RoadNode east = node("east", 10, 4);
        Parcel parcel = new Parcel(id("parcel"), bounds(8, 5, 4, 4), Set.of(), PlanProperties.empty());
        BuildingSlot building = new BuildingSlot(
                id("building"),
                parcel.id(),
                bounds(9, 6, 2, 2),
                Set.of(),
                PlanProperties.empty()
        );
        return new SettlementPlan(
                id("settlement"),
                new RoadGraph(
                        List.of(west, middleWest, middleEast, east),
                        List.of(
                                segment("west_road", west, middleWest),
                                segment("middle_road", middleWest, middleEast),
                                segment("east_road", middleEast, east)
                        )
                ),
                List.of(parcel),
                List.of(building),
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static SuburbPlanningRequest request() {
        GridBounds bounds = bounds(0, 0, 14, 10);
        TerrainSurvey survey = TerrainSurvey.sample(
                bounds,
                point -> Optional.of(new TerrainCell(
                        point,
                        64 + (point.x() / 3),
                        false,
                        0.0,
                        BiomeCategory.PLAINS,
                        TerrainCategory.BUILDABLE
                ))
        );
        return new SuburbPlanningRequest(id("settlement"), survey, 42L, SuburbPlanningSettings.defaults());
    }

    private static RoadNode node(String name, int x, int z) {
        return new RoadNode(id(name), new GridPoint(x, z), Set.of(), PlanProperties.empty());
    }

    private static RoadSegment segment(String name, RoadNode start, RoadNode end) {
        return new RoadSegment(id(name), start.id(), end.id(), 1, Set.of(), PlanProperties.empty());
    }

    private static GridBounds bounds(int x, int z, int width, int depth) {
        return new GridBounds(new GridPoint(x, z), new GridSize(width, depth));
    }

    private static PlanElementId id(String name) {
        return new PlanElementId("test/" + name);
    }
}
