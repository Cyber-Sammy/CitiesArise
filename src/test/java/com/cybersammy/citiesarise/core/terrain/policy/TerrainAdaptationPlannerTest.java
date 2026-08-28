package com.cybersammy.citiesarise.core.terrain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TerrainAdaptationPlannerTest {
    private static final GridBounds BOUNDS = new GridBounds(new GridPoint(0, 0), new GridSize(5, 5));
    private final TerrainAdaptationPlanner planner = new TerrainAdaptationPlanner();

    @Test
    void sensitivityChangesSmallPondDecision() {
        TerrainSurvey survey = survey(Set.of(new GridPoint(2, 2)), Set.of());

        TerrainAdaptationPlan aggressive = planner.plan(survey, 0.75, policy(0.0));
        TerrainAdaptationPlan preserving = planner.plan(survey, 0.75, policy(1.0));

        assertTrue(aggressive.permitsCurrentPlacement(new GridPoint(2, 2), TerrainFeatureType.WATER));
        assertFalse(preserving.permitsCurrentPlacement(new GridPoint(2, 2), TerrainFeatureType.WATER));
    }

    @Test
    void largeConnectedPondRemainsPreservedAtModerateSensitivity() {
        Set<GridPoint> water = Set.of(
                new GridPoint(1, 1),
                new GridPoint(2, 1),
                new GridPoint(3, 1),
                new GridPoint(1, 2),
                new GridPoint(2, 2),
                new GridPoint(3, 2)
        );

        TerrainAdaptationPlan plan = planner.plan(survey(water, Set.of()), 0.75, policy(0.5));

        assertEquals(TerrainPlanningAction.ROUTE_AROUND, plan.features().getFirst().action());
    }

    @Test
    void explicitPreserveIsNeverOverriddenBySensitivity() {
        Map<TerrainFeatureType, TerrainResponse> responses = responses();
        responses.put(TerrainFeatureType.WATER, TerrainResponse.PRESERVE);
        TerrainResponsePolicy policy = new TerrainResponsePolicy(
                responses,
                Set.of(),
                new TerrainAdaptationSettings(0.0, 100, 100, 10_000L)
        );

        TerrainAdaptationPlan plan = planner.plan(
                survey(Set.of(new GridPoint(2, 2)), Set.of()),
                0.75,
                policy
        );

        assertEquals(TerrainPlanningAction.PRESERVE_IN_PLACE, plan.features().getFirst().action());
    }

    @Test
    void repeatedPlanningProducesIdenticalFeaturePlan() {
        TerrainSurvey survey = survey(
                Set.of(new GridPoint(1, 1), new GridPoint(2, 1)),
                Set.of(new GridPoint(4, 4))
        );
        TerrainResponsePolicy policy = policy(0.25);

        TerrainAdaptationPlan first = planner.plan(survey, 0.75, policy);
        TerrainAdaptationPlan second = planner.plan(survey, 0.75, policy);

        assertEquals(first.features(), second.features());
    }

    @Test
    void adaptationLimitsAreInclusive() {
        TerrainAdaptationSettings settings = new TerrainAdaptationSettings(0.5, 8, 4, 20L);

        assertTrue(settings.permits(new TerrainFeatureMetrics(4, 2, 10L)));
        assertFalse(settings.permits(new TerrainFeatureMetrics(5, 2, 10L)));
        assertFalse(settings.permits(new TerrainFeatureMetrics(4, 3, 10L)));
        assertFalse(settings.permits(new TerrainFeatureMetrics(4, 2, 11L)));
    }

    @Test
    void maximumSensitivityRejectsEveryPositiveFeature() {
        TerrainAdaptationSettings settings = new TerrainAdaptationSettings(1.0, 100, 100, 100L);

        assertFalse(settings.permits(new TerrainFeatureMetrics(1, 0, 0L)));
    }

    @Test
    void adaptationSettingsRejectInvalidValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainAdaptationSettings(Double.NaN, 1, 1, 1L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainAdaptationSettings(-0.1, 1, 1, 1L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainAdaptationSettings(0.5, -1, 1, 1L)
        );
    }

    private static TerrainResponsePolicy policy(double sensitivity) {
        return new TerrainResponsePolicy(
                responses(),
                Set.of(),
                new TerrainAdaptationSettings(sensitivity, 8, 4, 64L)
        );
    }

    private static Map<TerrainFeatureType, TerrainResponse> responses() {
        Map<TerrainFeatureType, TerrainResponse> responses = new EnumMap<>(TerrainFeatureType.class);
        responses.put(TerrainFeatureType.WATER, TerrainResponse.BUILD_AROUND);
        responses.put(TerrainFeatureType.BLOCKED_TERRAIN, TerrainResponse.AVOID);
        responses.put(TerrainFeatureType.STEEP_SLOPE, TerrainResponse.BUILD_AROUND);
        return responses;
    }

    private static TerrainSurvey survey(Set<GridPoint> water, Set<GridPoint> steep) {
        List<TerrainCell> cells = new ArrayList<>();
        for (int z = BOUNDS.minZ(); z < BOUNDS.maxZExclusive(); z++) {
            for (int x = BOUNDS.minX(); x < BOUNDS.maxXExclusive(); x++) {
                GridPoint point = new GridPoint(x, z);
                cells.add(new TerrainCell(
                        point,
                        64,
                        water.contains(point),
                        steep.contains(point) ? 1.0 : 0.0,
                        BiomeCategory.PLAINS,
                        TerrainCategory.BUILDABLE
                ));
            }
        }
        return new TerrainSurvey(BOUNDS, cells);
    }
}
