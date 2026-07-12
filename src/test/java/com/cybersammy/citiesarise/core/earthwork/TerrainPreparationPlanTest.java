package com.cybersammy.citiesarise.core.earthwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TerrainPreparationPlanTest {
    @Test
    void reportsAcceptedWhenNoEarthworkIsRequired() {
        TerrainPreparationPlan plan = TerrainPreparationPlan.of(List.of(area(0L, 0L)));

        assertEquals(TerrainPreparationStatus.ACCEPTED, plan.status());
        assertFalse(plan.requiresEarthworks());
        assertEquals(0L, plan.totalVolume());
    }

    @Test
    void reportsAcceptedWithEarthworksAndSumsVolumes() {
        TerrainPreparationPlan plan = TerrainPreparationPlan.of(List.of(area(4L, 2L), area(3L, 5L)));

        assertEquals(TerrainPreparationStatus.ACCEPTED_WITH_EARTHWORKS, plan.status());
        assertTrue(plan.requiresEarthworks());
        assertEquals(7L, plan.cutVolume());
        assertEquals(7L, plan.fillVolume());
        assertEquals(14L, plan.totalVolume());
    }

    @Test
    void rejectsInconsistentAggregateVolumes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainPreparationPlan(
                        TerrainPreparationStatus.ACCEPTED_WITH_EARTHWORKS,
                        List.of(area(1L, 2L)),
                        2L,
                        2L
                )
        );
    }

    @Test
    void rejectsRejectedStatusBecauseRejectedSitesHaveNoPreparationPlan() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainPreparationPlan(TerrainPreparationStatus.REJECTED, List.of(), 0L, 0L)
        );
    }

    @Test
    void rejectsAcceptedStatusWhenEarthworkVolumeIsNotZero() {
        TerrainPreparationArea area = area(1L, 0L);

        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainPreparationPlan(TerrainPreparationStatus.ACCEPTED, List.of(area), 1L, 0L)
        );
    }

    private static TerrainPreparationArea area(long cutVolume, long fillVolume) {
        return new TerrainPreparationArea(
                new PlanElementId("area/test"),
                new GridBounds(new GridPoint(0, 0), new GridSize(2, 2)),
                64,
                cutVolume,
                fillVolume
        );
    }
}
