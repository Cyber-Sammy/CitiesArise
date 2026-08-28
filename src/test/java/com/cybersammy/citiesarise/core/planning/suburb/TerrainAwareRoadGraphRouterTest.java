package com.cybersammy.citiesarise.core.planning.suburb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.citiesarise.core.geometry.AxisAlignedGridCorridor;
import com.cybersammy.citiesarise.core.geometry.GridBounds;
import com.cybersammy.citiesarise.core.geometry.GridPoint;
import com.cybersammy.citiesarise.core.geometry.GridSize;
import com.cybersammy.citiesarise.core.model.PlanElementId;
import com.cybersammy.citiesarise.core.model.PlanProperties;
import com.cybersammy.citiesarise.core.model.RoadGraph;
import com.cybersammy.citiesarise.core.model.RoadNode;
import com.cybersammy.citiesarise.core.model.RoadSegment;
import com.cybersammy.citiesarise.core.terrain.BiomeCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCategory;
import com.cybersammy.citiesarise.core.terrain.TerrainCell;
import com.cybersammy.citiesarise.core.terrain.TerrainSurvey;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainAdaptationSettings;
import com.cybersammy.citiesarise.core.terrain.policy.InfrastructureCapability;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainFeatureType;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponse;
import com.cybersammy.citiesarise.core.terrain.policy.TerrainResponsePolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TerrainAwareRoadGraphRouterTest {
    private static final TerrainAwareRoadGraphRouter ROUTER = new TerrainAwareRoadGraphRouter();

    @Test
    void rejectsIndependentSegmentsThatCanOnlyCrossOutsideSharedNodes() {
        TerrainSurvey survey = survey(9, 9, point -> cell(point, false));
        RoadGraph source = graph(
                List.of(
                        node("horizontal-start", 0, 4),
                        node("horizontal-end", 8, 4),
                        node("vertical-start", 4, 0),
                        node("vertical-end", 4, 8)
                ),
                List.of(
                        segment("horizontal", "horizontal-start", "horizontal-end"),
                        segment("vertical", "vertical-start", "vertical-end")
                )
        );

        assertTrue(ROUTER.route(request(survey, TerrainResponsePolicy.defaults()), survey.bounds(), source, List.of())
                .isEmpty());
    }

    @Test
    void permitsConnectedSegmentsToOverlapAtSharedJunction() {
        TerrainSurvey survey = survey(9, 9, point -> cell(point, false));
        RoadGraph source = graph(
                List.of(
                        node("west", 0, 4),
                        node("junction", 4, 4),
                        node("north", 4, 0)
                ),
                List.of(
                        segment("west-link", "west", "junction"),
                        segment("north-link", "junction", "north")
                )
        );

        assertTrue(ROUTER.route(request(survey, TerrainResponsePolicy.defaults()), survey.bounds(), source, List.of())
                .isPresent());
    }

    @Test
    void routesAroundCrossingFeatureUntilCrossingsCanBeMaterialized() {
        GridPoint water = new GridPoint(4, 4);
        TerrainSurvey survey = survey(9, 9, point -> cell(point, point.equals(water)));
        TerrainResponsePolicy bridgePolicy = new TerrainResponsePolicy(
                Map.of(
                        TerrainFeatureType.WATER,
                        TerrainResponse.CROSS_IF_SUPPORTED,
                        TerrainFeatureType.BLOCKED_TERRAIN,
                        TerrainResponse.AVOID,
                        TerrainFeatureType.STEEP_SLOPE,
                        TerrainResponse.TERRAFORM
                ),
                Set.of(InfrastructureCapability.BRIDGE),
                new TerrainAdaptationSettings(0.0, 16, 4, 64L)
        );
        RoadGraph source = graph(
                List.of(node("west", 0, 4), node("east", 8, 4)),
                List.of(segment("main", "west", "east"))
        );

        RoadGraph routed = ROUTER.route(request(survey, bridgePolicy), survey.bounds(), source, List.of())
                .orElseThrow();

        assertFalse(roadCorridors(routed).stream().anyMatch(bounds -> bounds.contains(water)));
    }

    private static SuburbPlanningRequest request(TerrainSurvey survey, TerrainResponsePolicy policy) {
        return new SuburbPlanningRequest(
                new PlanElementId("test:settlement"),
                survey,
                1L,
                SuburbPlanningSettings.defaults(),
                policy
        );
    }

    private static RoadGraph graph(List<RoadNode> nodes, List<RoadSegment> segments) {
        return new RoadGraph(nodes, segments);
    }

    private static RoadNode node(String id, int x, int z) {
        return new RoadNode(
                id(id),
                new GridPoint(x, z),
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static RoadSegment segment(String id, String start, String end) {
        return new RoadSegment(
                id(id),
                id(start),
                id(end),
                1,
                Set.of(),
                PlanProperties.empty()
        );
    }

    private static PlanElementId id(String value) {
        return new PlanElementId("test:" + value);
    }

    private static List<GridBounds> roadCorridors(RoadGraph graph) {
        Map<PlanElementId, RoadNode> nodes = new HashMap<>();
        for (RoadNode node : graph.nodes()) {
            nodes.put(node.id(), node);
        }
        return graph.segments().stream()
                .map(segment -> AxisAlignedGridCorridor.bounds(
                        nodes.get(segment.startNodeId()).point(),
                        nodes.get(segment.endNodeId()).point(),
                        segment.width()
                ))
                .toList();
    }

    private static TerrainSurvey survey(int width, int depth, CellFactory cellFactory) {
        GridBounds bounds = new GridBounds(new GridPoint(0, 0), new GridSize(width, depth));
        return TerrainSurvey.sample(bounds, point -> Optional.of(cellFactory.create(point)));
    }

    private static TerrainCell cell(GridPoint point, boolean water) {
        return new TerrainCell(
                point,
                64,
                water,
                0.0,
                BiomeCategory.PLAINS,
                TerrainCategory.BUILDABLE
        );
    }

    @FunctionalInterface
    private interface CellFactory {
        TerrainCell create(GridPoint point);
    }
}
