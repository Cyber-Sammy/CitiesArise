package com.cybersammy.citiesarise.core.earthwork;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
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
                PlanProperties.of(PlanPropertyKeys.PLATFORM_Y, Integer.toString(elevation))
        );
    }

    private static PlanElementId id(String name) {
        return new PlanElementId("test/" + name);
    }
}
