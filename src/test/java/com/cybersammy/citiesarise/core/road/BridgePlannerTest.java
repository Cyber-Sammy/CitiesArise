package com.cybersammy.citiesarise.core.road;

import com.cybersammy.citiesarise.core.earthwork.*;
import com.cybersammy.citiesarise.core.geometry.*;
import com.cybersammy.citiesarise.core.model.*;
import com.cybersammy.citiesarise.core.planning.suburb.*;
import com.cybersammy.citiesarise.core.terrain.*;
import com.cybersammy.citiesarise.core.terrain.policy.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BridgePlannerTest {
    static final PlanElementId ID = new PlanElementId("test:bridge-town");
    static final TerrainPreparationPlan EMPTY = TerrainPreparationPlan.of(new RegionalElevationPlan(List.of(), List.of()), List.of(), List.of());

    @Test void suburbPipelineProducesCrossingsOnASurveyedPond() {
        var policy = request(true,62,BridgeSettings.defaults()).terrainResponsePolicy();
        var ponds=List.of(new GridPoint(23,25),new GridPoint(28,10),new GridPoint(6,19),new GridPoint(24,29),new GridPoint(4,15))
                .stream().map(p -> new GridBounds(p,new GridSize(5,5))).toList();
        var survey=TerrainSurvey.sample(new GridBounds(new GridPoint(0,0),new GridSize(64,48)), point -> {
            boolean water=ponds.stream().anyMatch(p -> p.contains(point));
            return Optional.of(new TerrainCell(point,water?62:65,water,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE));
        });
        var result=SuburbPlanner.defaults().plan(new SuburbPlanningRequest(ID,survey,100,SuburbPlanningSettings.defaults(),policy));
        assertTrue(result.successful(), result.toString());
        assertFalse(result.plan().orElseThrow().roadGraph().bridges().isEmpty());
        assertEquals(2, result.plan().orElseThrow().roadGraph().bridges().size());
        assertTrue(result.plan().orElseThrow().roadGraph().bridges().stream().allMatch(b -> b.length()==32));
        var transformed = com.cybersammy.citiesarise.core.transform.LightDecayTransform.defaults().apply(
                result.plan().orElseThrow(), new com.cybersammy.citiesarise.core.transform.TransformContext(100));
        assertEquals(result.plan().orElseThrow().roadGraph().bridges(), transformed.roadGraph().bridges());
        var placement = new com.cybersammy.citiesarise.minecraft.placement.DebugPlacementPlanConverter()
                .convert(transformed,result.terrainPreparationPlan().orElseThrow());
        assertTrue(placement.operations().stream().anyMatch(op -> op.role().bridge()));
    }

    @Test void connectsExistingRoadsWithAnOpenSpanAndDeterministicBanks() {
        var request = request(true, 62, BridgeSettings.defaults());
        var original = roads(64);
        var result = BridgePlanner.attach(request, original, EMPTY);
        assertEquals(1, result.roadGraph().bridges().size());
        var bridge = result.roadGraph().bridges().getFirst();
        assertEquals(64, bridge.deckY());
        assertEquals(3, bridge.startBankLength());
        assertEquals(3, bridge.endBankLength());
        assertFalse(bridge.bank(6));
        assertEquals(original.roadGraph().segments(), result.roadGraph().segments());
        assertEquals(result, BridgePlanner.attach(request, original, EMPTY));
        assertTrue(BridgePlanner.probePoints(request, original).contains(new GridPoint(10, 8)));
    }

    @Test void rejectsFloodedDeckUnevenApproachesMissingWaterAndExceededLimits() {
        assertTrue(BridgePlanner.attach(request(true, 66, BridgeSettings.defaults()), roads(64), EMPTY).roadGraph().bridges().isEmpty());
        assertTrue(BridgePlanner.attach(request(true, 62, BridgeSettings.defaults()), roads(65), EMPTY).roadGraph().bridges().isEmpty());
        assertTrue(BridgePlanner.attach(request(false, 62, BridgeSettings.defaults()), roads(64), EMPTY).roadGraph().bridges().isEmpty());
        assertTrue(BridgePlanner.attach(request(true, 62, new BridgeSettings(8, 2, 1, 0)), roads(64), EMPTY).roadGraph().bridges().isEmpty());
        assertTrue(BridgePlanner.attach(request(true, 62, new BridgeSettings(24, 0, 1, 0)), roads(64), EMPTY).roadGraph().bridges().isEmpty());
        assertTrue(BridgePlanner.attach(request(true, 62, new BridgeSettings(24, 2, 2, 2)), roads(64), EMPTY).roadGraph().bridges().isEmpty());
    }

    @Test void neverOverlaysOrdinaryEarthworksInsideSpan() {
        var preparation = TerrainPreparationPlan.of(EMPTY.elevationPlan(), List.of(), List.of(
                new TerrainPreparationColumn(new GridPoint(10, 8), ID, 64, 0, 0)));
        assertTrue(BridgePlanner.attach(request(true, 62, BridgeSettings.defaults()), roads(64), preparation).roadGraph().bridges().isEmpty());
    }

    @Test void disabledPolicyDoesNotProbeOrGenerateCrossings() {
        var enabled = request(true, 62, BridgeSettings.defaults());
        var disabled = new SuburbPlanningRequest(ID, enabled.survey(), 1, enabled.settings());
        assertTrue(BridgePlanner.probePoints(disabled, roads(64)).isEmpty());
        assertTrue(BridgePlanner.attach(disabled, roads(64), EMPTY).roadGraph().bridges().isEmpty());
    }

    private static SuburbPlanningRequest request(boolean water, int waterHeight, BridgeSettings settings) {
        var survey = TerrainSurvey.sample(new GridBounds(new GridPoint(0, 0), new GridSize(22, 14)), point -> {
            boolean channel = point.x() >= 7 && point.x() <= 13 && point.z() >= 5;
            return Optional.of(new TerrainCell(point, channel ? waterHeight : 65, channel && water, 0,
                    BiomeCategory.PLAINS, TerrainCategory.BUILDABLE));
        });
        return new SuburbPlanningRequest(ID, survey, 1, SuburbPlanningSettings.defaults(),
                new TerrainResponsePolicy(Map.of(TerrainFeatureType.WATER, TerrainResponse.CROSS_IF_SUPPORTED,
                        TerrainFeatureType.STEEP_SLOPE, TerrainResponse.BUILD_AROUND,
                        TerrainFeatureType.BLOCKED_TERRAIN, TerrainResponse.AVOID), Set.of(InfrastructureCapability.BRIDGE),
                        TerrainAdaptationSettings.disabled(), settings));
    }

    private static SettlementPlan roads(int rightHeight) {
        var nodes = List.of(node("a", 4, 8), node("b", 4, 2), node("c", 16, 2), node("d", 16, 8));
        var segments = List.of(segment("ab", nodes.get(0), nodes.get(1), 64),
                segment("bc", nodes.get(1), nodes.get(2), 64), segment("cd", nodes.get(2), nodes.get(3), rightHeight));
        return new SettlementPlan(ID, new RoadGraph(nodes, segments), List.of(), List.of(), Set.of(), PlanProperties.empty());
    }
    private static RoadNode node(String name, int x, int z) {
        return new RoadNode(ID.child(name), new GridPoint(x, z), Set.of(), PlanProperties.empty());
    }
    private static RoadSegment segment(String name, RoadNode start, RoadNode end, int y) {
        return new RoadSegment(ID.child(name), start.id(), end.id(), 3, Set.of(), PlanProperties.of(PlanPropertyKeys.PLATFORM_Y, Integer.toString(y)));
    }
}
