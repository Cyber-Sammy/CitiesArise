package com.cybersammy.citiesarise.core.earthwork;

import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupportLiningPlanTest {
    private static final PlanElementId ROAD = new PlanElementId("test:road");
    private static final GridPoint POINT = new GridPoint(-827, -2609);
    private static final GridBounds BOUNDS = new GridBounds(POINT, new GridSize(5, 5));
    private static final RegionalElevationPlan ELEVATIONS = new RegionalElevationPlan(
            List.of(new ElevationZone(ROAD, ElevationZoneType.ROAD_SEGMENT, BOUNDS, 65)), List.of());

    @Test void boundedEnvelopeAccountsForExistingNaturalRoofAndFill() {
        var natural = new TerrainPreparationColumn(POINT, ROAD, 65, 0, 0);
        var fill = new TerrainPreparationColumn(new GridPoint(POINT.x()+1, POINT.z()), ROAD, 65, 0, 3);
        var lining = SupportLiningPlan.create(ELEVATIONS, List.of(natural, fill), 4);
        assertEquals(60, lining.columns().getFirst().bottomY());
        assertEquals(64, lining.columns().getFirst().topY());
        assertEquals(58, lining.columns().getLast().bottomY());
        assertEquals(12, lining.volume());
        assertTrue(SupportLiningPlan.create(ELEVATIONS, List.of(natural), 0).columns().isEmpty());
    }

    @Test void optionalParcelShouldersNeverAcquireMandatoryLining() {
        var shoulder = new TerrainPreparationColumn(POINT, ROAD, 65, 0, 1, TerrainPreparationColumnType.PARCEL_SHOULDER);
        assertTrue(SupportLiningPlan.create(ELEVATIONS, List.of(shoulder), 4).columns().isEmpty());
    }

    @Test void constructionBudgetAndRankingIncludeLiningWithoutFalsifyingEarthwork() {
        var settings = new TerrainTransitionSettings(1, 2, 2, 3, 3, 3, 3, true, 2, 4);
        var plan = TerrainPreparationPlan.of(ELEVATIONS, List.of(new TerrainPreparationArea(ROAD, BOUNDS, 65, 0, 0)),
                List.of(new TerrainPreparationColumn(POINT, ROAD, 65, 0, 0)), settings);
        assertEquals(0, plan.totalVolume());
        assertEquals(5, plan.constructionVolume());
        var assessment = EarthworkSiteAssessment.evaluate(plan, 3, 3);
        assertEquals(5, assessment.rankingCost());
        assertEquals(0, assessment.earthworkColumnCount());
        assertEquals(EarthworkSiteQuality.DIRECT, assessment.quality());
        assertThrows(IllegalArgumentException.class, () -> new TerrainTransitionSettings(1, 2, 2, 3, 3, 3, 3, true, 2, 5));
    }
}
