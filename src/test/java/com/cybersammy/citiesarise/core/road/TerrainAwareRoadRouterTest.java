package com.cybersammy.citiesarise.core.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.policy.InfrastructureCapability;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainFeatureType;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponse;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TerrainAwareRoadRouterTest {
    private static final TerrainAwareRoadRouter ROUTER = new TerrainAwareRoadRouter();

    @Test
    void returnsStraightRouteAcrossUniformTerrain() {
        TerrainSurvey survey = survey(5, 5, point -> cell(point, 64, false, 0.0, TerrainCategory.BUILDABLE));

        RoadRoute route = ROUTER.route(request(
                survey,
                new GridPoint(0, 2),
                new GridPoint(4, 2),
                1,
                0,
                TerrainResponsePolicy.defaults(),
                RoadRoutingCostPolicy.defaults()
        )).route().orElseThrow();

        assertEquals(List.of(
                new GridPoint(0, 2),
                new GridPoint(1, 2),
                new GridPoint(2, 2),
                new GridPoint(3, 2),
                new GridPoint(4, 2)
        ), route.points());
        assertTrue(route.crossingCandidates().isEmpty());
    }

    @Test
    void routesAroundWaterWhenProfileAvoidsIt() {
        GridPoint water = new GridPoint(2, 2);
        TerrainSurvey survey = survey(
                5,
                5,
                point -> cell(point, 64, point.equals(water), 0.0, TerrainCategory.BUILDABLE)
        );

        RoadRoute route = ROUTER.route(request(
                survey,
                new GridPoint(0, 2),
                new GridPoint(4, 2),
                1,
                0,
                TerrainResponsePolicy.defaults(),
                RoadRoutingCostPolicy.defaults()
        )).route().orElseThrow();

        assertFalse(route.points().contains(water));
        assertTrue(route.points().size() > 5);
    }

    @Test
    void prefersLongerFlatRouteOverLargeElevationChanges() {
        TerrainSurvey survey = survey(
                5,
                5,
                point -> cell(
                        point,
                        point.equals(new GridPoint(2, 2)) ? 70 : 64,
                        false,
                        0.0,
                        TerrainCategory.BUILDABLE
                )
        );

        RoadRoute route = ROUTER.route(request(
                survey,
                new GridPoint(0, 2),
                new GridPoint(4, 2),
                1,
                0,
                TerrainResponsePolicy.defaults(),
                RoadRoutingCostPolicy.defaults()
        )).route().orElseThrow();

        assertFalse(route.points().contains(new GridPoint(2, 2)));
    }

    @Test
    void returnsControlledFailureWhenBarrierSeparatesEndpoints() {
        TerrainSurvey survey = survey(
                5,
                5,
                point -> cell(
                        point,
                        64,
                        false,
                        0.0,
                        point.x() == 2 ? TerrainCategory.BLOCKED : TerrainCategory.BUILDABLE
                )
        );

        RoadRoutingResult result = ROUTER.route(request(
                survey,
                new GridPoint(0, 2),
                new GridPoint(4, 2),
                1,
                0,
                TerrainResponsePolicy.defaults(),
                RoadRoutingCostPolicy.defaults()
        ));

        assertFalse(result.successful());
        assertEquals(Optional.of(RoadRoutingFailureReason.NO_ROUTE), result.failureReason());
    }

    @Test
    void emitsSemanticCrossingCandidateWhenCapabilityAllowsCrossing() {
        GridPoint water = new GridPoint(2, 2);
        TerrainSurvey survey = survey(
                5,
                5,
                point -> cell(point, 64, point.equals(water), 0.0, TerrainCategory.BUILDABLE)
        );
        TerrainResponsePolicy crossingPolicy = new TerrainResponsePolicy(
                Map.of(
                        TerrainFeatureType.WATER,
                        TerrainResponse.CROSS_IF_SUPPORTED,
                        TerrainFeatureType.BLOCKED_TERRAIN,
                        TerrainResponse.AVOID,
                        TerrainFeatureType.STEEP_SLOPE,
                        TerrainResponse.TERRAFORM
                ),
                Set.of(InfrastructureCapability.BRIDGE)
        );

        RoadRoute route = ROUTER.route(request(
                survey,
                new GridPoint(0, 2),
                new GridPoint(4, 2),
                1,
                0,
                crossingPolicy,
                new RoadRoutingCostPolicy(10, 0, 0, 0, 0)
        )).route().orElseThrow();

        assertEquals(1, route.crossingCandidates().size());
        RoadCrossingCandidate crossing = route.crossingCandidates().getFirst();
        assertEquals(TerrainFeatureType.WATER, crossing.featureType());
        assertEquals(water, crossing.entry());
        assertEquals(water, crossing.exit());
    }

    @Test
    void roadWidthKeepsWholeCorridorAwayFromWater() {
        GridPoint water = new GridPoint(3, 2);
        TerrainSurvey survey = survey(
                7,
                7,
                point -> cell(point, 64, point.equals(water), 0.0, TerrainCategory.BUILDABLE)
        );

        RoadRoute route = ROUTER.route(request(
                survey,
                new GridPoint(1, 3),
                new GridPoint(5, 3),
                3,
                0,
                TerrainResponsePolicy.defaults(),
                RoadRoutingCostPolicy.defaults()
        )).route().orElseThrow();

        for (int index = 1; index < route.points().size(); index++) {
            GridBounds corridor = AxisAlignedGridCorridor.bounds(
                    route.points().get(index - 1),
                    route.points().get(index),
                    3
            );
            assertFalse(corridor.contains(water));
        }
    }

    @Test
    void evaluatesSupportShoulderOutsideRoutingBounds() {
        GridPoint water = new GridPoint(3, 0);
        TerrainSurvey survey = survey(
                7,
                5,
                point -> cell(point, 64, point.equals(water), 0.0, TerrainCategory.BUILDABLE)
        );
        GridBounds routingBounds = new GridBounds(new GridPoint(1, 1), new GridSize(5, 3));

        RoadRoute route = ROUTER.route(new RoadRoutingRequest(
                survey,
                routingBounds,
                survey.bounds(),
                new GridPoint(1, 1),
                new GridPoint(5, 1),
                1,
                1,
                0.25,
                TerrainResponsePolicy.defaults(),
                RoadRoutingCostPolicy.defaults(),
                List.of(),
                List.of()
        )).route().orElseThrow();

        assertFalse(route.points().contains(new GridPoint(3, 1)));
        assertTrue(route.points().stream().allMatch(routingBounds::contains));
    }

    @Test
    void repeatedRoutingUsesStableTieBreaking() {
        GridPoint water = new GridPoint(2, 2);
        TerrainSurvey survey = survey(
                5,
                5,
                point -> cell(point, 64, point.equals(water), 0.0, TerrainCategory.BUILDABLE)
        );
        RoadRoutingRequest request = request(
                survey,
                new GridPoint(0, 2),
                new GridPoint(4, 2),
                1,
                0,
                TerrainResponsePolicy.defaults(),
                RoadRoutingCostPolicy.defaults()
        );

        assertEquals(
                ROUTER.route(request).route().orElseThrow(),
                ROUTER.route(request).route().orElseThrow()
        );
    }

    @Test
    void reusesTerrainFootprintsAcrossRoutes() {
        TerrainSurvey survey = survey(7, 7, point -> cell(point, 64, false, 0.0, TerrainCategory.BUILDABLE));
        RoadRoutingRequest request = request(
                survey,
                new GridPoint(1, 3),
                new GridPoint(5, 3),
                1,
                1,
                TerrainResponsePolicy.defaults(),
                RoadRoutingCostPolicy.defaults()
        );
        RoadTerrainEvaluationCache terrainEvaluations = new RoadTerrainEvaluationCache();

        assertTrue(ROUTER.route(request, terrainEvaluations).successful());
        int cachedFootprints = terrainEvaluations.size();
        assertTrue(cachedFootprints > 0);

        assertTrue(ROUTER.route(request, terrainEvaluations).successful());
        assertEquals(cachedFootprints, terrainEvaluations.size());
    }

    @Test
    void sharedTerrainCacheDoesNotCacheDynamicReservations() {
        TerrainSurvey survey = survey(7, 7, point -> cell(point, 64, false, 0.0, TerrainCategory.BUILDABLE));
        RoadRoutingRequest unrestricted = request(
                survey,
                new GridPoint(1, 3),
                new GridPoint(5, 3),
                1,
                0,
                TerrainResponsePolicy.defaults(),
                RoadRoutingCostPolicy.defaults()
        );
        RoadRoutingRequest blocked = new RoadRoutingRequest(
                survey,
                survey.bounds(),
                survey.bounds(),
                unrestricted.start(),
                unrestricted.destination(),
                unrestricted.roadWidth(),
                unrestricted.supportRadius(),
                unrestricted.maxBuildableSlope(),
                unrestricted.terrainResponsePolicy(),
                unrestricted.terrainAdaptationPlan(),
                unrestricted.costPolicy(),
                List.of(new GridBounds(new GridPoint(3, 0), new GridSize(1, 7))),
                List.of()
        );
        RoadTerrainEvaluationCache terrainEvaluations = new RoadTerrainEvaluationCache();

        assertTrue(ROUTER.route(unrestricted, terrainEvaluations).successful());
        assertFalse(ROUTER.route(blocked, terrainEvaluations).successful());
    }

    private static RoadRoutingRequest request(
            TerrainSurvey survey,
            GridPoint start,
            GridPoint destination,
            int width,
            int supportRadius,
            TerrainResponsePolicy responsePolicy,
            RoadRoutingCostPolicy costPolicy
    ) {
        return new RoadRoutingRequest(
                survey,
                survey.bounds(),
                survey.bounds(),
                start,
                destination,
                width,
                supportRadius,
                0.25,
                responsePolicy,
                costPolicy,
                List.of(),
                List.of()
        );
    }

    private static TerrainSurvey survey(int width, int depth, CellFactory cellFactory) {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(width, depth));
        return TerrainSurvey.sample(bounds, point -> Optional.of(cellFactory.create(point)));
    }

    private static TerrainCell cell(
            GridPoint point,
            int height,
            boolean water,
            double slope,
            TerrainCategory terrainCategory
    ) {
        return new TerrainCell(
                point,
                height,
                water,
                slope,
                BiomeCategory.PLAINS,
                terrainCategory
        );
    }

    @FunctionalInterface
    private interface CellFactory {
        TerrainCell create(GridPoint point);
    }
}
