package com.cybersammy.citiesarise.core.earthwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EarthworkSiteAssessmentTest {
    @Test
    void classifiesDirectModerateAndMajorSites() {
        EarthworkSiteAssessment direct = assessment(0, 0, 0, 4, 0, 0, 0);
        EarthworkSiteAssessment moderate = assessment(6, 0, 0, 4, 3, 2, 0);
        EarthworkSiteAssessment major = assessment(6, 2, 1, 4, 3, 4, 0);

        assertEquals(EarthworkSiteQuality.DIRECT, direct.quality());
        assertEquals(EarthworkSiteQuality.MODERATE, moderate.quality());
        assertEquals(EarthworkSiteQuality.MAJOR, major.quality());
    }

    @Test
    void ranksQuantitativeCostWithoutHardQualityPriority() {
        EarthworkSiteAssessment moderate = assessment(100, 0, 0, 10, 10, 3, 3);
        EarthworkSiteAssessment major = assessment(4, 1, 1, 10, 1, 4, 0);

        assertTrue(major.compareTo(moderate) < 0);
        assertEquals(EarthworkSiteQuality.MAJOR, major.quality());
        assertEquals(5, major.rankingCost());
    }

    @Test
    void ranksLowerCostThenLowerEarthworkDensity() {
        EarthworkSiteAssessment lowerCost = assessment(10, 1, 1, 10, 5, 4, 0);
        EarthworkSiteAssessment higherCost = assessment(10, 2, 1, 10, 5, 4, 0);
        EarthworkSiteAssessment lowerDensity = assessment(10, 1, 1, 10, 10, 4, 0);
        EarthworkSiteAssessment higherDensity = assessment(10, 1, 1, 10, 5, 4, 0);

        assertTrue(lowerCost.compareTo(higherCost) < 0);
        assertTrue(lowerDensity.compareTo(higherDensity) < 0);
        assertEquals(1.0, lowerDensity.earthworkDensity());
    }

    @Test
    void ignoresUnchangedFootprintColumnsWhenCalculatingDensity() {
        EarthworkSiteAssessment compact = assessment(10, 0, 0, 5, 2, 5, 0);
        EarthworkSiteAssessment broad = assessment(10, 0, 0, 100, 2, 5, 0);

        assertEquals(5.0, compact.earthworkDensity());
        assertEquals(compact.earthworkDensity(), broad.earthworkDensity());
    }

    @Test
    void rejectsQualityThatDoesNotMatchValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EarthworkSiteAssessment(EarthworkSiteQuality.DIRECT, 0, 0, 1, 1, 1, 1, 0)
        );
    }

    @Test
    void evaluatesNormalizedPreparationColumnsAgainstPreferredDepths() {
        TerrainPreparationPlan plan = TerrainPreparationPlan.of(
                elevationPlan(),
                List.of(area(4, 5)),
                List.of(column(0, 4, 0), column(1, 0, 5))
        );

        EarthworkSiteAssessment assessment = EarthworkSiteAssessment.evaluate(plan, 3, 3);

        assertEquals(EarthworkSiteQuality.MAJOR, assessment.quality());
        assertEquals(3, assessment.preferredDepthExcess());
        assertEquals(2, assessment.columnsAbovePreferred());
        assertEquals(9, assessment.totalVolume());
        assertEquals(2, assessment.footprintColumnCount());
        assertEquals(2, assessment.earthworkColumnCount());
        assertEquals(4.5, assessment.earthworkDensity());
    }

    private static EarthworkSiteAssessment assessment(
            long volume,
            long preferredExcess,
            int columnsAbovePreferred,
            int footprintColumns,
            int earthworkColumns,
            int maximumCut,
            int maximumFill
    ) {
        EarthworkSiteQuality quality = EarthworkSiteQuality.MAJOR;
        if (volume == 0) {
            quality = EarthworkSiteQuality.DIRECT;
        } else if (preferredExcess == 0) {
            quality = EarthworkSiteQuality.MODERATE;
        }
        return new EarthworkSiteAssessment(
                quality,
                preferredExcess,
                columnsAbovePreferred,
                volume,
                footprintColumns,
                earthworkColumns,
                maximumCut,
                maximumFill
        );
    }

    private static RegionalElevationPlan elevationPlan() {
        return new RegionalElevationPlan(
                List.of(new ElevationZone(
                        new PlanElementId("area/test"),
                        ElevationZoneType.ROAD_SEGMENT,
                        new GridBounds(new GridPoint(0, 0), new GridSize(2, 1)),
                        64
                )),
                List.of()
        );
    }

    private static TerrainPreparationArea area(long cutVolume, long fillVolume) {
        return new TerrainPreparationArea(
                new PlanElementId("area/test"),
                new GridBounds(new GridPoint(0, 0), new GridSize(2, 1)),
                64,
                cutVolume,
                fillVolume
        );
    }

    private static TerrainPreparationColumn column(int x, int cutDepth, int fillDepth) {
        return new TerrainPreparationColumn(
                new GridPoint(x, 0),
                new PlanElementId("area/test"),
                64,
                cutDepth,
                fillDepth
        );
    }
}
