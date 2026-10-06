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
    @Test void unequalNaturalBanksAreOptInAndRespectFittedRunAndLocalClearance() {
        for (int rise : List.of(-1,1)) for (boolean water : List.of(false,true)) {
            var settings = new BridgeSettings(48,1,1,0,true,2,65536,32,1);
            var original = request(water,61,settings);
            var survey = TerrainSurvey.sample(original.survey().bounds(), p -> {
                var c = original.survey().findCell(p).orElseThrow();
                return Optional.of(new TerrainCell(p,p.x()>=14?65+rise:c.height(),c.water(),0,c.biomeCategory(),c.terrainCategory()));
            });
            var req = new SuburbPlanningRequest(ID,survey,1,original.settings(),original.terrainResponsePolicy());
            var result = BridgePlanner.attach(req,roads(64+rise),EMPTY);
            assertEquals(1,result.roadGraph().bridges().size());
            var b = result.roadGraph().bridges().getFirst();
            assertEquals(64+rise,b.endDeckY());
            assertEquals(result,BridgePlanner.attach(req,roads(64+rise),EMPTY));
            assertTrue(BridgePlanner.attach(new SuburbPlanningRequest(ID,survey,1,original.settings(),
                    request(water,61,new BridgeSettings(48,1,1,0,true,2)).terrainResponsePolicy()),roads(64+rise),EMPTY)
                    .roadGraph().bridges().isEmpty());
            // Extending the real bank leaves only five open rows: no room for the rise.
            var shortSurvey=TerrainSurvey.sample(survey.bounds(),p -> p.x()>=7 && p.x()<=8
                    ? Optional.of(new TerrainCell(p,65,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)):survey.findCell(p));
            assertTrue(BridgePlanner.attach(new SuburbPlanningRequest(ID,shortSurvey,1,req.settings(),req.terrainResponsePolicy()),
                    roads(64+rise),EMPTY).roadGraph().bridges().isEmpty());
            var lowPoint=b.point(rise>0?b.startBankLength():b.length()-b.endBankLength(),1);
            var blocked=TerrainSurvey.sample(survey.bounds(),p -> p.equals(lowPoint)
                    ? Optional.of(new TerrainCell(p,65,water,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)):survey.findCell(p));
            assertTrue(BridgePlanner.attach(new SuburbPlanningRequest(ID,blocked,1,req.settings(),req.terrainResponsePolicy()),
                    roads(64+rise),EMPTY).roadGraph().bridges().isEmpty());
        }
    }

    @Test void gradedDeckHasHalfBlockWalkAndExactStructuralBudgetInAllDirections() {
        for (var end : List.of(new GridPoint(30,0),new GridPoint(-30,0),new GridPoint(0,30),new GridPoint(0,-30))) {
            for (int rise : List.of(-3,-2,-1,0,1,2,3)) {
                var b=new BridgePlan(ID,ID.child("a"),ID.child("b"),new GridPoint(0,0),end,5,64,2,3,3,64+rise);
                double previous=65;
                int steps=0;
                for(int d=0;d<=b.length();d++) {
                    double walking=b.deckElevation(d)+1+(b.transitionStep(d)?0.5:0);
                    assertTrue(Math.abs(walking-previous)<=0.5,"Unwalkable transition at "+d);
                    if(b.bank(d)) assertFalse(b.transitionStep(d));
                    if(b.transitionStep(d)) steps++;
                    previous=walking;
                }
                assertEquals(65+rise,previous);
                assertEquals(Math.abs(rise),steps);
                var operations=new com.cybersammy.citiesarise.minecraft.placement.ProceduralBridgePlacementProvider().create(b);
                assertEquals(b.constructionVolume(),operations.stream().filter(op -> op.role()!=
                        com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.BRIDGE_CLEARANCE).count());
                assertEquals(operations.size(),operations.stream().map(op->op.position()).distinct().count());
            }
        }
        assertThrows(IllegalArgumentException.class,()->new BridgePlan(ID,ID.child("a"),ID.child("b"),
                new GridPoint(0,0),new GridPoint(10,0),3,64,1,3,3,65));
    }

    @Test void rejectedFootingTriesAnotherCandidateWithoutLosingDistricts() {
        var checks=new ArrayList<GridPoint>();
        var request=request(true,62,new BridgeSettings(48,1,1,0,false,2,65536,4));
        var result=BridgePlanner.attachWaterCrossings(request,parallelRoads(),EMPTY, candidate -> {
            var bridge=candidate.roadGraph().bridges().getLast(); checks.add(bridge.start());
            return bridge.start().z()==12;
        });
        assertEquals(2,checks.size());
        assertEquals(1,result.roadGraph().bridges().size());
        assertEquals(12,result.roadGraph().bridges().getFirst().start().z());
        assertEquals(parallelRoads().roadGraph().segments(),result.roadGraph().segments());
    }

    @Test void failedChecksAreBoundedAndNotRetriedAsShortcuts() {
        var checks=new java.util.concurrent.atomic.AtomicInteger();
        var request=request(true,62,new BridgeSettings(48,2,1,0,false,2,65536,1));
        var result=BridgePlanner.attach(request,parallelRoads(),EMPTY,candidate -> {checks.incrementAndGet();return false;});
        assertEquals(1,checks.get());
        assertTrue(result.roadGraph().bridges().isEmpty());
    }

    @Test void structuralVolumeIncludesDeckAbutmentsAndRailsAndBudgetIsShared() {
        var geometry=BridgePlanner.attach(request(true,62,BridgeSettings.defaults()),parallelRoads(),EMPTY).roadGraph().bridges();
        assertEquals(2,geometry.size());
        long volume=geometry.getFirst().constructionVolume();
        var provider=new com.cybersammy.citiesarise.minecraft.placement.ProceduralBridgePlacementProvider();
        assertEquals(volume,provider.create(geometry.getFirst()).stream()
                .filter(op -> op.role()!=com.cybersammy.citiesarise.minecraft.placement.DebugPlacementRole.BRIDGE_CLEARANCE).count());
        assertEquals(1,BridgePlanner.attach(request(true,62,new BridgeSettings(48,2,1,0,false,2,volume,32)),
                parallelRoads(),EMPTY).roadGraph().bridges().size());
        assertTrue(BridgePlanner.attach(request(true,62,new BridgeSettings(48,2,1,0,false,2,volume-1,32)),
                parallelRoads(),EMPTY).roadGraph().bridges().isEmpty());
    }

    @Test void districtPhaseDoesNotSpendSharedBudgetOnOptionalShortcut() {
        assertTrue(BridgePlanner.attachWaterCrossings(request(true,62,BridgeSettings.defaults()),roads(64),EMPTY)
                .roadGraph().bridges().isEmpty());
    }

    private static SettlementPlan parallelRoads() {
        var nodes=List.of(node("a",4,8),node("b",4,12),node("c",16,8),node("d",16,12));
        return new SettlementPlan(ID,new RoadGraph(nodes,List.of(segment("left",nodes.get(0),nodes.get(1),64),
                segment("right",nodes.get(2),nodes.get(3),64))),List.of(),List.of(),Set.of(),PlanProperties.empty());
    }
    @Test void dryCrossingsRequireOptInAndClearanceAcrossEntireSpan() {
        var settings = new BridgeSettings(48,2,1,0,true,2);
        var request = request(false,62,settings);
        var result = BridgePlanner.attach(request,roads(64),EMPTY);
        assertEquals(1,result.roadGraph().bridges().size());
        assertEquals(result,BridgePlanner.attach(request,roads(64),EMPTY));
        assertTrue(BridgePlanner.attach(request(false,63,settings),roads(64),EMPTY).roadGraph().bridges().isEmpty());
        assertTrue(BridgePlanner.attach(request(false,65,settings),roads(64),EMPTY).roadGraph().bridges().isEmpty());
        var interrupted = TerrainSurvey.sample(request.survey().bounds(),p -> p.equals(new GridPoint(10,9))
                ? Optional.of(new TerrainCell(p,64,false,0,BiomeCategory.PLAINS,TerrainCategory.BUILDABLE)) : request.survey().findCell(p));
        var obstructed = new SuburbPlanningRequest(ID,interrupted,1,request.settings(),request.terrainResponsePolicy());
        assertTrue(BridgePlanner.attach(obstructed,roads(64),EMPTY).roadGraph().bridges().isEmpty());
    }

    @Test void dryPermissionDoesNotOverrideWaterAvoidance() {
        var settings = new BridgeSettings(48,2,1,0,true,2);
        for(boolean water:List.of(false,true)) {
            var original=request(water,62,settings);
            var policy=new TerrainResponsePolicy(Map.of(TerrainFeatureType.WATER,TerrainResponse.AVOID,
                    TerrainFeatureType.STEEP_SLOPE,TerrainResponse.BUILD_AROUND,TerrainFeatureType.BLOCKED_TERRAIN,TerrainResponse.AVOID),
                    Set.of(InfrastructureCapability.BRIDGE),TerrainAdaptationSettings.disabled(),settings);
            var req=new SuburbPlanningRequest(ID,original.survey(),1,original.settings(),policy);
            assertEquals(water?0:1,BridgePlanner.attach(req,roads(64),EMPTY).roadGraph().bridges().size());
        }
        assertThrows(IllegalArgumentException.class,()->new BridgeSettings(48,2,1,0,true,0));
    }
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
        var withoutUnsafeShortcuts=SuburbPlanner.defaults().plan(
                new SuburbPlanningRequest(ID,survey,100,SuburbPlanningSettings.defaults(),policy),
                (r,candidate) -> candidate.plan().orElseThrow().roadGraph().bridges().isEmpty()?candidate
                        : SuburbPlanningResult.rejected(SuburbPlanningFailureReason.NOT_ENOUGH_PARCEL_SPACE));
        assertTrue(withoutUnsafeShortcuts.successful(),withoutUnsafeShortcuts.toString());
        assertTrue(withoutUnsafeShortcuts.plan().orElseThrow().roadGraph().bridges().isEmpty());
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
